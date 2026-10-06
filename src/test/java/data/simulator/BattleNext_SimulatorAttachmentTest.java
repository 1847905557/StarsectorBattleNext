package data.simulator;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.BattleNext_MissionRestrictedUnlock;
import data.missions.BattleNext_lab.MissionDefinition;
import data.scripts.BattleNextModPlugin;
import data.scripts.campaign.BattleNext_LunaFleetSaveRefitButton;
import data.scripts.campaign.BattleNext_LunaPinnedVariantRefitButton;
import data.scripts.plugin.BattleNext_AllShipsStatusPlugin;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_FreeCameraPlugin;
import data.scripts.plugin.BattleNext_RttPlugin;
import data.test.fixture.BattleNext_TestGameFixture;
import lunalib.lunaRefit.LunaRefitManager;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class BattleNext_SimulatorAttachmentTest extends BattleNext_TestGameFixture {

	@Test
	void monitorTypesAndJsonRegistrationMatchTheirRespectiveHosts() throws Exception {
		JSONObject plugins = new JSONObject(Files.readString(Path.of("data/config/settings.json"), StandardCharsets.UTF_8)).getJSONObject("plugins");
		Class<?> registered = Class.forName(plugins.getString("BattleNextSimulatorMonitor"));
		assertEquals(BattleNext_SimulatorMonitor.class, registered);
		assertTrue(EveryFrameCombatPlugin.class.isAssignableFrom(registered));
		assertFalse(EveryFrameScript.class.isAssignableFrom(registered));
		assertTrue(EveryFrameScript.class.isAssignableFrom(BattleNext_SimulatorCampaignMonitor.class));
		assertFalse(EveryFrameCombatPlugin.class.isAssignableFrom(BattleNext_SimulatorCampaignMonitor.class));
	}

	@Test
	void campaignLoadsReplaceTheirOwnMonitorAndPreserveOtherTransientScripts() {

		var registry = LunaRefitManager.INSTANCE.getButtons();
		var previous = List.copyOf(registry);
		registry.removeIf(button -> button instanceof BattleNext_LunaPinnedVariantRefitButton || button instanceof BattleNext_LunaFleetSaveRefitButton);
		List<EveryFrameScript> scripts = new ArrayList<>();
		EveryFrameScript other = proxy(EveryFrameScript.class, (method, args) -> null);
		scripts.add(other);
		Map<String, Object> persistentData = new HashMap<>();
		List<String> lifecycle = new ArrayList<>();
		Global.setSector(proxy(SectorAPI.class, (method, args) -> {
			switch (method) {
				case "getPersistentData" -> {
					return persistentData;
				}
				case "getMemoryWithoutUpdate" -> {
					return proxy(MemoryAPI.class, (name, values) -> null);
				}
				case "removeTransientScriptsOfClass" -> {
					assertEquals(BattleNext_SimulatorCampaignMonitor.class, args[0]);
					scripts.removeIf(script -> ((Class<?>) args[0]).isInstance(script));
					lifecycle.add("remove");
				}
				case "removeTransientScript" -> scripts.remove(args[0]);
				case "addTransientScript" -> {
					scripts.add((EveryFrameScript) args[0]);
					if (args[0] instanceof BattleNext_SimulatorCampaignMonitor) {
						lifecycle.add("add");
					}
				}
			}
			return null;
		}));
		BattleNextModPlugin plugin = new BattleNextModPlugin() {
			@Override
			public void onDevModeF8Reload() {

			}
		};
		try {
			BattleNext_LunaPinnedVariantRefitButton.register();
			BattleNext_LunaFleetSaveRefitButton.register();
			EveryFrameScript previousMonitor = null;
			for (boolean newGame : List.of(true, false, true)) {
				lifecycle.clear();
				plugin.onGameLoad(newGame);
				var monitors = scripts.stream().filter(script -> script instanceof BattleNext_SimulatorCampaignMonitor).toList();
				assertEquals(1, monitors.size());
				assertNotSame(previousMonitor, monitors.get(0));
				assertTrue(scripts.contains(other));
				assertEquals(List.of("remove", "add"), lifecycle);
				previousMonitor = monitors.get(0);
			}
		} finally {
			registry.clear();
			registry.addAll(previous);
		}
	}

	@Test
	void labRegistersItsVisibleFleetsMapAndCombatPlugins() throws Exception {

		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (Set.of("getAllWeaponSpecs", "getAllHullModSpecs", "getAllFighterWingSpecs").contains(method.getName())) return List.of();
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		List<String> calls = new ArrayList<>();
		List<EveryFrameCombatPlugin> plugins = new ArrayList<>();
		MissionDefinitionAPI api = proxy(MissionDefinitionAPI.class, (method, args) -> {
			switch (method) {
				case "initFleet" -> calls.add("fleet:" + args[0] + ":" + args[3]);
				case "addBriefingItem" -> calls.add("briefing:" + args[0]);
				case "setFleetTagline" -> calls.add("tagline:" + args[0] + ":" + args[1]);
				case "addToFleet" -> {
					assertEquals(FleetMemberType.SHIP, args[2]);
					assertEquals(false, args[3]);
					calls.add("ship:" + args[0] + ":" + args[1]);
				}
				case "initMap" -> {
					assertArrayEquals(new Object[]{-6000f, 6000f, -6000f, 6000f}, args);
					calls.add("map");
				}
				case "addPlugin" -> plugins.add((EveryFrameCombatPlugin) args[0]);
			}
			return null;
		});
		try {
			new MissionDefinition().defineMission(api);
		} finally {
			BattleNext_MissionRestrictedUnlock.getInstance().forget();
		}
		List<String> expected = new ArrayList<>(List.of("fleet:PLAYER:true", "fleet:ENEMY:false", "briefing:TEST", "tagline:PLAYER:1", "tagline:ENEMY:2"));
		for (FleetSide side : FleetSide.values()) {
			for (int index = 0; index < 4; index++) {
				expected.add("ship:" + side + ":wolf_CS");
			}
		}
		expected.add("map");
		assertEquals(expected, calls);
		assertEquals(List.of(BattleNext_DeploymentPlugin.class, BattleNext_FreeCameraPlugin.class, BattleNext_AllShipsStatusPlugin.class,
				BattleNext_RttPlugin.class), plugins.stream().map(Object::getClass).toList());
	}

}
