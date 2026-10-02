package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.AssignmentBasedManeuver.
 * Presses the current fleet assignment: closes on the assignment target,
 * backs off when threatened, and strafes across the target's fire.
 */
public class BattleNext_AssignmentBasedManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private ShipAPI target;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float desiredRange;
	private BattleNext_FlockingAI flockingAI;
	// intentionally kept dead: vanilla assigns this field but never reads it
	private boolean canDecelerate = true;
	private final float facingAngle;
	private boolean fleetEscaping = false;
	private boolean needHardTurn = false;
	private IntervalUtil updateIntervalTracker = new IntervalUtil(0.5F, 1.0F);
	private float turnDirection = 0.0F;
	private boolean backingOff = false;
	private boolean harassMode = false;
	private float targetBearing = Float.MAX_VALUE;

	public BattleNext_AssignmentBasedManeuver(ShipAPI ship, ShipAPI target, float facingAngle, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.facingAngle = facingAngle;
		float radiiSum = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		this.desiredRange = radiiSum * 2.0F + 100.0F;
		if (ship.getDeceleration() != 0.0F) {
			float stoppingDistance = ship.getMaxSpeed() * ship.getMaxSpeed() / ship.getDeceleration();
			this.desiredRange = this.desiredRange + Math.min(200.0F, stoppingDistance);
		} else {
			this.canDecelerate = false;
		}

		this.harassMode = this.shouldHarass(ship, target);
		float holdRange = maneuverAPI.getAttackAI().getMinRange() * 1.5F;
		if (this.harassMode && !maneuverAPI.getAttackAI().hasNonMissileWeapon()) {
			holdRange = maneuverAPI.getAttackAI().getMinMissileRange();
		}

		if (BattleNext_ShipAIHelper.isShipOrderedToAttack(ship)) {
			holdRange = maneuverAPI.getAttackAI().getMaxThreatRange();
		}

		this.desiredRange = holdRange * 0.5F + radiiSum;
		this.flockingAI = flockingAI;
		this.ship = ship;
		this.target = target;
		this.maneuverAPI = maneuverAPI;
		byte oppositeOwner = 0;
		if (ship.getOwner() == 0) {
			oppositeOwner = 1;
		}

		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(oppositeOwner);
		this.fleetEscaping = fleetManager != null && fleetManager.getGoal() == FleetGoal.ESCAPE;
	}

	public boolean shouldHarass(ShipAPI ship, ShipAPI target) {
		// intentionally kept dead: vanilla computes the strict non-combat flag but
		// never reads it
		boolean selfNonCombatStrict = ship.isNonCombat(true);
		boolean selfNonCombat = ship.isNonCombat(false);
		boolean targetNonCombat = target.isNonCombat(false);
		boolean harass = false;
		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner());
		if (fleetManager != null) {
			CombatTaskManagerAPI taskManager = fleetManager.getTaskManager(ship.isAlly());
			// approximation: engine DirectOrders are not exposed through the API; the
			// ship's assignment is the only source of the HARASS order
			AssignmentInfo orders = taskManager.getAssignmentFor(ship);
			if (orders != null && orders.getType() == CombatAssignmentType.HARASS) {
				harass = true;
			}
		}

		String personality = BattleNext_ShipAIHelper.getPersonality(ship);
		boolean interceptTask = BattleNext_ShipAIHelper.hasInterceptTask(ship);
		boolean orderedToAttack = BattleNext_ShipAIHelper.isShipOrderedToAttack(ship);
		boolean reckless = "reckless".equals(personality);
		boolean aggressive = "aggressive".equals(personality);
		if ((personality.equals("cautious") || personality.equals("timid") || selfNonCombat)
			&& (selfNonCombat || !targetNonCombat)
			&& !aggressive
			&& !reckless
			&& !orderedToAttack
			&& !interceptTask) {
			harass = true;
		}

		return harass;
	}

	@Override
	public void advance(float amount) {
		if (BattleNext_ShipAIHelper.isShipAboutToBeHit(this.ship, this.target)) {
			this.flockingAI.addCollisionObject(this.target);
		}

		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.target.getLocation());
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (distance > 3000.0F) {
			float speedFactor = 1.0F - Math.min(1.0F, (distance - 3000.0F) / 3000.0F) * 0.5F;
			lead = Math.max(50.0F, this.ship.getMaxSpeed() * speedFactor);
		} else if (this.maneuverAPI.getAttackAI() != null && distance < 1500.0F) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.target, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		this.targetBearing = BattleNext_ShipAIHelper.getAngle(this.ship.getLocation(), aimPoint);
		if (this.fleetEscaping
			&& distance > this.maneuverAPI.getAttackAI().getNonMissileMaxRange() + 1000.0F + this.target.getCollisionRadius()
			&& BattleNext_ShipAIHelper.getAngleDiff(this.targetBearing, 90.0F) < 45.0F) {
			this.targetBearing = 90.0F;
			this.needHardTurn = true;
		} else {
			this.needHardTurn = false;
		}

		this.updateIntervalTracker.advance(amount);
		if (this.updateIntervalTracker.intervalElapsed() && this.target != null) {
			float radiiSum = (this.ship.getCollisionRadius() + this.target.getCollisionRadius()) * 0.75F;
			float holdRange = this.maneuverAPI.getAttackAI().getMinRange() * 1.5F;
			if (this.harassMode && !this.maneuverAPI.getAttackAI().hasNonMissileWeapon() && this.maneuverAPI.getAttackAI().getMinMissileRange() < 1000000.0F) {
				holdRange = this.maneuverAPI.getAttackAI().getMinMissileRange();
			}

			if (BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)) {
				holdRange = this.maneuverAPI.getAttackAI().getMaxThreatRange();
			}

			this.desiredRange = holdRange * 0.5F + radiiSum;
			if (this.target.getHullLevel() < 0.25F) {
				float hullLoss = 1.0F - this.target.getHullLevel() / 0.25F;
				float explosionBase = 200.0F;
				if (this.ship.getShield() == null || this.ship.getFluxLevel() > 0.95F || this.ship.getShield().isOff()) {
					explosionBase = 400.0F;
				}

				this.desiredRange = Math.max(
					this.desiredRange, this.ship.getCollisionRadius() + DamagingExplosionSpec.getShipExplosionRadius(this.target) + explosionBase * hullLoss
				);
			}

			int threatsBehind = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, this.ship.getFacing() + 180.0F, 300.0F, Math.max(1500.0F, distance + 500.0F));
			if (threatsBehind > 0) {
				this.turnDirection = BattleNext_ShipAIHelper.getAngleToEntity(this.ship, this.target);
			} else {
				this.turnDirection = 0.0F;
			}

			float angleAwayFromTarget = BattleNext_ShipAIHelper.getAngleFast(this.target.getLocation(), this.ship.getLocation());
			float threatBehindTarget = BattleNext_ShipAIHelper.getTotalThreatInRangeExcludingShip(
				this.ship, this.target.getLocation(), angleAwayFromTarget, 190.0F, Math.max(1500.0F, distance + 500.0F), this.target, true
			);
			if (threatBehindTarget > 0.0F
				&& distance
					< Math.max(1500.0F, this.maneuverAPI.getOptimalNonMissileRange() * 2.0F + this.ship.getCollisionRadius() + this.target.getCollisionRadius())) {
				this.backingOff = true;
			} else {
				this.backingOff = false;
			}

			float threatScore = BattleNext_StrafeTargetManeuverV2.computeThreatScore(
				this.ship, this.target, this.ship.isNonCombat(true), this.target != null && this.targetIsNonCombatVariant(this.target)
			);
			float crMult = BattleNext_ShipAIHelper.getCRMult(this.ship);
			threatScore += crMult;
			if (this.ship.getHardFluxLevel() > threatScore) {
				this.backingOff = true;
			}

			if (!this.maneuverAPI.getConfig().backingOffWhileNotVentingAllowed && !this.ship.getFluxTracker().isOverloadedOrVenting()) {
				this.backingOff = false;
			}

			if (this.backingOff && BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)) {
				this.backingOff = false;
			}
		}
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.target;
	}

	@Override
	public float getDesiredFacing() {
		return !this.backingOff && !this.ship.getFluxTracker().isOverloadedOrVenting() ? this.targetBearing : this.targetBearing + 180.0F;
	}

	@Override
	public void init() {
		this.maneuverAPI.getAIFlags().setFlag(AIFlags.PURSUING);
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.target.getLocation());
		boolean withinRange = distance <= this.maneuverAPI.getAttackAI().getNonMissileMaxRange() + 100.0F + this.target.getCollisionRadius();
		float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.target);
		if (withinRange) {
			BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.targetBearing, predictedAngleChange);
		} else {
			float facing = this.targetBearing;
			if (this.targetBearing == Float.MAX_VALUE) {
				facing = BattleNext_ShipAIHelper.getAngle(this.ship.getLocation(), this.target.getLocation());
				this.targetBearing = facing;
			}

			if (this.needHardTurn) {
				facing = 90.0F;
			}

			BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, facing, predictedAngleChange);
		}

		float speedFraction = this.ship.getVelocity().length() / this.ship.getMaxSpeed();
		boolean worthAttacking = BattleNext_ShipAIHelper.isShipWorthAttacking(this.ship, this.target, this.maneuverAPI);
		float radiiSum = (this.ship.getCollisionRadius() + this.target.getCollisionRadius()) * 0.75F;
		this.maneuverAPI.getAIFlags().unsetFlag(AIFlags.MANEUVER_RANGE_FROM_TARGET);
		if (this.backingOff || this.ship.getFluxTracker().isOverloadedOrVenting()) {
			this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
		} else if (this.facingAngle != 1.0F && speedFraction > this.facingAngle) {
			this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
		} else {
			float holdRange = Math.max(this.maneuverAPI.getOptimalNonMissileRange() * 0.75F + radiiSum, this.desiredRange);
			if (holdRange > 1000.0F + radiiSum) {
				holdRange = 1000.0F + radiiSum;
			}

			this.maneuverAPI.getAIFlags().setFlag(AIFlags.MANEUVER_RANGE_FROM_TARGET, 0.5F, holdRange);
			if (distance < holdRange && !this.target.isFighter()) {
				float hullLoss = 1.0F - this.target.getHullLevel() / 0.25F;
				float explosionRange = this.ship.getCollisionRadius() + DamagingExplosionSpec.getShipExplosionRadius(this.target) + 100.0F * hullLoss;
				float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, this.target);
				if (distance < explosionRange || worthAttacking && distance < holdRange || closingSpeed > 30.0F) {
					this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
				}
			} else if (distance > this.desiredRange * 1.5F && !worthAttacking || distance > this.maneuverAPI.getOptimalNonMissileRange() + radiiSum && distance > this.desiredRange) {
				Vector2f bearingUnit = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetBearing);
				float speedAlongBearing = Vector2f.dot(bearingUnit, this.ship.getVelocity());
				if (speedAlongBearing < this.ship.getMaxSpeed() * 1.05F) {
					this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
				}
			}
		}

		float strafeSide = 0.0F;
		Vector2f relativeVelocity = Vector2f.sub(this.target.getVelocity(), this.ship.getVelocity(), new Vector2f());
		Vector2f relativeDirection = BattleNext_ShipAIHelper.normalise(new Vector2f(relativeVelocity));
		Vector2f awayVector = Vector2f.sub(this.target.getLocation(), this.ship.getLocation(), new Vector2f());
		BattleNext_ShipAIHelper.normalise(awayVector);
		if (this.fleetEscaping && distance > this.maneuverAPI.getAttackAI().getNonMissileMaxRange() + 1000.0F + this.target.getCollisionRadius()) {
			awayVector.set(0.0F, 1.0F);
		}

		float alignment = Vector2f.dot(awayVector, relativeDirection);
		float targetSpeed = this.target.getVelocity().length();
		float crossfireSpeed = (1.0F - Math.abs(alignment)) * targetSpeed;
		if (this.turnDirection != 0.0F) {
			if (this.turnDirection >= 0.0F) {
				this.ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
			} else {
				this.ship.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
			}
		} else if (crossfireSpeed > 5.0F && withinRange) {
			float velocityAngle = BattleNext_ShipAIHelper.getAngleFast(relativeVelocity);
			float leftDiff = BattleNext_ShipAIHelper.getAngleDiff(velocityAngle, this.ship.getFacing() + 90.0F);
			float rightDiff = BattleNext_ShipAIHelper.getAngleDiff(velocityAngle, this.ship.getFacing() - 90.0F);
			if (leftDiff > rightDiff) {
				strafeSide = 1.0F;
			} else {
				strafeSide = -1.0F;
			}

			if (strafeSide >= 0.0F) {
				this.ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
			} else {
				this.ship.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
			}
		}
	}

	@Override
	public float getTargetFacing() {
		return this.targetBearing;
	}
	// approximation: ShipVariantAPI.isNonCombat() does not exist; the engine formula
	// (carrier or civilian hint, without the combat hint) is reproduced here
	private static boolean targetIsNonCombatVariant(ShipAPI target) {
		if (target == null) {
			return false;
		}

		boolean nonCombat = target.getVariant().isCivilian()
			|| target.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER);
		nonCombat &= !target.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.COMBAT);
		return nonCombat;
	}
}
