package data.scripts.orders;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BattleNext_OrderField {
	public static final String KEY_PREFIX = "BattleNext_OrderField_";
	private final CombatFleetManagerAPI manager;
	private final CombatTaskManagerAPI tasks;
	private final Map<ShipAPI, OrderInfo> automation = new HashMap<>();
	private final List<AssignmentInfo> generated = new ArrayList<>();
	private final Set<ShipAPI> visited = new HashSet<>();

	private BattleNext_OrderField(CombatEngineAPI engine, int owner, boolean ally) {
		manager = engine.getFleetManager(owner);
		tasks = manager.getTaskManager(ally);
	}

	public static BattleNext_OrderField get(CombatEngineAPI engine, int owner, boolean ally) {
		String key = KEY_PREFIX + owner + (ally ? "_ally" : "");
		Object value = engine.getCustomData().get(key);
		if (value instanceof BattleNext_OrderField) return (BattleNext_OrderField)value;
		BattleNext_OrderField field = new BattleNext_OrderField(engine, owner, ally);
		engine.getCustomData().put(key, field);
		return field;
	}

	public boolean hasPlayerForcedAssignment(ShipAPI ship) {
		AssignmentInfo current = tasks.getAssignmentFor(ship);
		if (current == null) return false;
		OrderInfo generatedInfo = automation.get(ship);
		return generatedInfo == null || !sameAssignment(generatedInfo.assignment, current);
	}

	public AssignmentInfo getInfoFor(ShipAPI ship) { return tasks.getAssignmentFor(ship); }

	public CombatAssignmentType getAssignmentTypeOnTarget(AssignmentTargetAPI target) {
		for (AssignmentInfo info : tasks.getAllAssignments()) if (info.getTarget() == target) return info.getType();
		return null;
	}

	public OrderInfo getOneAutomationInfoOnTarget(AssignmentTargetAPI target) {
		for (OrderInfo info : automation.values()) if (info.assignment.getTarget() == target) return info;
		return null;
	}

	public void assignToAutomationInfo(ShipAPI ship, CombatAssignmentType type, AssignmentTargetAPI target, float priority) {
		if (type == null) {
			tasks.orderSearchAndDestroy(manager.getDeployedFleetMember(ship), false);
			automation.remove(ship);
			return;
		}
		AssignmentInfo info;
		if (type == CombatAssignmentType.RETREAT) {
			DeployedFleetMemberAPI member = (DeployedFleetMemberAPI)target;
			tasks.orderRetreat(member, false, false);
			info = new RetreatAssignmentInfo(member);
		} else {
			info = tasks.createAssignment(type, target, false);
			tasks.giveAssignment(manager.getDeployedFleetMember(ship), info, false);
		}
		generated.add(info);
		automation.put(ship, new OrderInfo(info, priority));
	}

	public void assignToAutomationInfo(ShipAPI ship, OrderInfo info) {
		tasks.giveAssignment(manager.getDeployedFleetMember(ship), info.assignment, false);
		automation.put(ship, info);
	}

	public boolean apply(ShipAPI ship, CombatAssignmentType type, AssignmentTargetAPI target, float priority) {
		if (hasPlayerForcedAssignment(ship)) return false;
		if (target == null) {
			assignToAutomationInfo(ship, null, null, priority);
			return true;
		}
		CombatAssignmentType existingType = getAssignmentTypeOnTarget(target);
		if (existingType == null) {
			assignToAutomationInfo(ship, type, target, priority);
			return true;
		}
		OrderInfo existingAutomation = getOneAutomationInfoOnTarget(target);
		if (existingAutomation == null) {
			if (ship.getOwner() != Misc.OWNER_PLAYER) {
				assignToAutomationInfo(ship, type, target, priority);
				return true;
			}
			return false;
		}
		AssignmentInfo current = getInfoFor(ship);
		if (existingAutomation.assignment.getType() == type && current != existingAutomation.assignment) {
			if (type != CombatAssignmentType.RETREAT) assignToAutomationInfo(ship, existingAutomation);
			return true;
		}
		if (priority <= existingAutomation.priority) return false;
		clearAllAutomationInfoOnTarget(target);
		assignToAutomationInfo(ship, type, target, priority);
		return true;
	}

	public void clearAllAutomationInfoOnTarget(AssignmentTargetAPI target) {
		Iterator<Map.Entry<ShipAPI, OrderInfo>> iterator = automation.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<ShipAPI, OrderInfo> entry = iterator.next();
			if (entry.getValue().assignment.getTarget() == target) {
				tasks.orderSearchAndDestroy(manager.getDeployedFleetMember(entry.getKey()), false);
				iterator.remove();
			}
		}
	}

	public void registerShip(ShipAPI ship) { visited.add(ship); }

	public void pruneAssignments() {
		List<AssignmentInfo> remove = new ArrayList<>();
		for (AssignmentInfo info : tasks.getAllAssignments()) {
			boolean assignedToVisited = false;
			for (ShipAPI ship : visited) if (sameAssignment(tasks.getAssignmentFor(ship), info)) { assignedToVisited = true; break; }
			if (!assignedToVisited && generated.contains(info)) remove.add(info);
		}
		for (AssignmentInfo info : remove) tasks.removeAssignment(info);
		generated.removeAll(remove);
		visited.clear();
	}

	private static boolean sameAssignment(AssignmentInfo first, AssignmentInfo second) {
		if (first == null || second == null) return false;
		if (first.getType() == CombatAssignmentType.RETREAT && second.getType() == CombatAssignmentType.RETREAT) return true;
		return first.getType() == second.getType() && first.getTarget() == second.getTarget();
	}

	public static final class OrderInfo {
		private final AssignmentInfo assignment;
		private final float priority;
		public OrderInfo(AssignmentInfo assignment, float priority) { this.assignment = assignment; this.priority = priority; }
		public AssignmentInfo getAssignment() { return assignment; }
		public float getPriority() { return priority; }
	}

	private static final class RetreatAssignmentInfo implements AssignmentInfo {
		private final DeployedFleetMemberAPI member;
		private RetreatAssignmentInfo(DeployedFleetMemberAPI member) { this.member = member; }
		public CombatAssignmentType getType() { return CombatAssignmentType.RETREAT; }
		public AssignmentTargetAPI getTarget() { return member; }
		public List<DeployedFleetMemberAPI> getAssignedMembers() { return List.of(member); }
	}
}
