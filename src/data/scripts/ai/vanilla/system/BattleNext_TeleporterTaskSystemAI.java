package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.*;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.TeleporterTaskSystemAI.
 */
public class BattleNext_TeleporterTaskSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_AttackAIModule attackAI;
	private final BattleNext_FlockingAI flockingAI;
	private final BattleNext_FiringSolutionEval eval;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private float lastHullLevel = 1.0F;
	private float elapsed = 5.0F;

	public BattleNext_TeleporterTaskSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval,
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
		elapsed += amount;
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
		float assignmentDistance = assignmentPoint == null ? 0.0F
				: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint);
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		boolean engaged = ai != null && ai.isEngaged();
		float range = system.getSpecAPI().getRange(ship.getMutableStats());
		boolean precise = range > system.getSpecAPI().getRandomRange() + 200.0F;
		boolean safe = threatEval.getTotalThreat() <= 0.0F || threatEval.isAllThreatsValid();
		boolean travel = !engaged && assignmentDistance > 1000.0F && safe;
		travel |= target == null && !engaged && safe;
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		boolean spareFlux = flux < Math.max(0.1F, 1.0F - fluxFraction - 0.5F);
		if (assignmentPoint != null && !isTeleportDestValid(assignmentPoint, null, null)) assignmentPoint = null;
		if (travel && assignmentPoint != null && assignmentDistance > 1000.0F && precise && spareFlux) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, new Vector2f(assignmentPoint), -1);
			return;
		}
		if (ship.isRetreating() && flockingAI.getAvoidanceRadius() != Float.MAX_VALUE && precise) {
			Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(flockingAI.getAvoidanceRadius());
			offset.scale(range);
			Vector2f point = Vector2f.add(ship.getLocation(), offset, null);
			ship.setShipTarget(target);
			ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
			return;
		}
		boolean nearbyDamage = eval.hasFiringSolution(ship.getCollisionRadius() + 200.0F,
				new BattleNext_FiringSolutionEval.SolutionResult());
		boolean covered = ship.getShield() == null || !ship.getShield().isOn() || missileDangerDir == null
				|| BattleNext_ShipAIHelper.isInArc(ship.getShield().getFacing(), ship.getShield().getActiveArc(),
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
		boolean danger = threatEval.isThreatHigh()
				|| collisionDangerDir != null && !(flockingAI.getNearestThreat() instanceof CombatAsteroidAPI)
				|| (threatEval.getTotalThreat() > 0.0F || collisionDangerDir != null || missileDangerDir != null) && takingDamage
				|| nearbyDamage && !covered
				|| threatEval.isThreatLow() && flux > 1.0F - fluxFraction - 0.1F;
		if (danger) {
			Vector2f escape = computeTeleportDest(target);
			if (escape != null) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, escape, -1);
				return;
			}
		}
		if (elapsed < 5.0F || target == null || target.isHulk() || ship.isNonCombat(true)) return;
		if (ship.getEngineController().isDisabled() || ship.getFluxLevel() > 0.5F
				|| ship.getFluxTracker().isOverloadedOrVenting()) return;
		boolean targetRecovering = target.getFluxTracker().isOverloadedOrVenting()
				&& (target.getFluxTracker().getOverloadTimeRemaining() > 5.0F
						|| target.getFluxTracker().getTimeToVent() > 5.0F);
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float combinedRadius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		float attackRange = attackAI.getAttackRange();
		BattleNext_ThreatData targetThreat = threatEval.computeThreatData(target);
		boolean far = distance > attackRange + combinedRadius;
		boolean veryFar = distance > attackRange + combinedRadius + 300.0F;
		boolean closing = BattleNext_ShipAIHelper.getPredictedDistance(ship, target.getLocation(), target.getVelocity()) < 0.0F;
		boolean easyTarget = target.isNonCombat(false);
		if ((veryFar || far && closing) && (easyTarget || targetRecovering || flags.hasFlag(AIFlags.PURSUING))) {
			Vector2f point = Vector2f.add(ship.getLocation(), target.getLocation(), null);
			point.scale(0.5F);
			if (target.isRetreating()) {
				Vector2f toward = Vector2f.sub(target.getLocation(), ship.getLocation(), null);
				BattleNext_ShipAIHelper.normalise(toward);
				toward.scale(Math.max(1.0F, distance - ship.getCollisionRadius() + target.getCollisionRadius() - 150.0F));
				Vector2f.add(ship.getLocation(), toward, point);
			}
			if (isTeleportDestValid(point, target, target)) {
				ship.setShipTarget(target);
				ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
				elapsed = 0.0F;
				return;
			}
		}
		Vector2f towardShip = Vector2f.sub(ship.getLocation(), target.getLocation(), null);
		BattleNext_ShipAIHelper.normalise(towardShip);
		float facingDot = Vector2f.dot(towardShip, BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing()));
		if (distance < range * 0.5F && facingDot > 0.71F) {
			Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(
					BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation()));
			offset.scale(distance + combinedRadius + Math.min(400.0F, 300.0F + system.getSpecAPI().getRandomRange()));
			Vector2f point = Vector2f.add(ship.getLocation(), offset, null);
			if (isTeleportDestValid(point, target, target)) {
				ship.setShipTarget(target);
				ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
				elapsed = 0.0F;
				return;
			}
		}
		if (facingDot >= -0.25F || veryFar) {
			Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing() + 180.0F);
			offset.scale(combinedRadius + Math.max(200.0F + system.getSpecAPI().getRandomRange(), attackRange * 0.8F));
			Vector2f point = Vector2f.add(target.getLocation(), offset, null);
			if (isTeleportDestValid(point, target, target)) {
				ship.setShipTarget(target);
				ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
				elapsed = 0.0F;
				return;
			}
		}
		if (!targetRecovering && BattleNext_ShipAIHelper.getFluxAvailable(ship)
					< BattleNext_ShipAIHelper.getFluxAvailable(target) && flux < 0.5F
					&& target.getShield() != null && target.getShield().isOn()
					&& target.getShield().getType() == ShieldType.OMNI
					&& target.getShield().getActiveArc() < 270.0F) {
			Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getShield().getFacing() + 180.0F);
			offset.scale(combinedRadius + Math.max(200.0F + system.getSpecAPI().getRandomRange(), attackRange * 0.8F));
			Vector2f point = Vector2f.add(target.getLocation(), offset, null);
			if (isTeleportDestValid(point, target, target)) {
				ship.setShipTarget(target);
				ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
				elapsed = 0.0F;
			}
		}
	}

	protected Vector2f computeTeleportDest(ShipAPI target) {
		float distance = system.getSpecAPI().getRandomRange() + system.getSpecAPI().getRange(ship.getMutableStats());
		if (target != null) {
			distance = system.getSpecAPI().getRandomRange()
					+ threatEval.computeThreatData(target).getThreatRange() + 300.0F + target.getCollisionRadius();
		}
		float baseAngle = (float) Math.random() * 360.0F;
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (ai != null && ai.getCurrentManeuver() != null
				&& ai.getCurrentManeuver().getDesiredFacing() != Float.MAX_VALUE) {
			baseAngle = ai.getCurrentManeuver().getDesiredFacing();
		}
		if (flags.hasFlag(AIFlags.BACKING_OFF)) baseAngle = ship.getFacing() + 180.0F;
		for (int step = 0; step <= 6; step++) {
			float offset = step == 0 ? 0.0F : step == 6 ? 180.0F : (step + 1) / 2 * 30.0F * (step % 2 == 1 ? 1.0F : -1.0F);
			Vector2f direction = BattleNext_ShipAIHelper.getUnitVectorAtAngle(baseAngle + offset);
			direction.scale(ship.getCollisionRadius() + distance);
			Vector2f point = Vector2f.add(ship.getLocation(), direction, null);
			if (isTeleportDestValid(point, null, null)) return point;
		}
		return null;
	}

	protected boolean isTeleportDestValid(Vector2f point, ShipAPI target, ShipAPI excluded) {
		float halfWidth = BattleNext_ShipAIHelper.getEngine().getMapWidth() * 0.5F;
		float halfHeight = BattleNext_ShipAIHelper.getEngine().getMapHeight() * 0.5F;
		// approximation: the engine CombatMap has a private irregular bounds
		// check; public map dimensions provide the centered rectangle.
		if (Math.abs(point.x) > halfWidth || Math.abs(point.y) > halfHeight) return false;
		float dangerRadius = ship.getCollisionRadius()
				+ Math.max(750.0F, system.getSpecAPI().getRandomRange() + 250.0F);
		for (ShipAPI other : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (other == excluded || other == ship || other.isShuttlePod() || other.isFighter()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(point, other.getLocation());
			if (distance < other.getCollisionRadius() + ship.getCollisionRadius()) return false;
			if (!other.isHulk() && other.getOwner() == ship.getOriginalOwner()
					&& BattleNext_ShipAIHelper.isInArc(other.getFacing(), 30.0F, other.getLocation(), point)
					&& distance < other.getCollisionRadius() + ship.getCollisionRadius() + 500.0F) return false;
			if (other.getOwner() != ship.getOriginalOwner() && other.getOwner() != 100
					&& distance < other.getCollisionRadius() + dangerRadius) return false;
		}
		for (MissileAPI missile : BattleNext_ShipAIHelper.getEngine().getMissiles()) {
			float distance = BattleNext_ShipAIHelper.getDistance(point, missile.getLocation());
			if (distance > missile.getCollisionRadius() + dangerRadius) continue;
			if (missile.getCollisionClass() == CollisionClass.MISSILE_NO_FF
					&& missile.getOwner() == ship.getOriginalOwner() && !missile.isFizzling()) continue;
			float closing = BattleNext_ShipAIHelper.getClosingSpeed(point, missile.getLocation(),
					ship.getVelocity(), missile.getVelocity());
			if (closing > 0.0F) return false;
		}
		return true;
	}
}
