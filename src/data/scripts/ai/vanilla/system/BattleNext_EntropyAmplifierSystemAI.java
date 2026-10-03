package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.EntropyAmplifierStats;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.EntropyAmplifierSystemAI.
 */
public class BattleNext_EntropyAmplifierSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_AttackAIModule attackAI;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	// intentionally kept dead: vanilla configures this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval firingSolutionEval;

	public BattleNext_EntropyAmplifierSystemAI(ShipAPI ship, ShipwideAIFlags flags,
			BattleNext_ThreatEvalAI threatEval, BattleNext_AttackAIModule attackAI) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.attackAI = attackAI;
		this.system = ship.getSystem();
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship, flags);
		this.firingSolutionEval.setUseBurstDps(true);
		this.firingSolutionEval.setAssumeFiring(true);
		this.firingSolutionEval.setAlwaysEvaluateDamage(true);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;

		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;
		if (target == null || target.isHulk() || target.isPhased() || target.isFighter()) return;
		if (flags.hasFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL)) return;
		// intentionally kept dead: vanilla computes a target overload or venting flag
		// without consuming the result.
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float combinedRadius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		if (distance + combinedRadius > EntropyAmplifierStats.getMaxRange(ship)) return;
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction > 0.75F && fluxFraction < 0.75F) return;

		boolean criticalThreat = flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)
				&& threatEval.getMaxThreatData() != null && threatEval.getMaxThreatData().getShip() == target;
		float bestRange = attackAI.getBestGroupRange();
		boolean attackReady = bestRange >= distance + combinedRadius
				|| flags.hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT);
		ShipAPI nearbyEnemy = BattleNext_ShipAIHelper.getNearestEnemyInRange(target, target.getLocation(),
				1500.0F + target.getCollisionRadius(), ship);
		if (attackReady && !criticalThreat || nearbyEnemy != null && !nearbyEnemy.isNonCombat(true)) {
			if (target.getShield() != null && target.getShield().isOn() || target.isPhased()) {
				flags.setFlag(AIFlags.DELAY_STRIKE_FIRE, system.getChargeUpDur() * 0.5F);
			}
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
