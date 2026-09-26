package data.order;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import org.lazywizard.lazylib.combat.AIUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_OrderUtils {

	public static final String SELF = "self";
	public static final String HULL_SIZE_LARGEST = "largest";
	public static final String HULL_SIZE_SMALLEST = "smallest";
	public static final String HULL_TYPE_ANY = "any";
	public static final String HULL_TYPE_PHASE = "phase";
	public static final String HULL_TYPE_CARRIER = "carrier";
	private static final Map<String, HullSize> HULL_SIZES = new HashMap<>();

	static {
		HULL_SIZES.put("frigate", HullSize.FRIGATE);
		HULL_SIZES.put("destroyer", HullSize.DESTROYER);
		HULL_SIZES.put("cruiser", HullSize.CRUISER);
		HULL_SIZES.put("capital", HullSize.CAPITAL_SHIP);
	}

	private BattleNext_OrderUtils() {
	}

	public static HullSize hullSize(String value) {
		return HULL_SIZES.get(value);
	}

	public static List<ShipAPI> enemies(ShipAPI source) {
		return valid(AIUtils.getEnemiesOnMap(source));
	}

	public static List<ShipAPI> allies(ShipAPI source) {
		return valid(AIUtils.getAlliesOnMap(source));
	}

	private static List<ShipAPI> valid(List<ShipAPI> ships) {
		List<ShipAPI> result = new ArrayList<>();
		for (ShipAPI ship : ships) {
			if (isValid(ship)) {
				result.add(ship);
			}
		}
		return result;
	}

	public static boolean isValid(ShipAPI ship) {
		return ship.isAlive() && !ship.isFighter() && !ship.isDrone() && !ship.isStationModule();
	}

	public static List<BattleObjectiveAPI> enemyObjectives(ShipAPI source) {
		List<BattleObjectiveAPI> result = new ArrayList<>();
		for (BattleObjectiveAPI objective : Global.getCombatEngine().getObjectives()) {
			if (objective.getOwner() != source.getOwner()) {
				result.add(objective);
			}
		}
		return result;
	}

	public static List<BattleObjectiveAPI> allyObjectives(ShipAPI source) {
		List<BattleObjectiveAPI> result = new ArrayList<>();
		for (BattleObjectiveAPI objective : Global.getCombatEngine().getObjectives()) {
			if (objective.getOwner() == source.getOwner()) {
				result.add(objective);
			}
		}
		return result;
	}

	public static float factor(String value) {
		char last = value.charAt(value.length() - 1);
		return (last == '%' || last == '﹪') ? Float.parseFloat(value.substring(0, value.length() - 1)) * .01f : Float.parseFloat(value);
	}

	public static float self(String value, float alternative) {
		return SELF.equals(value) ? alternative : Float.parseFloat(value);
	}

	public static boolean percent(float value) {
		return value <= 1f;
	}

	public static int assignmentCount(AssignmentTargetAPI target, CombatTaskManagerAPI tasks) {
		int count = 0;
		for (ShipAPI ship : Global.getCombatEngine().getShips()) {
			if (tasks.getAssignmentFor(ship) != null && tasks.getAssignmentFor(ship).getTarget() == target) {
				count++;
			}
		}
		return count;
	}
}
