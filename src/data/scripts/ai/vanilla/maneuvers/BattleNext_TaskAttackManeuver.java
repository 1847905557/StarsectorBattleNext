package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.BattleNext_BestTargetEval;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.TaskAttackManeuver.
 * Moves toward the maneuver target and bends the approach around avoid orders.
 */
public class BattleNext_TaskAttackManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private Vector2f destPoint;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private float weaponRange = 0.0F;
	private IntervalUtil decisionTracker = new IntervalUtil(0.5F, 1.0F);
	private boolean avoidingFlag = false;
	private Vector2f moveDest;
	private ShipAPI targetShip;
	private ShipAPI attackTarget;
	private float targetDistance;
	private float maxRange = Float.MAX_VALUE;
	private boolean unusedFlag = false;
	private float currentRange = Float.MAX_VALUE;
	// vanilla shared this flag statically across maneuver instances (a static
	// method writes it); the project rules forbid mutable statics, so the flag
	// lives in a final atomic holder that keeps the cross-instance semantics
	private static final java.util.concurrent.atomic.AtomicBoolean AVOIDING =
		new java.util.concurrent.atomic.AtomicBoolean(false);

	public BattleNext_TaskAttackManeuver(ShipAPI ship, Vector2f destPoint, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.destPoint = destPoint;
		this.maneuverAPI = maneuverAPI;
		this.updateTarget();
	}

	private void updateTarget() {
		this.targetDistance = 0.0F;
		BattleNext_BestTargetEval eval = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
		if (eval != null) {
			this.targetShip = eval.getTarget();
			this.moveDest = eval.getAimPoint();
			this.targetDistance = eval.getRange();
			this.attackTarget = eval.getAlternateTarget();
			if (this.attackTarget == null) {
				this.attackTarget = this.targetShip;
			}
		} else {
			this.targetShip = BattleNext_ShipAIHelper.getNearestEnemyInRangeIncludingFighters(this.ship, 2200.0F);
			if (this.targetShip != null) {
				this.moveDest = this.targetShip.getLocation();
				this.attackTarget = this.targetShip;
			} else {
				this.moveDest = null;
				this.attackTarget = null;
			}
		}
	}

	private float computeMaxRange() {
		if (this.targetShip == null) {
			return Float.MAX_VALUE;
		}

		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		// vanilla oddity: despite the name this returns an aim angle, not a range
		return BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), aimPoint);
	}

	@Override
	public float getTargetFacing() {
		return this.maxRange;
	}

	@Override
	public void advance(float amount) {
		this.maxRange = this.computeMaxRange();
		this.currentRange = computeManeuverAngle(this.ship, this.targetShip, this.attackTarget, this.moveDest, this.destPoint, this.targetDistance);
		this.avoidingFlag = AVOIDING.get();
		this.decisionTracker.advance(amount);
		if (this.decisionTracker.intervalElapsed()) {
			this.updateTarget();
			ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(this.ship);
			if (nearestEnemy != null) {
				this.weaponRange = this.maneuverAPI.getAttackAI().getAimAngleForPoint(nearestEnemy.getLocation(), nearestEnemy.getCollisionRadius());
			} else {
				this.weaponRange = 0.0F;
			}
		}
	}

	public static float computeManeuverAngle(ShipAPI ship, CombatEntityAPI target, ShipAPI attackTarget, Vector2f moveDest, Vector2f destPoint, float targetDistance) {
		Vector2f avoidLocation = null;
		Vector2f attackLocation = null;
		if (target == null) {
			float nearestAvoidDistance = Float.MAX_VALUE;

			// approximation: engine CombatTask is not exposed; the side's assignments
			// stand in for its task list at the initial avoid-target search.
			for (AssignmentInfo task : BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly()).getAllAssignments()) {
				if (task.getType() == CombatAssignmentType.AVOID) {
					Vector2f taskLocation = task.getTarget().getLocation();
					float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), taskLocation);
					float maxDistance = 5000.0F;
					if (distance < maxDistance && distance < nearestAvoidDistance) {
						nearestAvoidDistance = distance;
						avoidLocation = task.getTarget().getLocation();
					}
				}
			}

			if (avoidLocation == null) {
				avoidLocation = new Vector2f(1.0E8F, 0.0F);
			}

			attackLocation = new Vector2f(avoidLocation);
		} else {
			avoidLocation = target.getLocation();
			attackLocation = attackTarget.getLocation();
		}

		if (moveDest == null) {
			moveDest = avoidLocation;
		}

		float distanceToAvoid = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), avoidLocation);
		float distanceToMoveDest = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), moveDest);
		float distanceToAttackLocation = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), attackLocation);
		float pullWeight = 1.0F - distanceToAvoid / 5500.0F;
		if (pullWeight < 0.0F) {
			pullWeight = 0.0F;
		}

		if (pullWeight > 1.0F) {
			pullWeight = 1.0F;
		}

		Vector2f awayVector = Vector2f.sub(ship.getLocation(), moveDest, new Vector2f());
		BattleNext_ShipAIHelper.normalise(awayVector);
		awayVector.scale(pullWeight);
		float awayAngle = BattleNext_ShipAIHelper.getAngleFast(awayVector);
		float sideAngle = 0.0F;
		if (targetDistance > 0.0F) {
			sideAngle = awayAngle + 90.0F;
		} else if (targetDistance < 0.0F) {
			sideAngle = awayAngle - 90.0F;
		}

		if (targetDistance != 0.0F) {
			Vector2f sideUnit = BattleNext_ShipAIHelper.getUnitVectorAtAngle(sideAngle);
			sideUnit.scale(1.0F - pullWeight);
			Vector2f.add(awayVector, sideUnit, awayVector);
		}

		if (distanceToAttackLocation > 1500.0F) {
			awayVector.scale(0.0F);
		}

		float nearestAvoidSteerDistance = Float.MAX_VALUE;
		Vector2f nearestAvoidSteerVector = null;

		for (AssignmentInfo task : BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly()).getAllAssignments()) {
			if (task.getType() == CombatAssignmentType.AVOID) {
				// approximation: CombatTask exposes one designated deployed member;
				// AssignmentInfo supplies only its assigned-member list, so ownership
				// filtering follows those currently assigned ships.
				boolean assignedFriendly = false;
				for (com.fs.starfarer.api.combat.DeployedFleetMemberAPI member : task.getAssignedMembers()) {
					ShipAPI assignedShip = member.getShip();
					if (assignedShip != null && !BattleNext_ShipAIHelper.isShipEnemy(ship, assignedShip)) {
						assignedFriendly = true;
						break;
					}
				}
				if (assignedFriendly) continue;
				Vector2f taskLocation = task.getTarget().getLocation();
				float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), taskLocation);
				float maxDistance = 5000.0F;
				if (distance < maxDistance) {
					float steerWeight = 1.0F - distance / maxDistance;
					Vector2f steerVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(BattleNext_ShipAIHelper.getAngleFast(taskLocation, ship.getLocation()));
					steerVector.scale(steerWeight);
					Vector2f.add(awayVector, steerVector, awayVector);
					// intentionally kept dead: vanilla compares against Float.MAX_VALUE with
					// an inverted bound, so this never fires and the close-avoid boost below
					// stays unreachable
					if (nearestAvoidSteerDistance < distance) {
						nearestAvoidSteerDistance = distance;
						nearestAvoidSteerVector = steerVector;
					}
				}
			}
		}

		if (nearestAvoidSteerDistance < 1000.0F && nearestAvoidSteerVector != null) {
			nearestAvoidSteerVector.scale(10.0F);
			Vector2f.add(awayVector, nearestAvoidSteerVector, awayVector);
		}

		if (awayVector.length() > 0.0F) {
			AVOIDING.set(true);
		} else {
			AVOIDING.set(false);
		}

		if (destPoint != null) {
			float destHeading = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), destPoint);
			Vector2f destUnit = BattleNext_ShipAIHelper.getUnitVectorAtAngle(destHeading);
			destUnit.scale(0.5F);
			Vector2f.add(awayVector, destUnit, awayVector);
		}

		return BattleNext_ShipAIHelper.getAngleFast(awayVector);
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public float getDesiredFacing() {
		return this.currentRange;
	}

	@Override
	public void init() {
		Vector2f shipLocation = this.ship.getLocation();
		float distanceToDest = BattleNext_ShipAIHelper.getDistance(shipLocation, this.destPoint);
		Vector2f approachPoint = this.destPoint;
		if (distanceToDest < 2000.0F) {
			approachPoint = BattleNext_ShipAIHelper.getInterceptPoint(this.ship, this.destPoint, new Vector2f(), 0.0F, false, true);
		}

		float closeRange = 500.0F;
		float currentSpeed = this.ship.getVelocity().length();
		float farRange = 2000.0F;
		if (this.maneuverAPI != null) {
			farRange = this.maneuverAPI.getOptimalNonMissileRange() + 1000.0F;
		}

		if (distanceToDest < closeRange && this.targetShip == null) {
			if (currentSpeed > 2.0F) {
				this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
			}

			ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(this.ship);
			if (nearestEnemy != null) {
				BattleNext_ShipAIHelper.turnToFace(this.ship, nearestEnemy.getLocation(), this.weaponRange);
			}
		} else if (this.targetShip == null && !this.avoidingFlag) {
			float approachHeading = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), approachPoint);
			if (BattleNext_ShipAIHelper.getAngleDiff(approachHeading, this.ship.getFacing()) < 2.0F && this.ship.getVelocity().length() > 2.0F) {
				this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
			} else {
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), approachPoint), 10000.0F);
			}

			ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(this.ship);
			float enemyDistance = Float.MAX_VALUE;
			float maxSpeed = this.ship.getMaxSpeed();
			if (nearestEnemy != null) {
				enemyDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), nearestEnemy.getLocation());
			}

			if (nearestEnemy != null && !this.ship.isFighter() && enemyDistance < farRange && this.ship.getVelocity().length() > maxSpeed * 0.5F) {
				BattleNext_ShipAIHelper.turnToFace(this.ship, nearestEnemy.getLocation(), this.weaponRange);
			} else {
				// vanilla passes the destination through the velocity-compensated
				// receiver overload; the helper's plain ship overload stands in
				// vanilla resolves to the receiver-family turnToFace (1.0F tolerance with stop-arc projection)
				BattleNext_ShipAIHelper.turnToFace(this.ship, approachPoint, 0.0F);
			}
		} else {
			if (this.currentRange != Float.MAX_VALUE) {
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.currentRange, 10000.0F);
			}

			if (this.maxRange != Float.MAX_VALUE) {
				float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetShip);
				BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.getTargetFacing(), predictedAngleChange);
			}
		}
	}

	// intentionally kept dead: vanilla dev leftover test method
	public static void main(String[] args) {
		Vector2f vector = new Vector2f(100.0F, 0.0F);
		BattleNext_ShipAIHelper.normalise(vector);
		System.out.println(vector);
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.targetShip;
	}
}
