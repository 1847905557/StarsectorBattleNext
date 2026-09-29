package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.EngagementSystemAI.
 */
public class BattleNext_EngagementSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_AttackAIModule attackAI;
	private final BattleNext_FlockingAI flockingAI;
	private final ShipSystemAPI system;
	private final BattleNext_FiringSolutionEval eval;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);

	public BattleNext_EngagementSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval,
			BattleNext_AttackAIModule attackAI, BattleNext_FlockingAI flockingAI) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.attackAI = attackAI;
		this.flockingAI = flockingAI;
		this.system = ship.getSystem();
		this.eval = new BattleNext_FiringSolutionEval(ship, flags);
		this.eval.setUseBurstDps(true);
		this.eval.setAssumeFiring(true);
		this.eval.setAlwaysEvaluateDamage(true);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.isActive()) {
			flags.setFlag(AIFlags.DO_NOT_VENT);
			return;
		}
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;

		if (target != null) {
			if (ship.getFluxLevel() > 0.7F) return;
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
			float radius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
			boolean closing = BattleNext_ShipAIHelper.getClosingSpeed(ship, target) > 5.0F;
			float attackRange = attackAI.getAttackRange();
			if (((attackRange >= distance - radius
					|| attackRange + ship.getMaxSpeedWithoutBoost() * 3.0F >= distance - radius) && closing)
					|| attackRange * 0.5F >= distance - radius) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
				return;
			}
		}

		ShipAPI nearest = BattleNext_ShipAIHelper.getNearestEnemyInRange(ship, 5000.0F);
		if (ship.getFluxLevel() == 0.0F && nearest == null) {
			AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
					.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
			Vector2f assignmentPoint = assignment == null || assignment.getTarget() == null
					? null : assignment.getTarget().getLocation();
			float assignmentDistance = assignmentPoint == null ? 0.0F
					: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint);
			boolean escort = assignment != null && (assignment.getType() == CombatAssignmentType.LIGHT_ESCORT
					|| assignment.getType() == CombatAssignmentType.MEDIUM_ESCORT
					|| assignment.getType() == CombatAssignmentType.HEAVY_ESCORT);
			BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
			boolean engaged = ai != null && ai.isEngaged();
			boolean safe = threatEval.getTotalThreat() <= 0.0F || threatEval.isAllThreatsValid();
			boolean travel = !engaged && assignmentDistance > 1000.0F && safe;
			travel |= target == null && !engaged && safe;
			boolean hasHeading = flockingAI.getAvoidanceRadius() != Float.MAX_VALUE
					|| Math.abs(flockingAI.getAvoidanceRadius() - ship.getFacing()) < 5.0F;
			if (escort && assignmentDistance < 800.0F) travel = false;
			if ((travel || ship.isRetreating()) && hasHeading) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
				return;
			}
		}

		boolean missileCovered = false;
		boolean targetCovered = false;
		if (ship.getShield() != null && missileDangerDir != null) {
			if (ship.getShield().isOn()) {
				float facing = ship.getShield().getFacing();
				float arc = Math.max(90.0F, ship.getShield().getActiveArc());
				missileCovered = BattleNext_ShipAIHelper.isInArc(facing, arc,
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
				if (target != null) targetCovered = BattleNext_ShipAIHelper.isInArc(facing, arc,
						ship.getLocation(), target.getLocation());
			} else if (ship.getShield().getType() == ShieldType.OMNI) {
				missileCovered = true;
				targetCovered = true;
			} else {
				missileCovered = BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 90.0F,
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
				if (target != null) targetCovered = BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 90.0F,
						ship.getLocation(), target.getLocation());
			}
		}
		eval.setAssumeFiring(!missileCovered);
		eval.resetSolution();
		boolean incoming = missileDangerDir != null && eval.hasSolution();
		float timeToHit = eval.getBestTimeToHit();
		float maxDamage = eval.getMaxWeaponDamage();
		eval.updateShieldState(threatEval, missileDangerDir, collisionDangerDir);
		if (ship.getShield() != null) {
			if (incoming && (eval.isShieldFacingOk() || !missileCovered) && timeToHit < 1.0F) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
				return;
			}
		} else if (ship.getPhaseCloak() != null) {
			if (incoming && (eval.isShieldFacingOk() || !missileCovered) && timeToHit < 1.0F
					&& ship.getFluxLevel() > 0.9F) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
				return;
			}
		} else if (incoming && timeToHit < 1.0F) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		float armor = ship.getArmorGrid().getArmorRating() + ship.getHitpoints() * 0.1F;
		if (threatEval.getMaxThreatData() != null) {
			// approximation: the engine directional armor evaluator is internal;
			// average armor in the same arc supplies the public estimate.
			float angle = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(),
						threatEval.getMaxThreatData().getShip().getLocation());
			armor = ship.getAverageArmorInSlice(angle, 90.0F) + ship.getHitpoints() * 0.1F;
		}
		boolean heavyDamage = maxDamage > armor;
		boolean criticalHull = ship.getHitpoints() < 150.0F * ship.getHullSize().ordinal();
		boolean nearbyExplosion = false;
		if (ship.getShield() == null || !targetCovered) {
			boolean targetCritical = target != null && target.getHitpoints() < 150.0F * target.getHullSize().ordinal();
			if (target != null && !target.isFighter()) {
				float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
				nearbyExplosion = targetCritical && distance < ship.getCollisionRadius()
						+ DamagingExplosionSpec.getShipExplosionRadius(target) + 50.0F;
				heavyDamage |= nearbyExplosion;
			}
			if (maxDamage > armor * 0.25F && criticalHull) heavyDamage = true;
		}
		if (ship.getShield() != null) {
			if (eval.isShieldFacingOk() && !nearbyExplosion || heavyDamage && (!nearbyExplosion || !targetCovered)) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			}
		} else if (ship.getPhaseCloak() != null) {
			if (heavyDamage && !threatEval.isAllThreatsActive() && ship.getFluxLevel() > 0.9F) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			}
		} else if (heavyDamage && !threatEval.isAllThreatsActive()) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
