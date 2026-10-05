package data.order;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.BattleNext_pvp.MissionDefinition;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.order.BattleNext_FleetOrderBehavior;
import data.test.fixture.BattleNext_TestOrderFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_OrderMissionIntegrationTest extends BattleNext_TestOrderFixture {

	@Test
	void memberJsonAssemblesRegistersInMissionAndExecutesWithoutActivationTags() throws Exception {

		ShipState player = world.ship(0, 0f);
		ShipState enemy = world.ship(1, 100f);
		List<ShipState> deployed = List.of(player, enemy);
		AtomicInteger next = new AtomicInteger();
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			return switch (method.getName()) {
				case "doesVariantExist" -> true;
				case "getVariant" -> proxy(ShipVariantAPI.class, (name, values) -> name.equals("getHullSpec")
						? proxy(ShipHullSpecAPI.class, (hullMethod, hullArguments) -> hullMethod.equals("getHullName") ? "Variant hull" : null) : null);
				case "createPerson" -> proxy(PersonAPI.class, (name, values) -> null);
				case "createFleetMember" -> {
					ShipState ship = deployed.get(next.getAndIncrement());
					RepairTrackerAPI repair = proxy(RepairTrackerAPI.class, (name, values) -> name.equals("getMaxCR") ? 1f : null);
					CrewCompositionAPI crew = proxy(CrewCompositionAPI.class, (name, values) -> null);
					FleetMemberAPI member = proxy(FleetMemberAPI.class, (name, values) -> switch (name) {
						case "getVariant" -> args[1];
						case "getRepairTracker" -> repair;
						case "getCrewComposition" -> crew;
						default -> null;
					});
					ship.setMember(member);
					yield member;
				}
				default -> {
					try {
						yield method.invoke(settings, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}
			};
		}));
		var playerDefinition = definition("player", "engage.nearest");
		var enemyDefinition = definition("enemy", "harass.nearest");
		var builder = new BattleNext_FleetBuilder();
		var playerFleet = builder.build(playerDefinition);
		var enemyFleet = builder.build(enemyDefinition);
		assertTrue(playerFleet.isComplete(), playerFleet.getMemberErrors().toString());
		assertTrue(enemyFleet.isComplete(), enemyFleet.getMemberErrors().toString());
		assertTrue(playerDefinition.getTags().isEmpty());
		List<FleetMemberAPI> added = new ArrayList<>();
		List<Boolean> defaultAi = new ArrayList<>();
		MissionDefinitionAPI api = proxy(MissionDefinitionAPI.class, (method, args) -> switch (method) {
			case "addFleetMember" -> { added.add((FleetMemberAPI) args[1]); yield null; }
			case "initFleet" -> { defaultAi.add((Boolean) args[3]); yield null; }
			default -> null;
		});
		MissionDefinition mission = new MissionDefinition();
		var configure = MissionDefinition.class.getDeclaredMethod("configureFleet", MissionDefinitionAPI.class,
				FleetSide.class, BattleNext_AssembledFleet.class);
		configure.setAccessible(true);
		configure.invoke(mission, api, FleetSide.PLAYER, playerFleet);
		configure.invoke(mission, api, FleetSide.ENEMY, enemyFleet);
		assertEquals(List.of(player.getMember(), enemy.getMember()), added);
		assertEquals(List.of(false, false), defaultAi);
		var plugin = new BattleNext_FleetOrderBehavior(BattleNext_FleetOrderBehavior.fromTags(playerDefinition.getTags()),
				BattleNext_FleetOrderBehavior.fromTags(enemyDefinition.getTags()), playerFleet, enemyFleet);
		plugin.init(world.getEngine());
		plugin.advance(0.16f, List.of());
		assertEquals(CombatAssignmentType.ENGAGE, world.side(0, false).current(player).getType());
		assertEquals(CombatAssignmentType.HARASS, world.side(1, false).current(enemy).getType());
		plugin.cleanup();
		assertNull(world.side(0, false).current(player));
		assertNull(world.side(1, false).current(enemy));
	}

	private BattleNext_FleetModel definition(String id, String orders) throws Exception {
		JSONObject member = new JSONObject().put("variant_id", "variant").put("orders", orders);
		JSONObject json = new JSONObject().put("id", id).put("name", id).put("members", new JSONArray().put(member));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.fromJson(json);
		return fleet;
	}
}
