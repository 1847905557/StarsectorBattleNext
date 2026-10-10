package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.plugins.CreateFleetPlugin;
import data.missions.BattleNext_MissionRestrictedUnlock;
import data.missions.BattleNext_pve.MissionDefinition;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_PveFleetGenerator;
import data.scripts.tools.BattleNext_KeyTuning;
import data.scripts.tools.BattleNext_Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntPredicate;

public class BattleNext_TestMissionFixture extends BattleNext_TestFleetIoFixture {

	protected Variant activeVariant;
	protected Variant otherVariant;
	protected PersonAPI player;
	protected PersonAPI officer;
	protected final Set<Integer> keys = new HashSet<>();
	protected final List<FactionSpecAPI> factionSpecs = new ArrayList<>();
	protected final JSONArray pveFactionRows = new JSONArray();
	protected Exception pveFactionReadFailure;
	protected final List<FleetParamsV3> generatedParameters = new ArrayList<>();
	protected final List<GeneratedFleet> generatedFleets = new ArrayList<>();
	protected Function<FleetParamsV3, CampaignFleetAPI> generator;
	private final Map<Field, Object> previousSelections = new LinkedHashMap<>();
	private Field settingsValues;
	private Object previousValues;
	private Field keyboardInput;
	private Object previousKeyboardInput;

