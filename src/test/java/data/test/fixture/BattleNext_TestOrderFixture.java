package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.model.fleet.BattleNext_FleetModel;
import data.order.BattleNext_OrderRegistry;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.order.BattleNext_OrderField;
import data.scripts.tools.BattleNext_CsvLine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BattleNext_TestOrderFixture extends BattleNext_TestGameFixture {

	private static List<List<String>> sourceRows;
	protected final World world = new World();
	private CombatEngineAPI previousEngine;
	private Object previousEntries;
	private Object previousFactory;
	private Object previousLoadErrors;

	@BeforeEach
	void installOrders() throws Exception {
		previousEngine = Global.getCombatEngine();
		Global.setCombatEngine(world.getEngine());
		var registry = BattleNext_OrderRegistry.getInstance();
		previousEntries = state(registry, "entries");
		previousFactory = state(registry, "scriptFactory");
		previousLoadErrors = state(registry, "loadErrors");
		registry.load(csvRows(), BattleNext_TestOrderFixture::instantiate);
	}

	@AfterEach
	void restoreOrders() throws Exception {
		Global.setCombatEngine(previousEngine);
		var registry = BattleNext_OrderRegistry.getInstance();
		setState(registry, "entries", previousEntries);
		setState(registry, "scriptFactory", previousFactory);
		setState(registry, "loadErrors", previousLoadErrors);
	}

	protected static Object instantiate(String name) {
		try {
			return Class.forName(name).getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalArgumentException(name, exception);
		}
	}

	protected static synchronized JSONArray csvRows() throws Exception {
		if (sourceRows == null) {
			List<List<String>> parsed = new ArrayList<>();
			List<String> lines = Files.readAllLines(Path.of(BattleNext_OrderRegistry.PATH), StandardCharsets.UTF_8);
			for (String line : lines.subList(1, lines.size())) {
				if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
				parsed.add(List.copyOf(BattleNext_CsvLine.parse(line)));
			}
			sourceRows = List.copyOf(parsed);
		}
		JSONArray rows = new JSONArray();
		for (List<String> fields : sourceRows) {
			rows.put(new JSONObject().put("id", fields.get(0)).put("type", fields.get(1))
					.put("script", fields.get(2)).put("desc", fields.get(3)));
		}
		return rows;
	}

	protected static Object state(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	protected static void setState(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	protected BattleNext_AssembledFleet assembled(int owner, List<ShipState> ships, List<String> scripts) {
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (int index = 0; index < ships.size(); index++) {
			members.add(new BattleNext_MemberModel("variant_" + index, "Ship " + index, "", List.of(), "",
					scripts.get(index), List.of()));
		}
		var definition = new BattleNext_FleetModel("fleet_" + owner, "Fleet " + owner,
				List.of(), List.of(), members);
		return new BattleNext_AssembledFleet(definition, null, ships.stream().map(ShipState::getMember).toList(), List.of());
	}

	protected class World {
		private final List<ShipAPI> ships = new ArrayList<>();
		private final List<BattleObjectiveAPI> objectives = new ArrayList<>();
		private final Map<String, Object> custom = new HashMap<>();
		private final Map<String, Side> sides = new LinkedHashMap<>();
		private final Map<ShipAPI, DeployedFleetMemberAPI> deployed = new IdentityHashMap<>();
		private final List<Object> removedPlugins = new ArrayList<>();
		private boolean paused;
		private boolean combatOver;
		private final CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getShips" -> ships;
			case "getObjectives" -> objectives;
			case "getCustomData" -> custom;
			case "isEntityInPlay" -> ships.contains(args[0]) && !((ShipAPI) args[0]).isExpired() || objectives.contains(args[0]);
			case "isPaused" -> paused;
			case "isCombatOver" -> combatOver;
			case "getFleetManager" -> manager(args[0] instanceof FleetSide side ? side.ordinal() : (Integer) args[0]);
			case "removePlugin" -> removedPlugins.add(args[0]);
			default -> null;
		});

		public CombatEngineAPI getEngine() {
			return engine;
		}

		public Map<String, Object> getCustom() {
			return custom;
		}

		public List<Object> getRemovedPlugins() {
			return removedPlugins;
		}

		public void setPaused(boolean value) {
			paused = value;
		}

		public void setCombatOver(boolean value) {
			combatOver = value;
		}

		public void leave(ShipState ship) {
			ships.remove(ship.getApi());
		}

		public ShipState ship(int owner, float x) {
			ShipState state = new ShipState(owner, x);
			ships.add(state.getApi());
			deployed.put(state.getApi(), proxy(DeployedFleetMemberAPI.class, (method, args) -> switch (method) {
				case "getShip" -> state.getApi();
				case "getMember" -> state.getMember();
				case "getLocation" -> state.getApi().getLocation();
				case "getOwner" -> state.getApi().getOwner();
				case "isAlly" -> state.getApi().isAlly();
				case "canBeGivenOrders", "canBeGivenRetreatOrders" -> true;
				default -> null;
			}));
			return state;
		}

		public BattleObjectiveAPI objective(int owner, float x) {
			BattleObjectiveAPI objective = proxy(BattleObjectiveAPI.class, (method, args) -> switch (method) {
				case "getOwner" -> owner;
				case "getLocation" -> new Vector2f(x, 0f);
				case "getCollisionRadius" -> 0f;
				default -> null;
			});
			objectives.add(objective);
			return objective;
		}

		public void removeObjective(BattleObjectiveAPI objective) {
			objectives.remove(objective);
		}

		public Side side(int owner, boolean ally) {
			return sides.computeIfAbsent(owner + ":" + ally, ignored -> new Side());
		}

		public BattleNext_OrderField field(ShipState ship) {
			return BattleNext_OrderField.getInstance(engine, ship.getApi().getOwner(), ship.getApi().isAlly());
		}

		private CombatFleetManagerAPI manager(int owner) {
			return proxy(CombatFleetManagerAPI.class, (method, args) -> switch (method) {
				case "getTaskManager" -> side(owner, (Boolean) args[0]).getTasks();
				case "getDeployedFleetMember" -> {
					ShipAPI ship = (ShipAPI) args[0];
					yield ships.contains(ship) && ship.isAlive() && ship.getOwner() == owner ? deployed.get(ship) : null;
				}
				case "getDeployedFleetMemberEvenIfDisabled" -> deployed.get(args[0]);
				case "getShipFor" -> ships.stream().filter(ship -> ship.getFleetMember() == args[0]).findFirst().orElse(null);
				default -> null;
			});
		}
	}

	protected class ShipState {
		private int owner;
		private final int originalOwner;
		private boolean ally;
		private boolean alive = true;
		private boolean expired;
		private boolean retreating;
		private boolean overloaded;
		private float hp = 500f;
		private float hullLevel = 0.5f;
		private float flux = 1000f;
		private float fluxLevel = 0.2f;
		private float speed = 100f;
		private ShipAPI.HullSize hullSize = ShipAPI.HullSize.FRIGATE;
		private final Set<ShipHullSpecAPI.ShipTypeHints> hints = EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
		private final Map<String, Object> custom = new HashMap<>();
		private final Vector2f location;
		private FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, args) -> null);
		private final ShipAPI api;

		private ShipState(int owner, float x) {

			this.owner = owner;
			originalOwner = owner;
			location = new Vector2f(x, 0f);
			FluxTrackerAPI tracker = proxy(FluxTrackerAPI.class, (method, args) -> method.equals("isOverloaded") ? overloaded : null);
			ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> method.equals("getHints") ? hints : null);
			api = proxy(ShipAPI.class, (method, args) -> switch (method) {
				case "getOwner" -> this.owner;
				case "getOriginalOwner" -> originalOwner;
				case "isAlly" -> ally;
				case "isAlive" -> alive;
				case "isExpired" -> expired;
				case "isRetreating" -> retreating;
				case "setRetreating" -> { retreating = (Boolean) args[0]; yield null; }
				case "getFleetMember" -> member;
				case "getLocation" -> location;
				case "getHullSize" -> hullSize;
				case "getHullSpec" -> hull;
				case "getHitpoints" -> hp;
				case "getHullLevel" -> hullLevel;
				case "getCurrFlux" -> flux;
				case "getFluxLevel" -> fluxLevel;
				case "getMaxSpeed" -> speed;
				case "getFluxTracker" -> tracker;
				case "getCustomData" -> custom;
				default -> null;
			});
		}

		public ShipAPI getApi() {
			return api;
		}
		public FleetMemberAPI getMember() {
			return member;
		}
		public void setMember(FleetMemberAPI value) {
			member = value;
		}
		public void setOwner(int value) {
			owner = value;
		}
		public void setAlly(boolean value) {
			ally = value;
		}
		public void setAlive(boolean value) {
			alive = value;
		}
		public void setExpired(boolean value) {
			expired = value;
		}
		public void setOverloaded(boolean value) {
			overloaded = value;
		}
		public void setHp(float value, float level) {
			hp = value;
			hullLevel = level;
		}
		public void setFlux(float value, float level) {
			flux = value;
			fluxLevel = level;
		}
		public void setSpeed(float value) {
			speed = value;
		}
		public void setHullSize(ShipAPI.HullSize value) {
			hullSize = value;
		}
		public Set<ShipHullSpecAPI.ShipTypeHints> getHints() {
			return hints;
		}
	}

	protected class Side {
		private final Map<ShipAPI, AssignmentInfo> assigned = new IdentityHashMap<>();
		private final List<AssignmentInfo> assignments = new ArrayList<>();
		private int retreats;
		private boolean fullAssault;
		private boolean fullRetreat;
		private boolean refuse;
		private final CombatTaskManagerAPI tasks = proxy(CombatTaskManagerAPI.class, (method, args) -> switch (method) {
			case "getAssignmentFor" -> assigned.get(args[0]);
			case "getAllAssignments" -> List.copyOf(assignments);
			case "createAssignment" -> assignment((CombatAssignmentType) args[0], (AssignmentTargetAPI) args[1]);
			case "giveAssignment" -> { if (!refuse) assigned.put(((DeployedFleetMemberAPI) args[0]).getShip(), (AssignmentInfo) args[1]); yield null; }
			case "orderSearchAndDestroy" -> { assigned.remove(((DeployedFleetMemberAPI) args[0]).getShip()); yield null; }
			case "orderRetreat" -> {
				ShipAPI ship = ((DeployedFleetMemberAPI) args[0]).getShip();
				ship.setRetreating(true, false);
				assigned.put(ship, assignment(CombatAssignmentType.RETREAT, (AssignmentTargetAPI) args[0]));
				retreats++;
				yield null;
			}
			case "removeAssignment" -> { assignments.remove(args[0]); assigned.values().removeIf(value -> value == args[0]); yield null; }
			case "isFullAssault" -> fullAssault;
			case "setFullAssault" -> { fullAssault = (Boolean) args[0]; yield null; }
			case "isInFullRetreat" -> fullRetreat;
			default -> null;
		});

		public CombatTaskManagerAPI getTasks() {
			return tasks;
		}
		public List<AssignmentInfo> getAssignments() {
			return assignments;
		}
		public int getRetreats() {
			return retreats;
		}
		public void setRefuse(boolean value) {
			refuse = value;
		}
		public void setFullRetreat(boolean value) {
			fullRetreat = value;
		}
		public void setFullAssault(boolean value) {
			fullAssault = value;
		}
		public AssignmentInfo current(ShipState ship) {
			return assigned.get(ship.getApi());
		}
		public void give(ShipState ship, AssignmentInfo assignment) {
			assigned.put(ship.getApi(), assignment);
		}

		public AssignmentInfo assignment(CombatAssignmentType type, AssignmentTargetAPI target) {
			AssignmentInfo info = new AssignmentInfo() {
				@Override
				public CombatAssignmentType getType() {
					return type;
				}
				@Override
				public AssignmentTargetAPI getTarget() {
					return target;
				}
				@Override
				public List<DeployedFleetMemberAPI> getAssignedMembers() {
					return assigned.entrySet().stream().filter(entry -> entry.getValue() == this)
							.map(entry -> world.deployed.get(entry.getKey())).toList();
				}
			};
			assignments.add(info);
			return info;
		}
	}
}
