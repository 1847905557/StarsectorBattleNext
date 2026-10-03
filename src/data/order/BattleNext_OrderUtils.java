package data.order;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BattleNext_OrderUtils {

	public static final String SELF = "self";
	public static final String HULL_SIZE_LARGEST = "largest";
	public static final String HULL_SIZE_SMALLEST = "smallest";
	public static final String HULL_TYPE_ANY = "any";
	public static final String HULL_TYPE_PHASE = "phase";
	public static final String HULL_TYPE_CARRIER = "carrier";
	private static final Map<String, HullSize> HULL_SIZES = Map.of(
			"frigate", HullSize.FRIGATE, "destroyer", HullSize.DESTROYER,
			"cruiser", HullSize.CRUISER, "capital", HullSize.CAPITAL_SHIP);

	private BattleNext_OrderUtils() {

	}

	public static HullSize hullSize(String value) {
		return HULL_SIZES.get(value);
	}

	public static List<ShipAPI> enemies(ShipAPI source, CombatEngineAPI engine) {
		return ships(source, engine, false);
	}

	public static List<ShipAPI> allies(ShipAPI source, CombatEngineAPI engine) {
		return ships(source, engine, true);
	}

	private static List<ShipAPI> ships(ShipAPI source, CombatEngineAPI engine, boolean allied) {
		List<ShipAPI> result = new ArrayList<>();
		for (ShipAPI ship : engine.getShips()) {
			if (ship == source) continue;
			if (!isValid(ship, engine)) continue;
			if (ship.getOwner() == 100) continue;
			if ((ship.getOwner() == source.getOwner()) != allied) continue;
			if (toTarget(ship, engine) == null) continue;
			result.add(ship);
		}
		return result;
	}

	public static boolean isValid(ShipAPI ship, CombatEngineAPI engine) {
		return ship.isAlive() && !ship.isExpired() && !ship.isFighter() && !ship.isDrone()
				&& !ship.isStationModule() && engine.isEntityInPlay(ship);
	}

	public static List<BattleObjectiveAPI> enemyObjectives(ShipAPI source, CombatEngineAPI engine) {
		List<BattleObjectiveAPI> result = new ArrayList<>();
		for (BattleObjectiveAPI objective : engine.getObjectives()) {
			if (objective.getOwner() != source.getOwner()) result.add(objective);
		}
		return result;
	}

	public static List<BattleObjectiveAPI> allyObjectives(ShipAPI source, CombatEngineAPI engine) {
		List<BattleObjectiveAPI> result = new ArrayList<>();
		for (BattleObjectiveAPI objective : engine.getObjectives()) {
			if (objective.getOwner() == source.getOwner()) result.add(objective);
		}
		return result;
	}

	public static void requireCount(List<String> params, int min, int max) {
		if (params.size() < min || params.size() > max) {
			throw new IllegalArgumentException("Expected " + min + ".." + max + " parameters, received " + params.size());
		}
	}

	public static float number(String text) {
		float value = Float.parseFloat(text);
		if (!Float.isFinite(value)) throw new IllegalArgumentException("Expected a finite number: " + text);
		return value;
	}

	public static AssignmentTargetAPI toTarget(CombatEntityAPI entity, CombatEngineAPI engine) {
		if (entity instanceof ShipAPI ship) {
			return engine.getFleetManager(ship.getOwner()).getDeployedFleetMember(ship);
		}
		return (AssignmentTargetAPI) entity;
	}

	public static CombatEntityAPI toEntity(AssignmentTargetAPI target) {
		return target instanceof DeployedFleetMemberAPI member ? member.getShip() : (CombatEntityAPI) target;
	}

	public static boolean isValidTarget(AssignmentTargetAPI target, CombatEngineAPI engine) {
		if (target instanceof DeployedFleetMemberAPI member) {
			ShipAPI ship = member.getShip();
			return ship != null && isValid(ship, engine) && toTarget(ship, engine) == member;
		}
		return engine.getObjectives().contains(target);
	}

	public static int assignmentCount(AssignmentTargetAPI target, CombatAssignmentType type,
			CombatTaskManagerAPI tasks, CombatEngineAPI engine) {

		int count = 0;
		for (AssignmentInfo assignment : tasks.getAllAssignments()) {
			if (assignment.getTarget() != target) continue;
			if (type != null && assignment.getType() != type) continue;
			for (DeployedFleetMemberAPI member : assignment.getAssignedMembers()) {
				ShipAPI ship = member.getShip();
				if (ship == null || !ship.isAlive() || !engine.isEntityInPlay(ship)) continue;
				if (tasks.getAssignmentFor(ship) == assignment) count++;
			}
		}
		return count;
	}
}
