package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatAsteroidAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import data.scripts.ai.vanilla.system.drones.BattleNext_DroneAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.DisplacerSystemAI.
 */
public class BattleNext_DisplacerSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_AttackAIModule attackAI;
	private final BattleNext_FlockingAI flockingAI;
	private final BattleNext_FiringSolutionEval eval;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private float lastHullLevel = 1.0F;

	public BattleNext_DisplacerSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval,
			BattleNext_AttackAIModule attackAI, BattleNext_FlockingAI flockingAI) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.attackAI = attackAI;
		this.flockingAI = flockingAI;
		this.eval = new BattleNext_FiringSolutionEval(ship);
		this.system = ship.getSystem();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.isActive() || system.getCooldownRemaining() > 0.0F || system.isOutOfAmmo()) return;
		float flux = ship.getFluxLevel();
		if (flux < 0.99F && threatEval.isThreatHigh()) flags.setFlag(AIFlags.DO_NOT_VENT);
		if (!interval.intervalElapsed()) return;
		float hull = ship.getHullLevel();
		boolean takingDamage = interval.getIntervalDuration() > 0.0F
				&& (lastHullLevel - hull) / interval.getIntervalDuration() > 0.01F;
		lastHullLevel = hull;
		AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
		Vector2f assignmentPoint = assignment == null || assignment.getTarget() == null
				? null : assignment.getTarget().getLocation();
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (assignmentPoint == null && ai instanceof BattleNext_DroneAI drone) assignmentPoint = drone.getDesiredLocation();
		float assignmentDistance = assignmentPoint == null ? 0.0F
				: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint);
		float heading = flockingAI.getAvoidanceRadius();
		float movementHeading = ship.getVelocity().length() > 5.0F
				? BattleNext_ShipAIHelper.getAngleFast(ship.getVelocity()) : ship.getFacing();
		boolean aligned20 = heading != Float.MAX_VALUE
				&& BattleNext_ShipAIHelper.getAngleDiff(heading, movementHeading) < 20.0F;
		boolean aligned60 = heading != Float.MAX_VALUE
				&& BattleNext_ShipAIHelper.getAngleDiff(heading, movementHeading) < 60.0F;
		boolean engaged = ai != null && ai.isEngaged();
		float range = system.getSpecAPI().getRange(ship.getMutableStats());
		boolean precise = range > system.getSpecAPI().getRandomRange() + (ship.isDrone() ? 0.0F : 200.0F);
		float travelRange = range + (ship.isDrone() ? 0.0F : 500.0F);
		boolean safe = threatEval.getTotalThreat() <= 0.0F || threatEval.isAllThreatsValid();
		boolean travel = !engaged && assignmentDistance > travelRange && safe;
		travel |= system.getAmmo() == system.getSpecAPI().getMaxUses(ship.getMutableStats())
				&& aligned20 && target == null && !engaged && safe;
		Vector2f destination = computeDisplacerDestination();
		if (!isPointSafe(destination, null)) return;
		if (travel && (assignmentPoint == null || assignmentDistance > travelRange) && precise && aligned20) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		if (ship.isRetreating() && heading != Float.MAX_VALUE && precise && aligned20) {
			ship.setShipTarget(target);
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		boolean nearbyDamage = eval.hasFiringSolution(ship.getCollisionRadius() + 200.0F,
				new BattleNext_FiringSolutionEval.SolutionResult());
		boolean shieldCovered = ship.getShield() == null || !ship.getShield().isOn() || missileDangerDir == null
				|| BattleNext_ShipAIHelper.isInArc(ship.getShield().getFacing(), ship.getShield().getActiveArc(),
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
		if (threatEval.isThreatHigh()
				|| collisionDangerDir != null && !(flockingAI.getNearestThreat() instanceof CombatAsteroidAPI)
				|| (threatEval.getTotalThreat() > 0.0F || collisionDangerDir != null || missileDangerDir != null) && takingDamage
				|| nearbyDamage && !shieldCovered || threatEval.isThreatLow() && flux > 0.8F) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		if (target == null || target.isHulk() || ship.isNonCombat(true) || ship.isRetreating()
				|| ship.getEngineController().isDisabled()) return;
		boolean targetRecovering = target.getFluxTracker().isOverloadedOrVenting()
				&& (target.getFluxTracker().getOverloadTimeRemaining() > 5.0F
						|| target.getFluxTracker().getTimeToVent() > 5.0F);
		if (ship.getFluxTracker().isOverloadedOrVenting() || flux > 0.5F) return;
		float attackRange = attackAI.getAttackRange();
		BattleNext_ThreatData targetThreat = threatEval.computeThreatData(target);
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float radius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		boolean far = distance > attackRange + radius;
		boolean halfFar = distance > attackRange * 0.5F + radius;
		boolean veryFar = distance > attackRange + radius + 300.0F;
		boolean closing = BattleNext_ShipAIHelper.getPredictedDistance(ship, target.getLocation(), target.getVelocity()) < 0.0F;
		boolean easyTarget = target.isNonCombat(false);
		boolean smallerThreat = ship.getHullSize().ordinal() < target.getHullSize().ordinal()
				&& !easyTarget && !targetRecovering;
		float towardAngle = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation());
		boolean movingToward = BattleNext_ShipAIHelper.getAngleDiff(towardAngle, movementHeading) < 60.0F;
		boolean movingAway = BattleNext_ShipAIHelper.getAngleDiff(towardAngle, movementHeading) > 130.0F;
		if (movingAway && smallerThreat && flux - 0.3F > target.getFluxLevel() && flux > 0.8F) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		if (aligned20 && movingToward && (veryFar || far && closing && !smallerThreat)
				&& (easyTarget || targetRecovering || flags.hasFlag(AIFlags.PURSUING))) {
			ship.setShipTarget(target);
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		if (aligned60 && movingToward && (ship.getHullSize().ordinal() > target.getHullSize().ordinal() || easyTarget)
				&& halfFar) {
			ship.setShipTarget(target);
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}

	private Vector2f computeDisplacerDestination() {
		// approximation: TeleporterSystem.getDisplacerDestination() is internal;
		// its source computes the destination from velocity or facing and spec range.
		float heading = ship.getVelocity().length() > 5.0F
				? BattleNext_ShipAIHelper.getAngleFast(ship.getVelocity()) : ship.getFacing();
		Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(heading);
		offset.scale(system.getSpecAPI().getRange(ship.getMutableStats()));
		return Vector2f.add(ship.getLocation(), offset, null);
	}

	private boolean isPointSafe(Vector2f point, ShipAPI excluded) {
		float halfWidth = BattleNext_ShipAIHelper.getEngine().getMapWidth() * 0.5F;
		float halfHeight = BattleNext_ShipAIHelper.getEngine().getMapHeight() * 0.5F;
		// approximation: the engine CombatMap bounds are internal; public map
		// dimensions supply the centered rectangle.
		if (Math.abs(point.x) > halfWidth || Math.abs(point.y) > halfHeight) return false;
		float dangerRadius = ship.getCollisionRadius()
				+ Math.max(300.0F, system.getSpecAPI().getRandomRange() + 100.0F);
		for (ShipAPI other : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (other == excluded || other == ship || other.isShuttlePod() || other.isFighter()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(point, other.getLocation());
			if (distance < other.getCollisionRadius() + ship.getCollisionRadius() + 50.0F) return false;
			if (!other.isHulk() && other.getOwner() == ship.getOriginalOwner()
					&& BattleNext_ShipAIHelper.isInArc(other.getFacing(), 30.0F, other.getLocation(), point)
					&& distance < other.getCollisionRadius() + ship.getCollisionRadius() + 500.0F) return false;
		}
		for (MissileAPI missile : BattleNext_ShipAIHelper.getEngine().getMissiles()) {
			float distance = BattleNext_ShipAIHelper.getDistance(point, missile.getLocation());
			if (distance > missile.getCollisionRadius() + dangerRadius) continue;
			if (missile.getCollisionClass() == CollisionClass.MISSILE_NO_FF
					&& missile.getOwner() == ship.getOriginalOwner() && !missile.isFizzling()) continue;
			if (BattleNext_ShipAIHelper.getClosingSpeed(point, missile.getLocation(),
					ship.getVelocity(), missile.getVelocity()) > 0.0F) return false;
		}
		return true;
	}
}
