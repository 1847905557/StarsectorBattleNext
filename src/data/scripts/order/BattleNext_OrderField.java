package data.scripts.order;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import data.order.BattleNext_OrderStarter.Action;
import data.order.BattleNext_OrderUtils;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_OrderField {

	public static final String KEY_PREFIX = "BattleNext_OrderField_";
	private final CombatEngineAPI engine;
	private final int owner;
	private final boolean ally;
	private final CombatFleetManagerAPI manager;
	private final CombatTaskManagerAPI tasks;
	private final Map<AssignmentTargetAPI, OwnedTask> targets = new IdentityHashMap<>();
	private final Map<ShipAPI, Participation> participants = new IdentityHashMap<>();
	private final Map<ShipAPI, Retreat> retreats = new IdentityHashMap<>();

	private BattleNext_OrderField(CombatEngineAPI engine, int owner, boolean ally) {
		this.engine = engine;
		this.owner = owner;
		this.ally = ally;
		manager = engine.getFleetManager(owner);
		tasks = manager.getTaskManager(ally);
	}

	@LifecycleEntry
	public static BattleNext_OrderField getInstance(CombatEngineAPI engine, int owner, boolean ally) {
		String key = KEY_PREFIX + owner + (ally ? "_ally" : "");
		Object value = engine.getCustomData().get(key);
		if (value instanceof BattleNext_OrderField field) return field;
		BattleNext_OrderField field = new BattleNext_OrderField(engine, owner, ally);
		engine.getCustomData().put(key, field);
		return field;
	}

	private boolean belongs(ShipAPI ship) {
		return ship.getOwner() == owner && ship.isAlly() == ally;
	}

	public boolean hasExternalAssignment(ShipAPI ship) {
		AssignmentInfo current = tasks.getAssignmentFor(ship);
		Participation participant = participants.get(ship);
		if (participant != null && current == participant.task.assignment) return false;
		Retreat retreat = retreats.get(ship);
		if (retreat != null && ownsRetreat(ship, retreat)) return false;
		return current != null || ship.isRetreating();
	}

	public AssignmentTargetAPI getCommandTarget(ShipAPI ship, Object command) {
		Participation participant = participants.get(ship);
		if (participant != null && participant.command == command
				&& tasks.getAssignmentFor(ship) == participant.task.assignment) {
			return participant.task.assignment.getTarget();
		}
		Retreat retreat = retreats.get(ship);
		if (retreat != null && retreat.command == command && ownsRetreat(ship, retreat)) {
			return manager.getDeployedFleetMember(ship);
		}
		return null;
	}

	public float getPriorityOnTarget(AssignmentTargetAPI target) {
		OwnedTask task = targets.get(target);
		float priority = Float.NEGATIVE_INFINITY;
		for (Participation participant : participants.values()) {
			if (participant.task == task) priority = Math.max(priority, participant.priority);
		}
		return priority;
	}

	public boolean apply(ShipAPI ship, Action action, CombatAssignmentType type,
			AssignmentTargetAPI target, float priority, Object command) {

		if (hasExternalAssignment(ship)) return false;
		if (action == Action.CLEAN) {
			releaseShip(ship);
			return true;
		}
		DeployedFleetMemberAPI member = manager.getDeployedFleetMember(ship);
		if (member == null) return false;
		if (action == Action.RETREAT) {
			if (!member.canBeGivenRetreatOrders()) return false;
			Retreat previous = retreats.get(ship);
			if (previous != null && ownsRetreat(ship, previous)) {
				retreats.put(ship, new Retreat(previous.assignment, command));
				return true;
			}
			releaseShip(ship);
			tasks.orderRetreat(member, false, false);
			retreats.put(ship, new Retreat(tasks.getAssignmentFor(ship), command));
			return true;
		}
		if (!member.canBeGivenOrders()) return false;
		if (!BattleNext_OrderUtils.isValidTarget(target, engine)) return false;
		OwnedTask existing = targets.get(target);
		for (AssignmentInfo assignment : tasks.getAllAssignments()) {
			if (assignment.getTarget() != target) continue;
			if (existing == null || assignment != existing.assignment) return false;
		}
		if (existing != null && existing.assignment.getType() != type) {
			if (hasExternalParticipants(existing)) return false;
			if (priority <= getPriorityOnTarget(target)) return false;
			List<ShipAPI> displaced = new ArrayList<>();
			for (var entry : participants.entrySet()) {
				if (entry.getValue().task == existing) displaced.add(entry.getKey());
			}
			for (ShipAPI displacedShip : displaced) {
				releaseShip(displacedShip);
			}
			existing = null;
		}
		Participation own = participants.get(ship);
		if (own != null && own.task == existing) {
			participants.put(ship, new Participation(existing, priority, command));
			return true;
		}
		releaseShip(ship);
		if (existing == null) {
			AssignmentInfo assignment = tasks.createAssignment(type, target, false);
			if (assignment == null) return false;
			existing = new OwnedTask(assignment);
			targets.put(target, existing);
		}
		tasks.giveAssignment(member, existing.assignment, false);
		if (tasks.getAssignmentFor(ship) != existing.assignment) return false;
		participants.put(ship, new Participation(existing, priority, command));
		return true;
	}

	private boolean hasExternalParticipants(OwnedTask task) {
		for (DeployedFleetMemberAPI member : task.assignment.getAssignedMembers()) {
			ShipAPI ship = member.getShip();
			if (ship == null || !engine.isEntityInPlay(ship)) continue;
			if (!belongs(ship)) continue;
			if (tasks.getAssignmentFor(ship) != task.assignment) continue;
			Participation participant = participants.get(ship);
			if (participant == null || participant.task != task) return true;
		}
		return false;
	}

	private boolean ownsRetreat(ShipAPI ship, Retreat retreat) {
		AssignmentInfo current = tasks.getAssignmentFor(ship);
		return retreat.assignment != null ? current == retreat.assignment
				: current == null && ship.isRetreating();
	}

	public void releaseShip(ShipAPI ship) {
		Participation participant = participants.remove(ship);
		Retreat retreat = retreats.remove(ship);
		boolean writable = !tasks.isInFullRetreat() && !tasks.isFullAssault();
		DeployedFleetMemberAPI member = manager.getDeployedFleetMemberEvenIfDisabled(ship);
		if (writable && member != null && participant != null
				&& tasks.getAssignmentFor(ship) == participant.task.assignment) {
			tasks.orderSearchAndDestroy(member, false);
		}
		if (writable && member != null && retreat != null && ownsRetreat(ship, retreat)) {
			tasks.orderSearchAndDestroy(member, false);
			ship.setRetreating(false, false);
			if (retreat.assignment != null && retreat.assignment.getAssignedMembers().isEmpty()
					&& tasks.getAllAssignments().contains(retreat.assignment)) {
				tasks.removeAssignment(retreat.assignment);
			}
		}
		pruneAssignments();
	}

	public void reconcile(List<ShipAPI> eligible) {

		List<AssignmentInfo> current = tasks.getAllAssignments();
		for (ShipAPI ship : new ArrayList<>(participants.keySet())) {
			Participation participant = participants.get(ship);
			if (!eligible.contains(ship) || !belongs(ship)
					|| tasks.getAssignmentFor(ship) != participant.task.assignment
					|| !current.contains(participant.task.assignment)
					|| !BattleNext_OrderUtils.isValidTarget(participant.task.assignment.getTarget(), engine)) {
				releaseShip(ship);
			}
		}
		for (ShipAPI ship : new ArrayList<>(retreats.keySet())) {
			if (!eligible.contains(ship) || !belongs(ship) || !ownsRetreat(ship, retreats.get(ship))) {
				releaseShip(ship);
			}
		}
		pruneAssignments();
	}

	public void pruneAssignments() {
		List<AssignmentInfo> current = tasks.getAllAssignments();
		for (var entry : new ArrayList<>(targets.entrySet())) {
			OwnedTask task = entry.getValue();
			if (!current.contains(task.assignment)) {
				participants.values().removeIf(participant -> participant.task == task);
				targets.remove(entry.getKey());
				continue;
			}
			if (participants.values().stream().anyMatch(participant -> participant.task == task)) continue;
			if (!hasExternalParticipants(task)) tasks.removeAssignment(task.assignment);
			targets.remove(entry.getKey());
		}
	}

	public void cleanup() {
		for (ShipAPI ship : new ArrayList<>(participants.keySet())) {
			releaseShip(ship);
		}
		for (ShipAPI ship : new ArrayList<>(retreats.keySet())) {
			releaseShip(ship);
		}
		pruneAssignments();
		String key = KEY_PREFIX + owner + (ally ? "_ally" : "");
		if (engine.getCustomData().get(key) == this) engine.getCustomData().remove(key);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	private record OwnedTask(AssignmentInfo assignment) { }

	private record Participation(OwnedTask task, float priority, Object command) { }

	private record Retreat(AssignmentInfo assignment, Object command) { }
}
