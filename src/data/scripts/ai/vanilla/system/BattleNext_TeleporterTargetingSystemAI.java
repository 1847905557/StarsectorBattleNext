package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.CombatAsteroidAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.TeleporterTargetingSystemAI.
 */
public class BattleNext_TeleporterTargetingSystemAI extends BattleNext_TeleporterTaskSystemAI {
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
	private boolean teleporting;

	public BattleNext_TeleporterTargetingSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval,
			BattleNext_AttackAIModule attackAI, BattleNext_FlockingAI flockingAI) {
		super(ship, flags, threatEval, attackAI, flockingAI);
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
		if (system.isActive() || system.getCooldownRemaining() > 0.0F || system.isOutOfAmmo()
				|| !system.canBeActivated() || !interval.intervalElapsed()) return;
		lastHullLevel = ship.getHullLevel();
		teleporting = false;
		if (shouldTeleport(target)) return;
		if (isTeleportDestGood(target, missileDangerDir, collisionDangerDir)) return;
		if (teleporting) return;
		isTeleportAllowed(target, missileDangerDir, collisionDangerDir);
	}

	public boolean isTeleportDestGood(ShipAPI target, Vector2f missileDangerDir, Vector2f collisionDangerDir) {
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		boolean nearbyDamage = eval.hasFiringSolution(ship.getCollisionRadius() + 200.0F,
				new BattleNext_FiringSolutionEval.SolutionResult());
		boolean shieldCovers = ship.getShield() == null || !ship.getShield().isOn() || missileDangerDir == null
				|| BattleNext_ShipAIHelper.isInArc(ship.getShield().getFacing(), ship.getShield().getActiveArc(),
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
		float flux = ship.getFluxLevel();
		// intentionally kept dead: advance records the same hull value before
		// this check, so the per-decision damage test stays false in vanilla.
		boolean takingDamage = interval.getIntervalDuration() > 0.0F
				&& (lastHullLevel - ship.getHullLevel()) / interval.getIntervalDuration() > 0.01F;
		boolean backingOffFlux = 1.0F - flux < Math.min(1.0F,
				BattleNext_ShipAIHelper.getStatisticsTracker(ship).fitHardFluxLevel(2.0F).slope * 4.0F)
				&& flags.hasFlag(AIFlags.BACK_OFF);
		boolean danger = threatEval.isThreatHigh()
				|| collisionDangerDir != null && !(flockingAI.getNearestThreat() instanceof CombatAsteroidAPI)
				|| (threatEval.getTotalThreat() > 0.0F || collisionDangerDir != null || missileDangerDir != null)
						&& (takingDamage || backingOffFlux)
				|| nearbyDamage && !shieldCovers
				|| threatEval.isThreatLow() && flux > 1.0F - fluxFraction - 0.1F;
		if (!danger) return false;
		Vector2f point = computeTeleportDest(target);
		if (point == null) return false;
		ship.giveCommand(ShipCommand.USE_SYSTEM, new Vector2f(point), -1);
		return true;
	}

	public boolean shouldTeleport(ShipAPI target) {
		AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
		Vector2f assignmentPoint = assignment == null || assignment.getTarget() == null
				? null : assignment.getTarget().getLocation();
		float distance = assignmentPoint == null ? 0.0F
				: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint);
		float range = system.getSpecAPI().getRange(ship.getMutableStats());
		if (assignmentPoint != null) {
			Vector2f heading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(
					BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), assignmentPoint));
			heading.scale(range);
			assignmentPoint = Vector2f.add(ship.getLocation(), heading, null);
		}
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		boolean engaged = ai != null && ai.isEngaged();
		boolean precise = range > system.getSpecAPI().getRandomRange() + 200.0F;
		teleporting = precise && distance > 2000.0F;
		if (engaged && distance < 5000.0F) teleporting = false;
		boolean moveAligned = false;
		if (ai != null && assignmentPoint != null) {
			if (!(ai.getCurrentManeuver() instanceof BattleNext_TaskMoveManeuver)) {
				teleporting = false;
			} else {
				float angle = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), assignmentPoint);
				moveAligned = BattleNext_ShipAIHelper.getAngleDiff(ai.getCurrentManeuver().getDesiredFacing(), angle) < 10.0F;
			}
		}
		if (moveAligned) engaged = false;
		boolean safe = threatEval.getTotalThreat() <= 0.0F || threatEval.isAllThreatsValid();
		boolean travel = !engaged && distance > 1000.0F && safe;
		travel |= target == null && !engaged && safe;
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		boolean spareFlux = fluxFraction <= 0.0F
				|| ship.getFluxLevel() < Math.max(0.1F, 1.0F - fluxFraction - 0.5F);
		if (assignmentPoint != null && !isTeleportDestValid(assignmentPoint, null, null)) assignmentPoint = null;
		if (travel && assignmentPoint != null && distance > 1000.0F && precise && spareFlux) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, new Vector2f(assignmentPoint), -1);
			return true;
		}
		if (ship.isRetreating() && flockingAI.getAvoidanceRadius() != Float.MAX_VALUE && precise) {
			Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(flockingAI.getAvoidanceRadius());
			offset.scale(range);
			Vector2f point = Vector2f.add(ship.getLocation(), offset, null);
			ship.setShipTarget(target);
			ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
			return true;
		}
		return false;
	}

	public boolean isTeleportAllowed(ShipAPI target, Vector2f missileDangerDir, Vector2f collisionDangerDir) {
		if (ship.isRetreating() || target == null || elapsed < 3.0F
				|| target.isHulk() || ship.isNonCombat(true) || ship.getEngineController().isDisabled()) return false;
		if (ship.getFluxTracker().isOverloadedOrVenting() || ship.getFluxLevel() > 0.5F) return false;
		boolean targetRecovering = target.getFluxTracker().isOverloadedOrVenting()
				&& (target.getFluxTracker().getOverloadTimeRemaining() > 5.0F
						|| target.getFluxTracker().getTimeToVent() > 5.0F);
		float attackRange = attackAI.getAttackRange();
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float radius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		boolean far = distance > attackRange + radius;
		boolean veryFar = distance > attackRange + radius + 300.0F;
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
				return true;
			}
		}
		boolean fluxAdvantage = BattleNext_ShipAIHelper.getFluxAvailable(ship)
				> BattleNext_ShipAIHelper.getFluxAvailable(target) * 1.5F;
		if (fluxAdvantage || ship.getFluxLevel() < 0.1F) {
			Vector2f point = getTeleportDestAroundTarget(target);
			if (point != null) {
				point = Misc.getPointAtRadius(point, 50.0F);
				float fromAngle = BattleNext_ShipAIHelper.getAngleFast(target.getLocation(), ship.getLocation());
				float toAngle = BattleNext_ShipAIHelper.getAngleFast(target.getLocation(), point);
				float nearby = BattleNext_ShipAIHelper.getDistance(point, ship.getLocation());
				if (BattleNext_ShipAIHelper.getAngleDiff(fromAngle, toAngle) < 30.0F
						&& nearby < ship.getCollisionRadius() + 100.0F) return false;
				ship.setShipTarget(target);
				ship.giveCommand(ShipCommand.USE_SYSTEM, point, -1);
				elapsed = 0.0F;
				return true;
			}
		}
		return false;
	}

	public Vector2f getTeleportDestAroundTarget(ShipAPI target) {
		float radius = attackAI.getAttackRange() * 0.75F
				+ (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.5F;
		float bestScore = 0.0F;
		Vector2f best = null;
		for (float offset = 0.0F; offset < 360.0F; offset += 30.0F) {
			Vector2f direction = BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing() + offset);
			direction.scale(radius);
			Vector2f point = Vector2f.add(target.getLocation(), direction, null);
			if (BattleNext_ShipAIHelper.getDistance(point, ship.getLocation())
					> system.getSpecAPI().getRange(ship.getMutableStats())) continue;
			if (!isTeleportDestValid(point, target, target)) continue;
			float score = scoreTeleportDest(point, target);
			if (score > bestScore) {
				best = point;
				bestScore = score;
			}
		}
		return best;
	}

	public float scoreTeleportDest(Vector2f point, ShipAPI target) {
		if (target == null || target.isDrone() || target.isFighter()) return -1.0F;
		List<ShipAPI> nearby = new ArrayList<>();
		Vector2f centroid = new Vector2f();
		float totalWeight = 0.0F;
		for (ShipAPI enemy : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (enemy.getOwner() == ship.getOwner() || enemy.isStationModule() || enemy.isHulk()
					|| enemy.isShuttlePod() || enemy.isDrone() || !enemy.isTargetable()) continue;
			if (enemy.isFighter() && enemy.getWing() != null && !enemy.getWing().isLeader(enemy)) continue;
			if (BattleNext_ShipAIHelper.getDistance(point, enemy.getLocation()) >= 2200.0F) continue;
			float weight = enemy.getHullSize().ordinal() + 1.0F;
			nearby.add(enemy);
			centroid.x += enemy.getLocation().x * weight;
			centroid.y += enemy.getLocation().y * weight;
			totalWeight += weight;
		}
		if (!nearby.contains(target)) {
			float weight = target.getHullSize().ordinal() + 1.0F;
			nearby.add(target);
			centroid.x += target.getLocation().x * weight;
			centroid.y += target.getLocation().y * weight;
			totalWeight += weight;
		}
		if (totalWeight <= 0.0F) return -1.0F;
		centroid.scale(1.0F / totalWeight);
		float maximumSpread = 0.0F;
		for (ShipAPI enemy : nearby) {
			if (enemy == target) continue;
			float centroidAngle = BattleNext_ShipAIHelper.getAngleFast(centroid, enemy.getLocation());
			float shipAngle = BattleNext_ShipAIHelper.getAngleFast(enemy.getLocation(), ship.getLocation());
			maximumSpread = Math.max(maximumSpread, BattleNext_ShipAIHelper.getAngleDiff(centroidAngle, shipAngle));
		}
		if (maximumSpread > 60.0F) return -1.0F;
		float pointAngle = BattleNext_ShipAIHelper.getAngleFast(target.getLocation(), point);
		return BattleNext_ShipAIHelper.getAngleDiff(target.getFacing(), pointAngle);
	}
}
