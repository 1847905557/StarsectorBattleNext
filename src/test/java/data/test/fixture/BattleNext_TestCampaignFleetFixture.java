package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.PersonalityAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.io.BattleNext_FleetSaver;
import data.scripts.ui.BattleNext_UiInputRouter;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_TestCampaignFleetFixture extends BattleNext_TestVariantFixture {

	protected Variant selectedVariant;
	protected Variant activeVariant;
	protected Variant otherVariant;
	protected FleetMemberAPI selected;
	protected CampaignFleetAPI fleet;
	protected PersonAPI player;
	protected PersonAPI officer;
	protected PersonAPI core;
	protected CoreUITabId tab = CoreUITabId.REFIT;
	protected final List<FleetMemberAPI> fleetMembers = new ArrayList<>();
	protected final List<Object> scripts = new ArrayList<>();
	protected final List<PersonAPI> createdPeople = new ArrayList<>();

	@BeforeEach
	protected void configurePlayerFleet() throws Exception {

		selectedVariant = source();
		activeVariant = new Variant("parent_variant", hulls.get("parent"));
		activeVariant.getJson().put("displayName", "Active draft");
		activeVariant.getJson().put("wings", selectedVariant.getJson().getJSONArray("wings"));
		activeVariant.getJson().put("weaponGroups", selectedVariant.getJson().getJSONArray("weaponGroups"));
		activeVariant.getApi().setNumFluxVents(42);
		activeVariant.getApi().addMod("ordinary");
		activeVariant.getApi().addPermaMod("sm", true);
		activeVariant.getApi().addPermaMod("active_dmod", false);
		activeVariant.getApi().getSModdedBuiltIns().add("built");
		activeVariant.getApi().getSuppressedMods().add("d");
		activeVariant.getApi().setModuleVariant("module", selectedVariant.getModules().get("module"));
		otherVariant = new Variant("parent_variant", hulls.get("parent"));
		otherVariant.getApi().setNumFluxVents(13);
		player = person("Player", "player.png", "aggressive", false, true, false,
				Map.of("combat", 2f, "fleet", 1f, "industry", 1f, "unlearned", 0f));
		officer = person("Officer", "officer.png", "reckless", false, false, false,
				Map.of("gunnery", 2f, "helmsmanship", 1f));
		core = person("Core", "core.png", "reckless", false, false, true, Map.of("core_skill", 2f));
		selected = member("Officer ship", selectedVariant.getApi(), officer, false);
		fleetMembers.add(selected);
		fleetMembers.add(member("Player ship", otherVariant.getApi(), player, false));
		fleetMembers.add(member("Core ship", otherVariant.getApi(), core, false));
		fleetMembers.add(member("Civilian mothballed", otherVariant.getApi(),
				person("Default", "default.png", "steady", true, false, false, Map.of()), true));
		fleetMembers.add(member("Unassigned", otherVariant.getApi(), null, false));
		FleetDataAPI data = proxy(FleetDataAPI.class, (method, args) -> switch (method) {
			case "getMembersListCopy" -> new ArrayList<>(fleetMembers);
			case "getCombatReadyMembersListCopy" -> List.of(selected);
			default -> null;
		});
		fleet = proxy(CampaignFleetAPI.class, (method, args) -> switch (method) {
			case "getName" -> "Player fleet";
			case "getFleetData" -> data;
			default -> null;
		});
		Global.setSector(proxy(SectorAPI.class, (method, args) -> switch (method) {
			case "getPlayerFleet" -> fleet;
			case "getPlayerPerson" -> player;
			case "getCampaignUI" -> proxy(CampaignUIAPI.class, (name, values) ->
					"getCurrentCoreTab".equals(name) ? tab : null);
			case "addTransientScript" -> scripts.add(args[0]);
			case "removeTransientScript" -> scripts.remove(args[0]);
			default -> null;
		}));
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if ("loadText".equals(method.getName()) && ((String) args[0]).startsWith("tournament/")) {
				return Files.readString(directory.resolve((String) args[0]), StandardCharsets.UTF_8);
			}
			if ("getString".equals(method.getName())) {
				return BattleNext_TestTextCatalog.text(args[0], args[1]);
			}
			if ("createPerson".equals(method.getName())) {
				PersonAPI person = person("Generated", "", "steady", false, false, false, Map.of());
				createdPeople.add(person);
				return person;
			}
			if ("createFleetMember".equals(method.getName())) return member("", (ShipVariantAPI) args[1], null, false);
			try {
				return method.invoke(previous, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	@AfterEach
	protected void releaseCampaignRouter() {
		BattleNext_UiInputRouter.activate(null);
	}

	protected FleetMemberAPI member(String name, ShipVariantAPI variant, PersonAPI officer, boolean mothballed) {
		String[] shipName = {name};
		PersonAPI[] person = {officer};
		RepairTrackerAPI repair = proxy(RepairTrackerAPI.class, (method, args) ->
				"getMaxCR".equals(method) ? 0.7f : null);
		return proxy(FleetMemberAPI.class, (method, args) -> switch (method) {
			case "getVariant" -> variant;
			case "getShipName" -> shipName[0];
			case "setShipName" -> { shipName[0] = (String) args[0]; yield null; }
			case "getCaptain" -> person[0];
			case "setCaptain" -> { person[0] = (PersonAPI) args[0]; yield null; }
			case "isMothballed", "isCivilian" -> mothballed;
			case "getRepairTracker" -> repair;
			case "getCrewComposition" -> proxy(CrewCompositionAPI.class, (key, values) -> null);
			default -> null;
		});
	}

	protected PersonAPI person(String name, String portrait, String personality, boolean defaultPerson,
			boolean playerPerson, boolean aiCore, Map<String, Float> initialSkills) {

		Map<String, Float> skills = new LinkedHashMap<>(initialSkills);
		String[] sprite = {portrait};
		String[] behavior = {personality};
		MutableCharacterStatsAPI stats = proxy(MutableCharacterStatsAPI.class, (method, args) -> switch (method) {
			case "getSkillsCopy" -> skills.entrySet().stream().map(entry ->
					proxy(MutableCharacterStatsAPI.SkillLevelAPI.class, (key, values) -> switch (key) {
						case "getLevel" -> entry.getValue();
						case "getSkill" -> proxy(SkillSpecAPI.class, (spec, params) ->
								"getId".equals(spec) ? entry.getKey() : null);
						default -> null;
					})).toList();
			case "getSkillLevel" -> skills.getOrDefault(args[0], 0f);
			case "setSkillLevel" -> skills.put((String) args[0], (Float) args[1]);
			default -> null;
		});
		return proxy(PersonAPI.class, (method, args) -> switch (method) {
			case "getNameString" -> name;
			case "getPortraitSprite" -> sprite[0];
			case "setPortraitSprite" -> { sprite[0] = (String) args[0]; yield null; }
			case "getStats" -> stats;
			case "isDefault" -> defaultPerson;
			case "isPlayer" -> playerPerson;
			case "isAICore" -> aiCore;
			case "getPersonalityAPI" -> proxy(PersonalityAPI.class, (key, values) -> "getId".equals(key) ? behavior[0] : null);
			case "setPersonality" -> { behavior[0] = (String) args[0]; yield null; }
			default -> null;
		});
	}

	public static class FailingFleetStore extends BattleNext_FleetStore {
		private boolean failLoad;
		private boolean failWrite = true;
		private final BattleNext_FleetSaver writer = new BattleNext_FleetSaver() {
			@Override
			public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {
				if (failWrite) throw new IOException("Injected fleet write failure");
				return super.save(fleet);
			}
		};

		public void setFailLoad(boolean value) {
			failLoad = value;
		}

		public void setFailWrite(boolean value) {
			failWrite = value;
		}

		@Override
		public void reload() throws IOException, JSONException {
			if (failLoad) throw new IOException("Injected fleet load failure");
			super.reload();
		}

		@Override
		protected BattleNext_FleetSaver saver() {
			return writer;
		}
	}
}
