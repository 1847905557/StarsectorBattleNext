package data.scripts.order;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import data.scripts.battlesnext.util.annotation.PublicData;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public final class BattleNext_AssignmentIndex implements AutoCloseable {

	private static final ThreadLocal<BattleNext_AssignmentIndex> CURRENT = new ThreadLocal<>();
	private final CombatEngineAPI engine;
	private final Map<CombatTaskManagerAPI, TaskCounts> managers = new IdentityHashMap<>();

	private BattleNext_AssignmentIndex(CombatEngineAPI engine) {
		this.engine = engine;
	}

	public static BattleNext_AssignmentIndex begin(CombatEngineAPI engine) {
		BattleNext_AssignmentIndex index = new BattleNext_AssignmentIndex(engine);
		CURRENT.set(index);
		return index;
	}

	public static Integer count(AssignmentTargetAPI target, CombatAssignmentType type,
			CombatTaskManagerAPI tasks, CombatEngineAPI engine) {
		BattleNext_AssignmentIndex index = CURRENT.get();
		if (index == null || index.engine != engine) return null;

		TaskCounts counts = index.managers.get(tasks);
		if (counts == null) {
			counts = new TaskCounts(tasks, engine);
			index.managers.put(tasks, counts);
		}

		TargetCounts selected = counts.targets.get(target);
		if (selected == null) return 0;
		return type == null ? selected.total : selected.types.getOrDefault(type, 0);
	}

	public static void changed(CombatTaskManagerAPI tasks, ShipAPI ship) {
		BattleNext_AssignmentIndex index = CURRENT.get();
		if (index == null) return;

		TaskCounts counts = index.managers.get(tasks);
		if (counts != null) counts.update(ship, tasks.getAssignmentFor(ship));
	}

	public static List<AssignmentInfo> assignments(CombatTaskManagerAPI tasks, CombatEngineAPI engine) {
		BattleNext_AssignmentIndex index = CURRENT.get();
		if (index == null || index.engine != engine) return tasks.getAllAssignments();
		return index.managers.computeIfAbsent(tasks, ignored -> new TaskCounts(tasks, engine)).assignments;
	}

	public static void created(CombatTaskManagerAPI tasks, AssignmentInfo assignment) {
		BattleNext_AssignmentIndex index = CURRENT.get();
		if (index != null && index.managers.containsKey(tasks)) index.managers.get(tasks).track(assignment);
	}

	public static void removed(CombatTaskManagerAPI tasks, AssignmentInfo assignment) {
		BattleNext_AssignmentIndex index = CURRENT.get();
		if (index == null || !index.managers.containsKey(tasks)) return;

		TaskCounts counts = index.managers.get(tasks);
		counts.assignments.remove(assignment);
		counts.ships.values().removeIf(previous -> {
			if (previous.assignment() != assignment) return false;
			counts.add(previous, -1);
			return true;
		});
	}

	@Override
	public void close() {
		CURRENT.remove();
	}

	private static final class TaskCounts {
		private final List<AssignmentInfo> assignments;
		private final Map<AssignmentTargetAPI, TargetCounts> targets = new IdentityHashMap<>();
		private final Map<ShipAPI, Assigned> ships = new IdentityHashMap<>();

		private TaskCounts(CombatTaskManagerAPI tasks, CombatEngineAPI engine) {
			assignments = new ArrayList<>(tasks.getAllAssignments());
			for (AssignmentInfo assignment : assignments) {
				for (DeployedFleetMemberAPI member : assignment.getAssignedMembers()) {
					ShipAPI ship = member.getShip();
					if (ship != null && ship.isAlive() && engine.isEntityInPlay(ship) && tasks.getAssignmentFor(ship) == assignment) {
						update(ship, assignment);
					}
				}
			}
		}

		private void update(ShipAPI ship, AssignmentInfo assignment) {
			Assigned previous = ships.remove(ship);
			if (previous != null) add(previous, -1);

			if (assignment != null) {
				track(assignment);
				Assigned current = new Assigned(assignment, assignment.getTarget(), assignment.getType());
				ships.put(ship, current);
				add(current, 1);
			}
		}

		private void track(AssignmentInfo assignment) {
			if (!assignments.contains(assignment)) assignments.add(assignment);
		}

		private void add(Assigned assignment, int amount) {
			TargetCounts counts = targets.computeIfAbsent(assignment.target(), ignored -> new TargetCounts());
			counts.total += amount;
			counts.types.merge(assignment.type(), amount, Integer::sum);
		}
	}

	private record Assigned(AssignmentInfo assignment, AssignmentTargetAPI target, CombatAssignmentType type) {

	}

	@PublicData
	private static final class TargetCounts {
		public final Map<CombatAssignmentType, Integer> types = new EnumMap<>(CombatAssignmentType.class);
		public int total;
	}
}