	@BeforeEach
	protected void configureMission() throws Exception {

		activeVariant = new Variant("parent_variant", hulls.get("parent"));
		activeVariant.getJson().put("displayName", "Active draft");
		activeVariant.getApi().setNumFluxVents(42);
		activeVariant.getJson().put("wings", selectedVariant.getJson().getJSONArray("wings"));
		activeVariant.getJson().put("weaponGroups", selectedVariant.getJson().getJSONArray("weaponGroups"));
		activeVariant.getApi().setModuleVariant("module", selectedVariant.getModules().get("module"));
		activeVariant.getApi().addMod("ordinary");
		activeVariant.getApi().addPermaMod("sm", true);
		activeVariant.getApi().addPermaMod("active_dmod", false);
		activeVariant.getApi().getSModdedBuiltIns().add("built");
		activeVariant.getApi().getSuppressedMods().add("d");
		otherVariant = new Variant("parent_variant", hulls.get("parent"));
		otherVariant.getApi().setNumFluxVents(13);
		player = person("Player", "player.png", "aggressive", false, true, false,
				Map.of("combat", 2f, "fleet", 1f, "industry", 1f, "unlearned", 0f));
		officer = person("Officer", "officer.png", "reckless", false, false, false,
				Map.of("gunnery", 2f, "helmsmanship", 1f));

		keyboardInput = BattleNext_KeyTuning.class.getDeclaredField("keyPressed");
		keyboardInput.setAccessible(true);
		previousKeyboardInput = keyboardInput.get(null);
		keyboardInput.set(null, (IntPredicate) keys::contains);
		for (String name : List.of("expectedFp", "factionIndex", "quickBattleCurrentIndex")) {
			Field field = MissionDefinition.class.getDeclaredField(name);
			field.setAccessible(true);
			previousSelections.put(field, field.get(null));
			field.set(null, name.equals("expectedFp") ? 100 : 0);
		}
		Field pvpSelection = Class.forName("data.missions.BattleNext_pvp.MissionDefinition").getDeclaredField("quickBattleCurrentIndex");
		pvpSelection.setAccessible(true);
		previousSelections.put(pvpSelection, pvpSelection.get(null));
		pvpSelection.set(null, 0);
		settingsValues = BattleNext_Settings.class.getDeclaredField("values");
		settingsValues.setAccessible(true);
		previousValues = settingsValues.get(null);
		factionSpecs.add(faction("hidden", false));
		factionSpecs.add(faction("mod_faction", true));
		factionSpecs.add(faction("hegemony", true));
		generator = params -> {
			GeneratedFleet result = new GeneratedFleet("generated_" + generatedParameters.size(), params.factionId,
					List.of(enemyMember("Officer ship", activeVariant, officer, 37),
							enemyMember("Default ship", otherVariant, person("Default", "", "steady", true, false, false, Map.of()), 8)), player);
			generatedFleets.add(result);
			return result.getApi();
		};
		CreateFleetPlugin plugin = proxy(CreateFleetPlugin.class, (method, args) -> {
			if (!method.equals("createFleet")) return null;
			FleetParamsV3 params = (FleetParamsV3) args[0];
			generatedParameters.add(params);
			return generator.apply(params);
		});
		GenericPluginManagerAPI plugins = proxy(GenericPluginManagerAPI.class, (method, args) ->
				method.equals("pickPlugin") && args[0] == CreateFleetPlugin.class ? plugin : null);
		SectorAPI sector = Global.getSector();
		Global.setSector((SectorAPI) Proxy.newProxyInstance(SectorAPI.class.getClassLoader(), new Class<?>[]{SectorAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getGenericPlugins")) return plugins;
			try {
				return method.invoke(sector, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getAllFactionSpecs")) return new ArrayList<>(factionSpecs);
			if (method.getName().equals("getMergedSpreadsheetData") && args[1].equals(BattleNext_PveFleetGenerator.FACTIONS_PATH)) {
				dataReads.add("getMergedSpreadsheetData:" + args[0] + ":" + args[1]);
				if (pveFactionReadFailure != null) throw pveFactionReadFailure;
				return new JSONArray(pveFactionRows.toString());
			}
			if (Set.of("getAllWeaponSpecs", "getAllFighterWingSpecs", "getAllHullModSpecs").contains(method.getName())) return List.of();
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Settings.load();
	}

	@AfterEach
	protected void restoreMission() throws Exception {
		BattleNext_MissionRestrictedUnlock.getInstance().forget();
		for (var entry : previousSelections.entrySet()) {
			entry.getKey().set(null, entry.getValue());
		}
		settingsValues.set(null, previousValues);
		keyboardInput.set(null, previousKeyboardInput);
	}

	protected FactionSpecAPI faction(String id, boolean visible) {
		return proxy(FactionSpecAPI.class, (method, args) -> switch (method) {
			case "getId", "getDisplayName" -> id;
			case "isShowInIntelTab" -> visible;
			default -> null;
		});
	}

	protected FleetMemberAPI enemyMember(String name, Variant variant, PersonAPI officer, int points) {
		float[] readiness = {0f};
		RepairTrackerAPI repair = proxy(RepairTrackerAPI.class, (method, args) -> switch (method) {
			case "getMaxCR" -> 0.7f;
			case "getCR" -> readiness[0];
			case "setCR" -> { readiness[0] = (Float) args[0]; yield null; }
			default -> null;
		});
		return proxy(FleetMemberAPI.class, (method, args) -> switch (method) {
			case "getId" -> name;
			case "getShipName" -> name;
			case "getVariant" -> variant.getApi();
			case "getCaptain" -> officer;
			case "getFleetPointCost" -> points;
			case "getRepairTracker" -> repair;
			case "getCrewComposition" -> proxy(CrewCompositionAPI.class, (methodName, values) -> null);
			default -> null;
		});
	}

	protected void fleetSkills(String id, List<String> skills, List<String> tags) throws Exception {
		BattleNext_FleetModel model = new BattleNext_FleetModel();
		String path = "tournament/" + id + "/fleet_data.json";
		model.fromJson(new JSONObject(Files.readString(directory.resolve(path), StandardCharsets.UTF_8)));
		model.setSkills(skills);
		model.setTags(tags);
		write(path, model.toJson().toString());
	}

	protected void select(String name, int value) throws Exception {
		Field field = MissionDefinition.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}

	public class CombatState {
		private final List<String> notices = new ArrayList<>();
		private final CombatEngineAPI api;
		private final Set<FleetSide> aliveSides = EnumSet.allOf(FleetSide.class);
		private float elapsed;

		public CombatState(MissionState mission) {
			List<ShipAPI> ships = new ArrayList<>();
			for (FleetSide side : FleetSide.values()) {
				for (FleetMemberAPI member : mission.getMembers().get(side)) {
					ships.add(proxy(ShipAPI.class, (method, args) -> switch (method) {
						case "getFleetMember" -> member;
						case "getOwner" -> side.ordinal();
						case "isAlive" -> aliveSides.contains(side);
						case "getHullLevel" -> aliveSides.contains(side) ? 0.75f : 0f;
						default -> null;
					}));
				}
			}
			Map<String, Object> custom = new LinkedHashMap<>();
			CombatUIAPI ui = proxy(CombatUIAPI.class, (method, args) -> {
				if (method.equals("addMessage")) notices.add((String) ((Object[]) args[1])[0]);
				return null;
			});
			api = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
				case "getFleetManager" -> proxy(CombatFleetManagerAPI.class, (name, values) -> switch (name) {
					case "getDeployedCopy" -> aliveSides.contains(args[0]) ? mission.getMembers().get(args[0]) : List.of();
					case "getDestroyedCopy" -> aliveSides.contains(args[0]) ? List.of() : mission.getMembers().get(args[0]);
					case "getReservesCopy", "getDisabledCopy", "getRetreatedCopy", "getAllEverDeployedCopy" -> List.of();
					default -> null;
				});
				case "getShips" -> ships;
				case "getTotalElapsedTime" -> elapsed;
				case "getCustomData" -> custom;
				case "getListenerManager" -> proxy(CombatListenerManagerAPI.class, (name, values) -> null);
				case "getCombatUI" -> ui;
				default -> null;
			});
		}

		public CombatEngineAPI getApi() {
			return api;
		}

		public List<String> getNotices() {
			return notices;
		}

		public void destroySide(FleetSide side, float elapsed) {
			aliveSides.remove(side);
			this.elapsed = elapsed;
		}
	}

	public class GeneratedFleet {
		private boolean inflated;
		private final List<FleetMemberAPI> members;
		private final CampaignFleetAPI api;

		public GeneratedFleet(String id, String faction, List<FleetMemberAPI> members, PersonAPI commander) {
			this.members = members;
			FleetDataAPI data = proxy(FleetDataAPI.class, (method, args) -> {
				if (!method.equals("getMembersListCopy")) return null;
				if (!inflated) throw new IllegalStateException("Members read before inflation");
				return new ArrayList<>(members);
			});
			api = proxy(CampaignFleetAPI.class, (method, args) -> switch (method) {
				case "getId" -> id;
				case "getNameWithFaction" -> faction + " patrol";
				case "getFleetData" -> data;
				case "getCommander" -> commander;
				case "getFleetPoints" -> members.stream().mapToInt(FleetMemberAPI::getFleetPointCost).sum();
				case "inflateIfNeeded" -> { inflated = true; yield null; }
				default -> null;
			});
		}

		public CampaignFleetAPI getApi() {
			return api;
		}

		public List<FleetMemberAPI> getMembers() {
			return members;
		}

		public boolean isInflated() {
			return inflated;
		}
	}

	public class MissionState {
		private final Map<FleetSide, List<FleetMemberAPI>> members = new EnumMap<>(FleetSide.class);
		private final Map<FleetSide, PersonAPI> commanders = new EnumMap<>(FleetSide.class);
		private final Map<FleetSide, String> names = new EnumMap<>(FleetSide.class);
		private final Map<FleetSide, Boolean> defaultAi = new EnumMap<>(FleetSide.class);
		private final List<EveryFrameCombatPlugin> plugins = new ArrayList<>();
		private final List<String> briefing = new ArrayList<>();
		private final List<String> events = new ArrayList<>();
		private float[] map;
		private final MissionDefinitionAPI api;

		public MissionState() {
			for (FleetSide side : FleetSide.values()) {
				members.put(side, new ArrayList<>());
				commanders.put(side, person("Commander", "", "steady", false, false, false, Map.of()));
			}
			api = proxy(MissionDefinitionAPI.class, (method, args) -> switch (method) {
				case "initFleet" -> { defaultAi.put((FleetSide) args[0], (Boolean) args[3]); events.add("init:" + args[0]); yield null; }
				case "setFleetTagline" -> { names.put((FleetSide) args[0], (String) args[1]); events.add("tagline:" + args[0]); yield null; }
				case "addFleetMember" -> { members.get(args[0]).add((FleetMemberAPI) args[1]); events.add("member:" + args[0]); yield null; }
				case "getDefaultCommander" -> { events.add("commander:" + args[0]); yield commanders.get(args[0]); }
				case "addPlugin" -> { plugins.add((EveryFrameCombatPlugin) args[0]); yield null; }
				case "addBriefingItem" -> { briefing.add((String) args[0]); yield null; }
				case "initMap" -> { map = new float[]{(Float) args[0], (Float) args[1], (Float) args[2], (Float) args[3]}; yield null; }
				default -> null;
			});
		}

		public MissionDefinitionAPI getApi() {
			return api;
		}

		public Map<FleetSide, List<FleetMemberAPI>> getMembers() {
			return members;
		}

		public Map<FleetSide, PersonAPI> getCommanders() {
			return commanders;
		}

		public Map<FleetSide, String> getNames() {
			return names;
		}

		public Map<FleetSide, Boolean> getDefaultAi() {
			return defaultAi;
		}

		public List<EveryFrameCombatPlugin> getPlugins() {
			return plugins;
		}

		public List<String> getBriefing() {
			return briefing;
		}

		public List<String> getEvents() {
			return events;
		}

		public float[] getMap() {
			return map;
		}
	}
}
