package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.AcausalDisruptorStats;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.AcausalDisruptorSystemAI.
 */
public class BattleNext_AcausalDisruptorSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	// intentionally kept dead: vanilla configures this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval firingSolutionEval;

	public BattleNext_AcausalDisruptorSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.system = ship.getSystem();
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship, flags);
		this.firingSolutionEval.setUseBurstDps(true);
		this.firingSolutionEval.setAssumeFiring(true);
		this.firingSolutionEval.setAlwaysEvaluateDamage(true);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount * 4.0F);
		if (!interval.intervalElapsed()) return;
		if (target != null && target.getFluxTracker().isOverloadedOrVenting()) return;
		if (system.isActive()) {
			flags.setFlag(AIFlags.DO_NOT_VENT);
			return;
		}
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;

		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;
		if (target == null || target.isHulk() || target.isFighter()) return;
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction > 0.85F) return;

		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float combinedRadius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		float range = AcausalDisruptorStats.getMaxRange(ship) + combinedRadius * 0.75F;
		boolean closeEnough = range >= distance;
		boolean firmlyInRange = range * 0.67F >= distance || range - 100.0F >= distance;
		// approximation: the engine checks TeleporterSystem by concrete type; the
		// public system spec's aiType identifies its two teleporter configurations.
		boolean evasiveSystem = false;
		if (target.getSystem() != null && target.getSystem().getSpecAPI().getSpecJson() != null) {
			String type = target.getSystem().getSpecAPI().getSpecJson().optString("aiType");
			evasiveSystem = "PHASE_TELEPORTER".equals(type) || "PHASE_TELEPORTER_2".equals(type);
		}
		boolean protectedTarget = evasiveSystem || target.isPhased();
		if (!target.isPhased() && target.getShield() != null) {
			protectedTarget = BattleNext_ShipAIHelper.isInArc(target.getShield().getFacing(),
					Math.min(target.getShield().getArc() + 20.0F, target.getShield().getActiveArc() + 40.0F),
					target.getLocation(), ship.getLocation());
			if (!protectedTarget) {
				float threat = BattleNext_ShipAIHelper.getTotalThreatInRange(ship,
						BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation()), 120.0F, 2000.0F, false, false);
				protectedTarget = threat > 0.0F;
			}
		}
		boolean criticalThreat = flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)
				&& threatEval.getMaxThreatData() != null && threatEval.getMaxThreatData().getShip() == target;
		if (!protectedTarget && !criticalThreat) return;
		if (closeEnough || firmlyInRange) {
			if (target.getShield() != null && target.getShield().isOn() || target.isPhased()) {
				flags.setFlag(AIFlags.DELAY_STRIKE_FIRE, system.getChargeUpDur() * 0.5F);
			}
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
