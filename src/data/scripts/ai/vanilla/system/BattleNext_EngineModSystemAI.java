package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.maneuvers.BattleNext_AssignmentBasedManeuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3;
import data.scripts.ai.vanilla.maneuvers.BattleNext_Maneuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_RetreatManeuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_StrafeTargetManeuverV2;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskAttackManeuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponFireControlHelper;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.EngineModSystemAI.
 */
public class BattleNext_EngineModSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_AttackAIModule attackAI;
	private final BattleNext_FlockingAI flockingAI;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private final BattleNext_FiringSolutionEval eval;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final IntervalUtil activeInterval = new IntervalUtil(0.25F, 0.5F);
	private final float maxSpeed;
	private final boolean flameoutRisk;
	private float decisionDelay = 1.0F;
	private float turnTime;
	private boolean hasEnemyTarget;
	private float activeTime;
	private float angleToEnemy;
	private Vector2f enemyCenter;

	public BattleNext_EngineModSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval,
			BattleNext_AttackAIModule attackAI, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.attackAI = attackAI;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.eval = new BattleNext_FiringSolutionEval(ship);
		this.system = ship.getSystem();
		// approximation: ShipStats.create(spec) is internal. Public ship speed
		// plus the declared active speed hint estimates system cruise speed.
		JSONObject hints = system.getSpecAPI().getSpecJson().optJSONObject("aiHints");
		float speedIncrease = hints == null ? 0.0F : (float) hints.optDouble("activeSpeedIncrease", 0.0F);
		this.maxSpeed = ship.getMaxSpeed() + speedIncrease;
		this.flameoutRisk = system.getSpecAPI().getFlameoutOnImpactChance() > 0.0F;
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.isActive()) {
			advanceActive(amount, missileDangerDir, collisionDangerDir, target);
			return;
		}
		hasEnemyTarget = false;
		activeTime = 0.0F;
		angleToEnemy = 0.0F;
		if (system.getCooldownRemaining() > 0.0F || system.isOutOfAmmo() || ship.getEngineController().isDisabled()) return;
		if (ship.isFighter() && ship.getWing() != null && !ship.getWing().isLeader(ship)) {
			ShipAPI leader = ship.getWing().getLeader();
			if (leader != null && leader.getSystem() != null && leader.getSystem().isOn()) activateSystem();
			return;
		}
		float turnLimit = Math.max(5.0F, ship.getTurnAcceleration() * 0.5F);
		turnTime = Math.abs(ship.getAngularVelocity()) < turnLimit ? turnTime + amount : 0.0F;
		boolean decide = interval.intervalElapsed();
		if (decisionDelay > 1.0F) {
			if (turnTime > 0.0F) {
				decide = true;
				decisionDelay = 0.0F;
			}
		} else decisionDelay += amount;
		if (!decide) return;
		if (target != null && isEnemyDoomed(target)) return;
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
		float speed = maxSpeed;
		if (ship.isEngineBoostActive()
				&& ship.getMutableStats().getZeroFluxMinimumFluxLevel().getModifiedValue() > 0.0F) {
			speed += ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
		}
		float travel = system.getSpecAPI().getIn() * speed * 0.5F
				+ system.getSpecAPI().getActive() * speed + ship.getCollisionRadius();
		float nearDistance = speed * 3.0F;
		float duration = system.getSpecAPI().getIn() + system.getSpecAPI().getActive();
		AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
		Vector2f assignmentPoint = assignment == null || assignment.getTarget() == null
				? null : assignment.getTarget().getLocation();
		boolean capture = assignment != null && assignment.getType() == CombatAssignmentType.CAPTURE;
		boolean avoid = assignment != null && assignment.getType() == CombatAssignmentType.AVOID;
		Vector2f ahead = BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing());
		ahead.scale(nearDistance);
		Vector2f.add(ahead, ship.getLocation(), ahead);
		boolean farFromOrder = assignmentPoint != null
				&& BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint) > travel + 500.0F;
		boolean facingOrder = assignmentPoint != null && !avoid
				&& BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 10.0F, ship.getLocation(), assignmentPoint);
		float forwardArmor = ship.getAverageArmorInSlice(ship.getFacing(), 90.0F);
		// approximation: engine ArmorEvaluator.getArmorInArc is internal;
		// the public average armor slice preserves the same facing.
		float armorFraction = Math.max(0.0F,
				forwardArmor / ship.getArmorGrid().getArmorRating() - 0.5F) * 2.0F;
		boolean protectedFront = armorFraction > 0.5F;
		boolean criticalHull = ship.getHitpoints() < 150.0F * ship.getHullSize().ordinal();
		boolean collisionDanger = false;
		int closeEnemies = 0;
		int hulks = 0;
		if (!ship.isFighter()) {
			collisionDanger = BattleNext_ShipAIHelper.computeDanger(ship, target,
					ship.getLocation(), ahead, speed, 2.5F, nearDistance) > 0.1F;
			HullSize minimum = BattleNext_ShipAIHelper.getSmallerHullSize(ship.getHullSize());
			if (!protectedFront) minimum = BattleNext_ShipAIHelper.getSmallerHullSize(minimum);
			if (criticalHull) minimum = BattleNext_ShipAIHelper.getSmallerHullSize(minimum);
			if (!flameoutRisk && protectedFront && !criticalHull) {
				minimum = ship.getHullSize();
			}
			closeEnemies = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(ship, ahead, 30.0F,
					nearDistance, minimum, null);
			closeEnemies += BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(ship, ahead, 120.0F,
					nearDistance + ship.getCollisionRadius() + 500.0F);
			for (ShipAPI other : BattleNext_ShipAIHelper.getEngine().getShips()) {
				if (!other.isHulk()) continue;
				if (BattleNext_ShipAIHelper.getDistance(ship.getLocation(), other.getLocation()) > nearDistance) continue;
				if (BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 30.0F, ship.getLocation(), other.getLocation())) hulks++;
			}
		}
		boolean engaged = ai != null && ai.isEngaged();
		boolean asteroidDanger = (criticalHull || !protectedFront)
				&& BattleNext_ShipAIHelper.getAsteroidThreat(ship, ship.getLocation(), ship.getFacing(),
						30.0F, nearDistance + ship.getCollisionRadius()) >= ship.getMass() * ship.getHullLevel();
		boolean incomingMissiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissilesInArcRange(ship,
				ship.getFacing(), 210.0F, nearDistance + ship.getCollisionRadius()) > 0;
		boolean stable = turnTime > 3.0F && ship.getVelocity().length() >= ship.getMaxSpeed() * 0.95F;
		boolean moving = turnTime > 0.0F;
		if (system.getSpecAPI().isTurningAllowed()) {
			stable = ship.getVelocity().length() >= ship.getMaxSpeed() * 0.75F;
			moving = true;
		}
		ShipAIConfig config = maneuverAPI.getConfig();
		boolean pathClear = !collisionDanger && (closeEnemies <= 0 || config.burnDriveIgnoreEnemies)
				&& hulks <= 0 && !asteroidDanger && !incomingMissiles && !hasEnemyAhead();
		if (isEnemyDoomed(target)) pathClear = false;
		boolean orderedAttack = BattleNext_ShipAIHelper.isShipOrderedToAttack(ship);
		boolean aggressive = "aggressive".equals(BattleNext_ShipAIHelper.getPersonality(ship)) && !orderedAttack;
		boolean enemyCenterClear = !computeEnemyCenter(ahead, target) || orderedAttack;
		hasEnemyTarget = !enemyCenterClear;
		boolean awayFromCenter = isPointAwayFromEnemyCenter(ahead, nearDistance);
		boolean pastCenter = isPointPastEnemyCenter(ahead, nearDistance);
		if (awayFromCenter) enemyCenterClear = true;
		if (assignmentPoint != null && !facingOrder) {
			if (flags.hasFlag(AIFlags.RUN_QUICKLY) && pathClear && enemyCenterClear) activateSystem();
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
		if (farFromOrder && facingOrder && !wrongDirection && !engaged && stable && pathClear && enemyCenterClear) {
			activateSystem();
			return;
		}
		float facingTolerance = 5.0F;
		if (target != null) {
			BattleNext_WeaponFireControlHelper fireControl = new BattleNext_WeaponFireControlHelper();
			fireControl.updateTargeting(ship, target, ship.getCollisionRadius() + 100.0F);
			if (!fireControl.isOnTargetHeading()) facingTolerance = 30.0F;
			angleToEnemy = BattleNext_ShipAIHelper.getAngleDiff(ship.getFacing(),
					BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation()));
		}
		boolean aligned = flockingAI.getAvoidanceRadius() == Float.MAX_VALUE && !ship.isRetreating()
				|| flockingAI.getAvoidanceRadius() != Float.MAX_VALUE
						&& BattleNext_ShipAIHelper.getAngleDiff(flockingAI.getAvoidanceRadius(), ship.getFacing()) < facingTolerance;
		if (wrongDirection) aligned = false;
		if (target == null && threatEval.getTotalThreat() <= 0.0F && aligned
				&& (assignmentPoint == null || facingOrder && farFromOrder)
				&& pathClear && enemyCenterClear && stable && !engaged) {
			activateSystem();
			return;
		}
		if (flags.hasFlag(AIFlags.RUN_QUICKLY) && pathClear && enemyCenterClear
				|| ship.isRetreating() && stable && pathClear && enemyCenterClear && aligned) {
			activateSystem();
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
		if (!pastCenter && (ship.isNonCombat(true) || !targetNonCombat && ship.getFluxLevel() > 0.75F)) return;
		if (criticalHull && !protectedFront || !moving) return;
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
		Vector2f aheadPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing());
		aheadPoint.scale(nearDistance);
		Vector2f.add(ship.getLocation(), aheadPoint, aheadPoint);
		float lineDistance = Math.abs(Vector2f.dot(Vector2f.sub(target.getLocation(), ship.getLocation(), null),
				BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing() + 90.0F)));
		boolean crossing = lineDistance > radius * 1.3F && lineDistance < attackRange * 0.75F + radius
				&& BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 160.0F, ship.getLocation(), target.getLocation());
		Vector2f away = Vector2f.sub(ship.getLocation(), target.getLocation(), null);
		BattleNext_ShipAIHelper.normalise(away);
		float facingDot = Vector2f.dot(away, BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing()));
		boolean targetFacingAway = facingDot < -0.35F || (orderedAttack || aggressive) && facingDot < 0.3F;
		BattleNext_ThreatData targetThreat = threatEval.computeThreatData(target);
		boolean targetCanFight = BattleNext_ShipAIHelper.hasEnoughFirepower(target, ship);
		if (target.isCapital() && !target.isNonCombat(false)) targetCanFight = true;
		if (target.getFluxTracker().isOverloadedOrVenting()) {
			float remaining = target.getFluxTracker().isVenting()
					? target.getFluxTracker().getTimeToVent() : target.getFluxTracker().getOverloadTimeRemaining();
			if (remaining > duration) targetCanFight = false;
		}
		boolean longRangeThreat = targetThreat.getThreatRange() > attackRange * 1.1F || target.getVariant().isCarrier();
		boolean far = distance - radius > attackRange * 1.2F;
		boolean fasterTarget = target.getMaxSpeed() > ship.getMaxSpeed() + 5.0F;
		boolean backingAway = BattleNext_ShipAIHelper.getClosingSpeed(ship, target) < 0.0F;
		boolean enoughDistance = distance > nearDistance + radius;
		boolean veryFar = distance > travel + radius + attackRange * 0.5F;
		boolean facingTarget = BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 30.0F + facingTolerance,
				ship.getLocation(), target.getLocation());
		if (targetCanFight && !targetFacingAway && !veryFar) return;
		if (largerTarget && distance > travel * 0.25F && distance <= travel * 0.7F
				&& crossing && pathClear && enemyCenterClear) {
			activateSystem();
			return;
		}
		if ((easyTarget || veryFar && lowFlux) && enoughDistance && facingTarget && pathClear && enemyCenterClear) {
			flags.setFlag(AIFlags.DO_NOT_BACK_OFF, 5.0F);
			activateSystem();
			return;
		}
		if (easyTarget && (equalTarget && lowFlux && targetHighFlux || largerTarget)
				&& facingTarget && pathClear && enemyCenterClear && fluxAdvantage) {
			activateSystem();
			return;
		}
		if (far && longRangeThreat && (fasterTarget || backingAway)
				&& facingTarget && enoughDistance && pathClear && enemyCenterClear) {
			activateSystem();
			return;
		}
		if (eval.hasFiringSolution(1000.0F, new BattleNext_FiringSolutionEval.SolutionResult())
				&& missileDangerDir != null && (ship.getShield() == null || ship.getShield().isOff()
						|| ship.getFluxLevel() > 0.95F || !BattleNext_ShipAIHelper.isInArc(
								ship.getShield().getFacing(), ship.getShield().getActiveArc(),
								BattleNext_ShipAIHelper.getAngleFast(missileDangerDir)))
				&& pathClear && enemyCenterClear) activateSystem();
	}

	private void activateSystem() {
		if (!system.isActive()) ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
	}

	private void deactivateSystem() {
		if (system.isActive() && system.getState() != SystemState.OUT
				&& system.getState() != SystemState.IDLE && system.getState() != SystemState.COOLDOWN) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}

	public void advanceActive(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		activeTime += amount;
		activeInterval.advance(amount * 2.0F);
		if (!activeInterval.intervalElapsed() || activeTime <= 3.0F) return;
		if (target != null) {
			float angle = BattleNext_ShipAIHelper.getAngleDiff(ship.getFacing(),
					BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation()));
			if (!system.getSpecAPI().isTurningAllowed()
					&& BattleNext_ShipAIHelper.getAngleDiff(angle, angleToEnemy) > 45.0F) {
				deactivateSystem();
				return;
			}
			if (!flags.hasFlag(AIFlags.RUN_QUICKLY) && !flags.hasFlag(AIFlags.BACK_OFF)
					&& !ship.isRetreating() && system.getSpecAPI().isTurningAllowed()
					&& BattleNext_ShipAIHelper.getClosingSpeed(ship, target) < ship.getVelocity().length() * 0.2F
					&& ship.getVelocity().length() >= ship.getMaxSpeed() * 0.9F) {
				deactivateSystem();
				return;
			}
		} else if (activeTime > 10.0F) {
			deactivateSystem();
			return;
		}
		float speed = maxSpeed;
		if (ship.isEngineBoostActive()
				&& ship.getMutableStats().getZeroFluxMinimumFluxLevel().getModifiedValue() > 0.0F) {
			speed += ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
		}
		float range = speed * 2.0F;
		Vector2f ahead = BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing());
		ahead.scale(range);
		Vector2f.add(ship.getLocation(), ahead, ahead);
		boolean away = isPointAwayFromEnemyCenter(ahead, range);
		boolean past = isPointPastEnemyCenter(ahead, range);
		Vector2f sample = BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing());
		sample.scale(hasThreatAhead() ? -100.0F : ship.getCollisionRadius() + 300.0F);
		Vector2f.add(ship.getLocation(), sample, sample);
		boolean clear = !computeEnemyCenter(sample, target) || BattleNext_ShipAIHelper.isShipOrderedToAttack(ship);
		if (away || past) clear = true;
		if (!hasEnemyTarget && !clear) {
			deactivateSystem();
			return;
		}
		float armor = ship.getAverageArmorInSlice(ship.getFacing(), 90.0F);
		boolean protectedFront = Math.max(0.0F,
				armor / ship.getArmorGrid().getArmorRating() - 0.5F) * 2.0F > 0.5F;
		boolean criticalHull = ship.getHitpoints() < 150.0F * ship.getHullSize().ordinal();
		boolean danger = !ship.isFighter() && BattleNext_ShipAIHelper.computeDanger(ship, target,
				ship.getLocation(), ahead, speed, 1.5F, range) > 0.1F;
		HullSize minimum = BattleNext_ShipAIHelper.getSmallerHullSize(ship.getHullSize());
		if (!protectedFront) minimum = BattleNext_ShipAIHelper.getSmallerHullSize(minimum);
		if (criticalHull) minimum = BattleNext_ShipAIHelper.getSmallerHullSize(minimum);
		int enemies = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(ship, ahead, 30.0F,
				range * 0.5F, minimum, null);
		enemies += BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(ship, ahead, 120.0F,
				range + ship.getCollisionRadius() + 500.0F);
		boolean asteroid = (criticalHull || !protectedFront)
				&& BattleNext_ShipAIHelper.getAsteroidThreat(ship, ship.getLocation(), ship.getFacing(),
						30.0F, range * 0.5F + ship.getCollisionRadius()) >= ship.getMass() * ship.getHullLevel();
		boolean missiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissilesInArcRange(ship,
				ship.getFacing(), 210.0F, range + ship.getCollisionRadius()) > 0;
		boolean safe = !danger && (enemies <= 0 || maneuverAPI.getConfig().burnDriveIgnoreEnemies)
				&& !asteroid && !missiles && !hasEnemyAhead() && !isEnemyDoomed(target);
		if (!safe) deactivateSystem();
	}

	public boolean hasEnemyAhead() {
		if (ship.isFighter()) return false;
		for (ShipAPI other : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (other == ship || other.getOwner() != ship.getOwner()
					|| other.getHullSize().ordinal() < ship.getHullSize().ordinal()
					|| other.isStationModule() || other.isHulk() || other.isShuttlePod()
					|| other.isDrone() || other.isFighter()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(other.getLocation(), ship.getLocation())
					- other.getCollisionRadius() - ship.getCollisionRadius();
			if (distance > 1000.0F) continue;
			float ourAngle = BattleNext_ShipAIHelper.getAngleDiff(ship.getFacing(),
					BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), other.getLocation()));
			float theirAngle = BattleNext_ShipAIHelper.getAngleDiff(other.getFacing(),
					BattleNext_ShipAIHelper.getAngleFast(other.getLocation(), ship.getLocation()));
			if (ourAngle + theirAngle > 180.0F - distance / 1000.0F * 20.0F) continue;
			Vector2f sample = new Vector2f(ship.getLocation());
			Vector2f stride = BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing());
			stride.scale(maxSpeed);
			for (int step = 0; step < 3; step++) {
				float arc = 60.0F - 15.0F * step;
				if (BattleNext_ShipAIHelper.isInArc(other.getFacing(), arc, other.getLocation(), sample)) return true;
				Vector2f.add(sample, stride, sample);
			}
		}
		return false;
	}

	public boolean hasThreatAhead() {
		float threat = BattleNext_ShipAIHelper.getTotalThreatInRange(ship, ship.getLocation(),
				ship.getFacing(), 150.0F, 1000.0F, true, true);
		return threat >= BattleNext_ShipAIHelper.getThreat(ship, true) * 0.5F;
	}

	public boolean computeEnemyCenter(Vector2f point, ShipAPI target) {
		enemyCenter = null;
		List<ShipAPI> nearby = new ArrayList<>();
		Vector2f sum = new Vector2f();
		float totalWeight = 0.0F;
		for (ShipAPI other : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (other.getOwner() == ship.getOwner() || other.isStationModule()
					|| other.isHulk() || other.isShuttlePod() || other.isDrone()) continue;
			if (other.isFighter() && other.getWing() != null && !other.getWing().isLeader(other)) continue;
			if (!other.isTargetable() || other.getOwner() == 100) continue;
			if (other.getHullSize().ordinal() < ship.getHullSize().ordinal() - 2
					&& !other.isDestroyer() && other.getParentStation() == null) continue;
			if (BattleNext_ShipAIHelper.getDistance(point, other.getLocation()) >= 2200.0F) continue;
			float weight = other.getHullSize().ordinal() + 1.0F;
			nearby.add(other);
			sum.x += other.getLocation().x * weight;
			sum.y += other.getLocation().y * weight;
			totalWeight += weight;
		}
		if (target != null && !nearby.contains(target)) {
			float weight = target.getHullSize().ordinal() + 1.0F;
			nearby.add(target);
			sum.x += target.getLocation().x * weight;
			sum.y += target.getLocation().y * weight;
			totalWeight += weight;
		}
		if (totalWeight <= 0.0F) return false;
		Vector2f average = new Vector2f(sum);
		average.scale(1.0F / totalWeight);
		float otherWeight = totalWeight - (target == null || !nearby.contains(target)
				? 0.0F : target.getHullSize().ordinal() + 1.0F);
		if (otherWeight > 0.0F) {
			if (target != null) {
				sum.x -= target.getLocation().x * (target.getHullSize().ordinal() + 1.0F);
				sum.y -= target.getLocation().y * (target.getHullSize().ordinal() + 1.0F);
			}
			sum.scale(1.0F / otherWeight);
			enemyCenter = sum;
		}
		ShipAPI focus = target == null ? nearby.get(0) : target;
		float spread = 0.0F;
		for (ShipAPI other : nearby) {
			if (other == focus) continue;
			float fromCenter = BattleNext_ShipAIHelper.getAngleFast(average, other.getLocation());
			float towardShip = BattleNext_ShipAIHelper.getAngleFast(other.getLocation(), ship.getLocation());
			spread = Math.max(spread, BattleNext_ShipAIHelper.getAngleDiff(fromCenter, towardShip));
		}
		return spread > 60.0F && !isEngagedWithTarget();
	}

	public boolean isEnemyDoomed(ShipAPI target) {
		if (target == null || target.getHullLevel() >= 0.33F) return false;
		float fraction = 1.0F - target.getHullLevel() / 0.33F;
		float margin = ship.getShield() == null || ship.getFluxLevel() > 0.95F || ship.getShield().isOff()
				? 400.0F : 200.0F;
		if (system.getCooldown() <= 1.0F) margin += 100.0F * (ship.getHullSize().ordinal() - 2);
		float radius = ship.getCollisionRadius() + DamagingExplosionSpec.getShipExplosionRadius(target) + margin * fraction;
		return BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation()) < radius
				&& BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 60.0F, ship.getLocation(), target.getLocation());
	}

	public boolean isPointAwayFromEnemyCenter(Vector2f point, float range) {
		return enemyCenter != null && BattleNext_ShipAIHelper.getDistance(enemyCenter, ship.getLocation())
				< BattleNext_ShipAIHelper.getDistance(enemyCenter, point) + range * 0.1F;
	}

	public boolean isPointPastEnemyCenter(Vector2f point, float range) {
		return enemyCenter != null && BattleNext_ShipAIHelper.getDistance(enemyCenter, ship.getLocation())
				< BattleNext_ShipAIHelper.getDistance(enemyCenter, point) - range * 0.6F;
	}

	public boolean isEngagedWithTarget() {
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (ai == null || flags.hasFlag(AIFlags.BACKING_OFF) || flags.hasFlag(AIFlags.BACK_OFF)) return false;
		BattleNext_Maneuver maneuver = ai.getCurrentManeuver();
		if (!(maneuver instanceof BattleNext_StrafeTargetManeuverV2)
				&& !(maneuver instanceof BattleNext_AssignmentBasedManeuver)) return false;
		if (!(maneuver.getTarget() instanceof ShipAPI target)) return false;
		Object rangeFlag = flags.getCustom(AIFlags.MANEUVER_RANGE_FROM_TARGET);
		float desiredRange = rangeFlag instanceof Float ? (Float) rangeFlag : 100000.0F;
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float closing = BattleNext_ShipAIHelper.getClosingSpeed(ship, target);
		return distance > desiredRange + (closing >= 10.0F ? ship.getCollisionRadius() + 100.0F : 100.0F);
	}
}
