package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.*;
import data.scripts.tools.BattleNext_Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.TaskMoveManeuver.
 * Moves toward the current task destination, blending in target pursuit and
 * intercept orders.
 */
public class BattleNext_TaskMoveManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private Vector2f destPoint;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private float weaponRange = 0.0F;
	private IntervalUtil decisionTracker = new IntervalUtil(0.5F, 1.0F);
	private Vector2f moveDest;
	private ShipAPI targetShip;
	private ShipAPI attackTarget;
	private float turnDirection;
	private boolean intercepting = false;
	private float maxRange = Float.MAX_VALUE;
	private boolean unusedFlag = false;
	private float currentRange = Float.MAX_VALUE;
	private float arrivalRange = 500.0F;

	public BattleNext_TaskMoveManeuver(ShipAPI ship, Vector2f destPoint, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.destPoint = destPoint;
		this.maneuverAPI = maneuverAPI;
		this.updateTarget();
	}

	public static BattleNext_TaskMoveManeuver rttMove(ShipAPI ship, Vector2f destPoint, BattleNext_ManeuverAPI maneuverAPI, float arrivalRange) {
		BattleNext_TaskMoveManeuver maneuver = new BattleNext_TaskMoveManeuver(ship, destPoint, maneuverAPI);
		maneuver.arrivalRange = arrivalRange;
		return maneuver;
	}

	private void updateTarget() {
		this.turnDirection = 0.0F;
		BattleNext_BestTargetEval eval = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
		if (eval != null) {
			this.targetShip = eval.getTarget();
			this.moveDest = eval.getAimPoint();
			this.turnDirection = eval.getRange();
			if (!eval.isBadTarget()) {
				float moveDestAngle = Misc.getAngleInDegrees(this.moveDest, this.ship.getLocation());
				float destAngle = Misc.getAngleInDegrees(this.moveDest, this.destPoint);
				float turnSign = Misc.getClosestTurnDirection(moveDestAngle, destAngle);
				if (turnSign > 0.0F) {
					this.turnDirection = 1.0F;
				} else {
					this.turnDirection = -1.0F;
				}
			}

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

		this.intercepting = false;
		ShipAPI interceptTarget = null;
		BattleNext_BasicShipAI basicShipAI = BattleNext_BasicShipAI.getActiveInstance(this.ship);
		if (basicShipAI != null) {
			BattleNext_OrderResponseModule orderResponse = basicShipAI.getOrderResponseModule();
			BattleNext_DirectOrders orders = orderResponse.getOrders();
			boolean interceptOrdered = orders != null && orders.getTaskType() == CombatAssignmentType.INTERCEPT;
			if (interceptOrdered && orders.getTaskTarget() != null) {
				if (orders.getTaskTarget() instanceof DeployedFleetMemberAPI taskMember) {
					if (!taskMember.isFighterWing()) {
						interceptTarget = taskMember.getShip();
					}
				}
				// approximation: the internal task target can wrap a deployed member;
				// AssignmentTargetAPI exposes only directly deployed members and world position.
			}

			if (interceptTarget != null) {
				float distance = Misc.getDistance(this.ship.getLocation(), interceptTarget.getLocation());
				distance -= this.ship.getCollisionRadius() + interceptTarget.getCollisionRadius();
				if (distance < 3500.0F) {
					this.targetShip = interceptTarget;
					this.intercepting = true;
				}
			}
		}

		if (this.ship.getFluxLevel() < 0.8F
			&& this.targetShip != null
			&& (this.targetShip.controlsLocked() || this.targetShip.isNonCombat(false) && !this.ship.isNonCombat(true))) {
			this.targetShip = null;
			this.moveDest = null;
			this.attackTarget = null;
		}

		if (this.targetShip != null) {
			this.weaponRange = this.maneuverAPI.getAttackAI().getAimAngleForPoint(this.targetShip.getLocation(), this.targetShip.getCollisionRadius());
			this.maneuverAPI.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, 1.0F, this.targetShip);
		} else {
			this.weaponRange = 0.0F;
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
		float facingOverride = this.getFacingOverride();
		return facingOverride != Float.MAX_VALUE ? facingOverride : this.maxRange;
	}

	private float getFacingOverride() {
		Object facingFlag = this.ship.getAIFlags().getCustom(AIFlags.FACING_OVERRIDE_FOR_MOVE_AND_ESCORT_MANEUVERS);
		return facingFlag instanceof Float ? (Float) facingFlag : Float.MAX_VALUE;
	}

	@Override
	public void advance(float amount) {
		this.maxRange = this.computeMaxRange();
		if (this.targetShip != null) {
			Vector2f destCopy = new Vector2f(this.destPoint);
			this.currentRange = computeManeuverAngle(this.ship, this.targetShip, this.attackTarget, this.moveDest, destCopy, this.turnDirection);
		} else {
			Vector2f destCopy = new Vector2f(this.destPoint);
			// intentionally kept dead: vanilla evaluates isPhased and discards the result
			this.ship.isPhased();
			destCopy = Misc.getPointAtRadius(destCopy, 500.0F, new Random(this.ship.getId().hashCode()));
			Vector2f shipLocation = this.ship.getLocation();
			this.currentRange = BattleNext_ShipAIHelper.getAngleFast(shipLocation, destCopy);
		}

		this.decisionTracker.advance(amount);
		if (this.decisionTracker.intervalElapsed()) {
			this.updateTarget();
		}
	}

	public static float computeManeuverAngle(ShipAPI ship, ShipAPI targetShip, ShipAPI attackTarget, Vector2f moveDest, Vector2f destPoint, float turnDirection) {
		if (moveDest == null) {
			moveDest = targetShip.getLocation();
		}

		float distanceToDest = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), destPoint);
		if (distanceToDest < 1000.0F) {
			return BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), destPoint);
		}

		float distanceToTarget = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), targetShip.getLocation());
		float distanceToMoveDest = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), moveDest);
		float distanceToAttackTarget = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), attackTarget.getLocation());
		float pullWeight = 1.0F - distanceToTarget / 5500.0F;
		if (pullWeight < 0.0F) {
			pullWeight = 0.0F;
		}

		if (pullWeight > 1.0F) {
			pullWeight = 1.0F;
		}

		Vector2f awayVector = Vector2f.sub(ship.getLocation(), moveDest, new Vector2f());
		BattleNext_ShipAIHelper.normalise(awayVector);
		awayVector.scale(pullWeight);
		float destHeading = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), destPoint);
		Vector2f destUnit = BattleNext_ShipAIHelper.getUnitVectorAtAngle(destHeading);
		float awayAngle = BattleNext_ShipAIHelper.getAngleFast(awayVector);
		float sideAngle = 0.0F;
		if (turnDirection > 0.0F) {
			sideAngle = awayAngle + 90.0F;
		} else if (turnDirection < 0.0F) {
			sideAngle = awayAngle - 90.0F;
		}

		if (turnDirection != 0.0F) {
			Vector2f sideUnit = BattleNext_ShipAIHelper.getUnitVectorAtAngle(sideAngle);
			sideUnit.scale(1.0F - pullWeight);
			Vector2f.add(awayVector, sideUnit, awayVector);
		}

		if (distanceToAttackTarget > 1500.0F) {
			awayVector.scale(0.0F);
		}

		destUnit.scale(0.5F);
		if (ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER)) {
			destUnit.scale(3.0F);
		}

		Vector2f.add(awayVector, destUnit, awayVector);
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

		float closeRange = this.arrivalRange;
		float currentSpeed = this.ship.getVelocity().length();
		float farRange = 2000.0F;
		if (this.maneuverAPI != null) {
			farRange = this.maneuverAPI.getOptimalNonMissileRange() + 2500.0F;
		}

		boolean facingHandled = false;
		float facingOverride = this.getFacingOverride();
		if (facingOverride != Float.MAX_VALUE) {
			BattleNext_ShipAIHelper.turnToFace(this.ship, this.weaponRange, facingOverride);
			facingHandled = true;
		}

		if (distanceToDest < closeRange && this.targetShip == null) {
			if (currentSpeed > 2.0F) {
				this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
			}

			ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(this.ship);
			if (nearestEnemy != null && !facingHandled) {
				BattleNext_ShipAIHelper.turnToFace(this.ship, nearestEnemy.getLocation(), this.weaponRange);
			}
		} else if (this.targetShip != null) {
			if (this.currentRange != Float.MAX_VALUE) {
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.currentRange, 10000.0F);
			}

			if (this.maxRange != Float.MAX_VALUE && !facingHandled) {
				BattleNext_ShipAIHelper.turnToFace(this.ship, this.targetShip.getLocation(), this.weaponRange);
			}
		} else {
			float approachHeading = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), approachPoint);
			if (BattleNext_ShipAIHelper.getAngleDiff(approachHeading, this.ship.getFacing()) < 2.0F && this.ship.getVelocity().length() > 2.0F) {
				this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
			} else {
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), approachPoint), 10000.0F);
			}

			if (!facingHandled) {
				ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(this.ship);
				if (this.intercepting
					&& this.targetShip != null
					&& this.ship.getHullSize().ordinal() > nearestEnemy.getHullSize().ordinal()
					&& this.ship.getHullSize().ordinal() >= HullSize.CAPITAL_SHIP.ordinal()) {
					boolean chaseTargetInstead = true;
					if (nearestEnemy != null && nearestEnemy != this.targetShip) {
						float enemyAngle = Misc.getAngleInDegrees(this.ship.getLocation(), nearestEnemy.getLocation());
						float targetAngle = Misc.getAngleInDegrees(this.ship.getLocation(), this.targetShip.getLocation());
						float angleBetween = Misc.getAngleDiff(enemyAngle, targetAngle);
						chaseTargetInstead = angleBetween > 30.0F;
					}

					if (chaseTargetInstead) {
						nearestEnemy = this.targetShip;
					}
				}

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
			}
		}
	}

	// intentionally kept dead: vanilla dev leftover test method
	public static void main(String[] args) {
		Vector2f vector = new Vector2f(100.0F, 0.0F);
		BattleNext_ShipAIHelper.normalise(vector);
		BattleNext_Logger.info(BattleNext_TaskMoveManeuver.class, "maneuver.vector.normalized",
				BattleNext_Logger.fields().put("vector", vector).put("arguments", args).build());
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.targetShip;
	}
}
