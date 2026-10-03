package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.*;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.maneuvers.*;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.RetreatSystemAI.
 */
public class BattleNext_RetreatSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_AttackAIModule attackAI;
	private final BattleNext_FlockingAI flockingAI;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private final BattleNext_FiringSolutionEval eval;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final boolean hasRetreatHints;
	private final float retreatMaxSpeed;
	private float decisionTime = 1.0F;
	private float stableTime;

	public BattleNext_RetreatSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval,
			BattleNext_AttackAIModule attackAI, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.attackAI = attackAI;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.eval = new BattleNext_FiringSolutionEval(ship);
		this.system = ship.getSystem();
		JSONObject hints = system.getSpecAPI().getSpecJson().optJSONObject("aiHints");
		float burst = hints == null ? 0.0F : (float) hints.optDouble("burstMovementPotential", 0.0F);
		float activeSpeed = hints == null ? 0.0F : (float) hints.optDouble("activeSpeedIncrease", 0.0F);
		this.hasRetreatHints = burst > 0.0F && activeSpeed <= 0.0F && system.getSpecAPI().getActive() <= 1.0F;
		// approximation: ShipStats.create(spec) is internal; current public max
		// speed is the closest base for sustained engine systems.
		this.retreatMaxSpeed = hasRetreatHints ? burst : ship.getMaxSpeed() + activeSpeed;
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.isActive() || system.getCooldownRemaining() > 0.0F || system.isOutOfAmmo()
				|| ship.getEngineController().isDisabled()) return;
		if (ship.isFighter() && ship.getWing() != null && !ship.getWing().isLeader(ship)) {
			ShipAPI leader = ship.getWing().getLeader();
			if (leader != null && leader.getSystem() != null && leader.getSystem().isOn()) useSystem();
			return;
		}
		float turnLimit = Math.max(5.0F, ship.getTurnAcceleration() * 0.5F);
		stableTime = Math.abs(ship.getAngularVelocity()) < turnLimit ? stableTime + amount : 0.0F;
		boolean decide = interval.intervalElapsed();
		if (decisionTime > 1.0F) {
			if (stableTime > 0.0F) {
				decide = true;
				decisionTime = 0.0F;
			}
		} else decisionTime += amount;
		if (!decide) return;
		if (flags.hasFlag(AIFlags.BACK_OFF)) {
			if (target == null) return;
			float angle = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation());
			if (BattleNext_ShipAIHelper.getAngleDiff(angle, ship.getFacing()) < 100.0F) return;
		}

		boolean wrongDirection = false;
		Object movementDestination = flags.getCustom(AIFlags.MOVEMENT_DEST);
		if (movementDestination instanceof Vector2f point
				&& BattleNext_ShipAIHelper.getDistance(ship.getLocation(), point) > 500.0F
				&& !BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 90.0F, ship.getLocation(), point)) {
			wrongDirection = true;
		}
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (ship.isRetreating() && !(ai != null && ai.getCurrentManeuver() instanceof BattleNext_RetreatManeuver)) {
			Vector2f direction = BattleNext_RetreatManeuver.getRetreatDirection(ship.getLocation(), ship.getOwner(),
					BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner()).getGoal());
			if (!BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 90.0F,
						BattleNext_ShipAIHelper.getAngleFast(direction))) wrongDirection = true;
		}
		float maxSpeed = retreatMaxSpeed;
		if (ship.isEngineBoostActive()
				&& ship.getMutableStats().getZeroFluxMinimumFluxLevel().getModifiedValue() > 0.0F) {
			maxSpeed += ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
		}
		float travel = system.getSpecAPI().getIn() * maxSpeed * 0.5F
				+ system.getSpecAPI().getActive() * maxSpeed + ship.getCollisionRadius();
		float duration = Math.max(system.getSpecAPI().getIn() + system.getSpecAPI().getActive(), 3.0F);
		if (hasRetreatHints) {
			travel = maxSpeed;
			duration = 1.0F;
		}
		AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
		Vector2f assignmentPoint = assignment == null || assignment.getTarget() == null
				? null : assignment.getTarget().getLocation();
		boolean capture = assignment != null && assignment.getType() == CombatAssignmentType.CAPTURE;
		boolean avoid = assignment != null && assignment.getType() == CombatAssignmentType.AVOID;
		Vector2f ahead = BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing());
		ahead.scale(travel + duration * 50.0F);
		Vector2f.add(ahead, ship.getLocation(), ahead);
		boolean orderedAttack = BattleNext_ShipAIHelper.isShipOrderedToAttack(ship);
		boolean farFromOrder = assignmentPoint != null
				&& BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint) > travel + 750.0F;
		boolean facingOrder = assignmentPoint != null && !avoid
				&& BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 10.0F, ship.getLocation(), assignmentPoint);
		float forwardArmor = ship.getAverageArmorInSlice(ship.getFacing(), 90.0F);
		// approximation: the engine directional ArmorEvaluator is internal;
		// the public armor slice preserves the forward resistance comparison.
		float armorFraction = Math.max(0.0F,
				forwardArmor / ship.getArmorGrid().getArmorRating() - 0.5F) * 2.0F;
		boolean protectedFront = armorFraction > 0.5F;
		boolean criticalHull = ship.getHitpoints() < 150.0F * ship.getHullSize().ordinal();
		boolean collisionDanger = false;
		int closeEnemies = 0;
		int nearbyEnemies = 0;
		int hulks = 0;
		if (!ship.isFighter()) {
			collisionDanger = BattleNext_ShipAIHelper.computeDanger(ship, target,
					ship.getLocation(), ahead, maxSpeed, duration, travel) > 0.1F;
			HullSize minimum = BattleNext_ShipAIHelper.getSmallerHullSize(ship.getHullSize());
			if (!protectedFront) minimum = BattleNext_ShipAIHelper.getSmallerHullSize(minimum);
			if (criticalHull) minimum = BattleNext_ShipAIHelper.getSmallerHullSize(minimum);
			closeEnemies = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(ship, ahead, 30.0F, travel, minimum, null);
			nearbyEnemies = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(ship, ahead, 120.0F, travel + 2000.0F);
			// approximation: the native predicate counts hulks through an internal
			// filter; public combat entities supply an equivalent geometric count.
			for (ShipAPI other : BattleNext_ShipAIHelper.getEngine().getShips()) {
				if (!other.isHulk()) continue;
				if (BattleNext_ShipAIHelper.getDistance(ship.getLocation(), other.getLocation()) > travel) continue;
				if (BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 30.0F, ship.getLocation(), other.getLocation())) hulks++;
			}
		}
		float threatAhead = BattleNext_ShipAIHelper.getTotalThreatInRange(ship, ahead, ship.getFacing(),
				210.0F, 1250.0F, false, false);
		float threatAround = BattleNext_ShipAIHelper.getTotalThreatInRangeExcludingShip(ship, ahead, ship.getFacing(),
				360.0F, 1750.0F, target, false);
		float ownThreat = BattleNext_ShipAIHelper.getThreat(ship, true);
		float targetThreat = target == null ? 0.0F : BattleNext_ShipAIHelper.getThreat(target, true);
		boolean asteroidDanger = (criticalHull || !protectedFront)
				&& BattleNext_ShipAIHelper.getAsteroidThreat(ship, ship.getLocation(), ship.getFacing(), 30.0F, travel)
						>= ship.getMass() * ship.getHullLevel();
		boolean incomingMissiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissilesInArcRange(ship,
				ship.getFacing(), 60.0F, travel) > 0;
		boolean stable = stableTime > 3.0F && ship.getVelocity().length() >= ship.getMaxSpeed() * 0.95F;
		ShipAIConfig config = maneuverAPI.getConfig();
		boolean pathClear = !collisionDanger && (closeEnemies <= 0 || config.burnDriveIgnoreEnemies)
				&& hulks <= 0 && !asteroidDanger && !incomingMissiles;
		boolean favorable = threatAhead + ownThreat >= threatAround * 2.0F + targetThreat || orderedAttack;
		boolean strongFront = threatAhead >= threatAround * 2.0F || orderedAttack;
		if (assignmentPoint != null && !facingOrder) {
			if (flags.hasFlag(AIFlags.RUN_QUICKLY) && pathClear && favorable) useSystem();
			return;
		}
		CombatTaskManagerAPI tasks = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly());
		for (AssignmentInfo other : tasks.getAllAssignments()) {
			if (other.getType() != CombatAssignmentType.AVOID || other.getTarget() == null) continue;
			Vector2f point = other.getTarget().getLocation();
			if (BattleNext_ShipAIHelper.getDistance(ship.getLocation(), point) <= 4000.0F
					&& BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 10.0F, ship.getLocation(), point)) return;
		}
		if (farFromOrder && facingOrder && !wrongDirection && !engaged(ai) && stable && pathClear && favorable) {
			useSystem();
			return;
		}
		float facingTolerance = 5.0F;
		if (target != null) {
			BattleNext_WeaponFireControlHelper fireControl = new BattleNext_WeaponFireControlHelper();
			fireControl.updateTargeting(ship, target, ship.getCollisionRadius() + 100.0F);
			if (!fireControl.isOnTargetHeading()) facingTolerance = 30.0F;
		}
		boolean aligned = flockingAI.getAvoidanceRadius() == Float.MAX_VALUE && !ship.isRetreating()
				|| flockingAI.getAvoidanceRadius() != Float.MAX_VALUE
						&& BattleNext_ShipAIHelper.getAngleDiff(flockingAI.getAvoidanceRadius(), ship.getFacing()) < facingTolerance;
		if (wrongDirection) aligned = false;
		if (target == null && threatEval.getTotalThreat() <= 0.0F && aligned
				&& (assignmentPoint == null || facingOrder && farFromOrder)
				&& pathClear && favorable && stable && !engaged(ai)) {
			useSystem();
			return;
		}
		if (flags.hasFlag(AIFlags.RUN_QUICKLY) && pathClear && favorable
				|| ship.isRetreating() && stable && pathClear && favorable && aligned) {
			useSystem();
			return;
		}
		if (target == null || target.isHulk() || capture && !threatEval.isThreatHigh()) return;
		if (ai != null) {
			BattleNext_Maneuver maneuver = ai.getCurrentManeuver();
			if (maneuver instanceof BattleNext_TaskMoveManeuver || maneuver instanceof BattleNext_TaskAttackManeuver
					|| maneuver instanceof BattleNext_RetreatManeuver || maneuver instanceof BattleNext_EscortTargetManeuverV3) {
				float desired = maneuver.getDesiredFacing();
				if (desired != Float.MAX_VALUE && BattleNext_ShipAIHelper.getAngleDiff(ship.getFacing(), desired) > 20.0F) return;
			}
		}
		boolean targetNonCombat = target.isNonCombat(false);
		if (ship.isNonCombat(true) || !targetNonCombat && ship.getFluxLevel() > 0.75F
				|| criticalHull && !protectedFront || stableTime <= 0.0F) return;
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float radius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		float attackRange = attackAI.getAttackRange();
		boolean lowFlux = ship.getFluxLevel() <= 0.35F;
		boolean targetHighFlux = target.getFluxLevel() >= 0.75F;
		boolean fluxAdvantage = ship.getFluxLevel() < 0.65F
				&& ship.getFluxLevel() < target.getFluxLevel() + 0.2F || ship.getFluxLevel() <= 0.0F;
		boolean easyTarget = target.getHullSize().ordinal() < ship.getHullSize().ordinal() || targetNonCombat;
		boolean largerTarget = target.getHullSize().ordinal() > ship.getHullSize().ordinal() && !targetNonCombat;
		boolean equalTarget = target.getHullSize().ordinal() == ship.getHullSize().ordinal() && !targetNonCombat;
		Vector2f awayFromTarget = Vector2f.sub(ship.getLocation(), target.getLocation(), null);
		BattleNext_ShipAIHelper.normalise(awayFromTarget);
		boolean enemyFacingAway = Vector2f.dot(awayFromTarget,
				BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing())) < 0.0F;
		BattleNext_ThreatData targetData = threatEval.computeThreatData(target);
		boolean targetCanFight = BattleNext_ShipAIHelper.hasEnoughFirepower(target, ship);
		if (target.isCapital() && !target.isNonCombat(false)) targetCanFight = true;
		if (target.getFluxTracker().isOverloadedOrVenting()) {
			float recovery = target.getFluxTracker().isVenting()
					? target.getFluxTracker().getTimeToVent() : target.getFluxTracker().getOverloadTimeRemaining();
			if (recovery > duration) targetCanFight = false;
		}
		boolean station = target.isStation() || target.getParentStation() != null && target.getParentStation().isStation();
		if (station) targetCanFight = true;
		boolean longRangeThreat = targetData.getThreatRange() > attackRange * 1.1F
				|| target.getVariant().isCarrier();
		boolean far = distance - radius > attackRange * 1.2F;
		boolean fasterTarget = target.getMaxSpeed() > ship.getMaxSpeed() + 5.0F;
		boolean backingAway = BattleNext_ShipAIHelper.getClosingSpeed(ship, target) < -10.0F;
		boolean enoughDistance = distance > travel + radius;
		boolean veryFar = distance > travel + radius + attackRange * 0.5F + (station ? 1500.0F : 0.0F);
		Vector2f intercept = BattleNext_ShipAIHelper.getInterceptPoint(ship, ship.getAimAccuracy(), target, maxSpeed);
		boolean facingIntercept = intercept != null && BattleNext_ShipAIHelper.isInArc(ship.getFacing(),
					30.0F + facingTolerance, ship.getLocation(), intercept);
		float projectedDistance = intercept == null ? Float.MAX_VALUE
				: Math.abs(Vector2f.dot(Vector2f.sub(intercept, ship.getLocation(), null),
						BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing() + 90.0F)));
		boolean crossing = projectedDistance > radius * 1.3F
				&& projectedDistance < attackRange * 0.75F + radius
				&& BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 160.0F, ship.getLocation(), intercept);
		boolean saferPath = pathClear && !BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 20.0F,
				ship.getLocation(), target.getLocation());
		if (targetCanFight && !enemyFacingAway && !veryFar) return;
		if ((largerTarget || (float) Math.random() > 0.95F) && distance > travel * 0.25F
				&& distance <= travel * 0.7F && crossing && pathClear && strongFront) {
			useSystem();
			return;
		}
		if ((easyTarget || veryFar && lowFlux) && enoughDistance && facingIntercept && pathClear && favorable) {
			flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 5.0F);
			useSystem();
			return;
		}
		if (fluxAdvantage && (equalTarget && lowFlux && targetHighFlux || easyTarget)
				&& facingIntercept && saferPath && favorable) {
			useSystem();
			return;
		}
		if (far && longRangeThreat && (fasterTarget || backingAway) && !largerTarget
				&& facingIntercept && enoughDistance && saferPath && strongFront) {
			useSystem();
			return;
		}
		if (eval.hasFiringSolution(1000.0F, new BattleNext_FiringSolutionEval.SolutionResult())
				&& missileDangerDir != null && (ship.getShield() == null || ship.getShield().isOff()
						|| ship.getFluxLevel() > 0.95F || !BattleNext_ShipAIHelper.isInArc(
								ship.getShield().getFacing(), ship.getShield().getActiveArc(),
								BattleNext_ShipAIHelper.getAngleFast(missileDangerDir)))
				&& pathClear && favorable) useSystem();
	}

	private boolean engaged(BattleNext_BasicShipAI ai) {
		return ai != null && ai.isEngaged();
	}

	private void useSystem() {
		ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
	}
}
