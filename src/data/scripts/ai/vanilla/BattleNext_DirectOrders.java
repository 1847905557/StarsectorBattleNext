package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;

/**
 * Port of engine class com.fs.starfarer.combat.tasks.DirectOrders.
 * Approximation: the engine class wraps a CombatTask owned by the war-room task
 * manager, which the mod API does not expose. The port keeps the same accessors
 * but carries the assignment type and target instead of the internal task object.
 */
public class BattleNext_DirectOrders {
	private BattleNext_DirectOrdersType type;
	private CombatAssignmentType taskType;
	private AssignmentTargetAPI taskTarget;
	private Object param;

	public BattleNext_DirectOrders(BattleNext_DirectOrdersType type, CombatAssignmentType taskType, AssignmentTargetAPI taskTarget) {
		this.type = type;
		this.taskType = taskType;
		this.taskTarget = taskTarget;
	}

	public BattleNext_DirectOrdersType getType() {
		return this.type;
	}

	public void setType(BattleNext_DirectOrdersType type) {
		this.type = type;
	}

	public CombatAssignmentType getTaskType() {
		return this.taskType;
	}

	public AssignmentTargetAPI getTaskTarget() {
		return this.taskTarget;
	}

	public Object getTarget() {
		return this.param;
	}

	public void setTarget(Object param) {
		this.param = param;
	}
}
