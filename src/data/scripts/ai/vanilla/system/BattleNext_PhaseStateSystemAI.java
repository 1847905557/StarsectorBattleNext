package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.PhaseStateSystemAI.
 */
public class BattleNext_PhaseStateSystemAI implements BattleNext_SystemAI {
	private final ShipwideAIFlags flags;
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final IntervalUtil decisionInterval = new IntervalUtil(0.25F, 0.5F);
	// intentionally kept dead: vanilla configures this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval firingSolutionEval;

	public BattleNext_PhaseStateSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.flags = flags;
		this.ship = ship;
		this.system = ship.getSystem();
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship, flags);
		this.firingSolutionEval.setUseBurstDps(true);
		this.firingSolutionEval.setAssumeFiring(true);
		this.firingSolutionEval.setAlwaysEvaluateDamage(true);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		decisionInterval.advance(amount);
		if (!decisionInterval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;

		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;
		if (target == null || target.isHulk() || target.isPhased()) return;
		if (flags.hasFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL)) return;

		// intentionally kept dead: vanilla computes an overload or venting flag
		// for the target and does not consume it.
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float combinedRadius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		if (distance + combinedRadius > 1000.0F) return;
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction > 0.75F && fluxFraction < 0.75F) return;

		boolean closing = BattleNext_ShipAIHelper.getClosingSpeed(ship, target) > 5.0F;
		boolean targetFaster = target.getMaxSpeedWithoutBoost() > ship.getMaxSpeedWithoutBoost();
		ShipAPI other = BattleNext_ShipAIHelper.getNearestEnemyInRange(target, target.getLocation(),
				1500.0F + target.getCollisionRadius(), ship);
		boolean otherClosing = other != null && BattleNext_ShipAIHelper.getClosingSpeed(other, target) > 5.0F;
		boolean targetFasterThanOther = other != null && target.getMaxSpeedWithoutBoost() > other.getMaxSpeedWithoutBoost();
		boolean inRearArc = BattleNext_ShipAIHelper.isInArc(target.getFacing(), 210.0F,
				target.getLocation(), ship.getLocation());
		if (closing && targetFaster || other != null && !other.isNonCombat(true) && otherClosing && targetFasterThanOther
				|| inRearArc && !targetFaster) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
