package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BestTargetEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.PursuitAttackManeuver.
 * Orbits into a firing solution on a pursued ship, holding the band between
 * 0.9x and 1.3x the pursuit range.
 */
public class BattleNext_PursuitAttackManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private ShipAPI targetShip;
	private float angleOffset;
	private float pursuitRange;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float interceptRange;
	private boolean done = true;
	private IntervalUtil intervalTracker = new IntervalUtil(0.5F, 1.0F);
	private float angleToTarget;
	private boolean useFixedRange = false;
	private float desiredFacing;
	private float baseRange;
	private float distToIntercept = Float.MAX_VALUE;

	public BattleNext_PursuitAttackManeuver(ShipAPI ship, ShipAPI targetShip, float angleOffset, float baseRange, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.targetShip = targetShip;
		this.angleOffset = angleOffset;
		this.baseRange = baseRange;
		// vanilla oddity: the target radius is multiplied by the distance instead
		// of added to it
		this.interceptRange = ship.getCollisionRadius() + targetShip.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(ship.getLocation(), targetShip);
		this.pursuitRange = this.baseRange + this.interceptRange;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.angleToTarget = BattleNext_ShipAIHelper.getAngleToEntity(ship, targetShip);
	}

	public BattleNext_PursuitAttackManeuver(ShipAPI ship, ShipAPI targetShip, float angleOffset, float baseRange, float fixedAngleToTarget, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.targetShip = targetShip;
		this.angleOffset = angleOffset;
		this.baseRange = baseRange;
		// vanilla oddity: the target radius is multiplied by the distance instead
		// of added to it
		this.interceptRange = ship.getCollisionRadius() + targetShip.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(ship.getLocation(), targetShip);
		this.pursuitRange = this.baseRange + this.interceptRange;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		if (fixedAngleToTarget != 0.0F) {
			this.angleToTarget = fixedAngleToTarget;
			this.useFixedRange = true;
		} else {
			// intentionally kept dead: vanilla assigns the parameter but never reads
			// it afterwards
			fixedAngleToTarget = BattleNext_ShipAIHelper.getAngleToEntity(ship, targetShip);
		}
	}

	@Override
	public void advance(float amount) {
		this.intervalTracker.advance(amount);
		if (!this.maneuverAPI.keepTarget() && this.intervalTracker.intervalElapsed()) {
			if (!this.useFixedRange) {
				if (this.maneuverAPI.keepTarget() && this.targetShip != null) {
					this.angleToTarget = BattleNext_ShipAIHelper.getAngleToEntity(this.ship, this.targetShip);
				} else {
					BattleNext_BestTargetEval eval = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
					if (eval != null) {
						this.angleToTarget = eval.getRange();
						this.targetShip = eval.getTarget();
					}
				}
			}

			this.interceptRange = this.ship.getCollisionRadius()
				+ this.targetShip.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(this.ship.getLocation(), this.targetShip);
			this.pursuitRange = this.baseRange + this.interceptRange;
		}

		if (this.targetShip != null) {
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
			float speedFraction = this.ship.getVelocity().length() / this.ship.getMaxSpeed();
			float velocityAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getVelocity());
			float lead = Float.MAX_VALUE;
			if (this.maneuverAPI.getAttackingGroup() != null) {
				lead = this.maneuverAPI.getAttackingGroup().getRange();
			} else if (this.maneuverAPI.getAttackAI() != null) {
				lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
			}

			Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
			this.distToIntercept = BattleNext_ShipAIHelper.getAngle(this.ship.getLocation(), aimPoint);
			if (BattleNext_ShipAIHelper.isShipAboutToBeHit(this.ship, this.targetShip)) {
				this.flockingAI.addCollisionObject(this.targetShip);
			}

			if (!(distance > this.pursuitRange * 1.3F)
				&& !(distance < this.pursuitRange * 0.9F)
				&& !(speedFraction < 0.5F)
				&& !(BattleNext_ShipAIHelper.getAngleDiff(this.distToIntercept, velocityAngle) < 80.0F)) {
				this.done = true;
			} else {
				this.done = false;
				float orbitOffset = this.pursuitRange * 1.3F;
				float hypotenuse = (float) Math.sqrt(orbitOffset * orbitOffset + distance * distance);
				float orbitAngle = (float) Math.toDegrees((float) Math.asin(orbitOffset / hypotenuse));
				if (distance < this.pursuitRange && this.targetShip.getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
					orbitAngle += 45.0F;
				} else if (distance > this.pursuitRange * 1.5F) {
					orbitAngle = 0.0F;
				}

				Vector2f headingVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing());
				float turnSign;
				if (this.angleToTarget != 0.0F) {
					turnSign = -1.0F * this.angleToTarget;
				} else {
					turnSign = -1.0F * BattleNext_ShipAIHelper.getTurnDirection(headingVector, BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.distToIntercept));
					if (turnSign == 0.0F) {
						turnSign = Math.signum((float) Math.random() - 0.5F);
					}
				}

				this.desiredFacing = this.distToIntercept + turnSign * orbitAngle;
				this.done = false;
			}
		}
	}

	public static float computeTurnAngle(float rangeDelta, Vector2f from, Vector2f to) {
		float distance = BattleNext_ShipAIHelper.getDistance(from, to);
		float overshoot = rangeDelta - distance;
		float turnAngle;
		if (overshoot > 0.0F) {
			turnAngle = 90.0F + Math.min(Math.abs(overshoot) * 0.25F, 90.0F);
		} else {
			turnAngle = -90.0F + Math.min(Math.abs(overshoot) * 0.25F, 90.0F);
		}

		return turnAngle;
	}

	@Override
	public boolean isDone() {
		return this.done;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.targetShip;
	}

	@Override
	public float getDesiredFacing() {
		return this.desiredFacing;
	}

	@Override
	public void init() {
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetShip);
		BattleNext_ShipAIHelper.turnToFace(this.ship, aimPoint, this.angleOffset, predictedAngleChange);
		if (this.ship.getVelocity().length() > 10.0F) {
			BattleNext_ShipAIHelper.giveMoveCommands(this.ship, BattleNext_ShipAIHelper.getAngleFast(this.ship.getVelocity()), 1000.0F);
		}
	}

	@Override
	public float getTargetFacing() {
		return this.isDone() ? this.distToIntercept : Float.MAX_VALUE;
	}
}
