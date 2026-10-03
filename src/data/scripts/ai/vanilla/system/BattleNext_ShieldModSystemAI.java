package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.ShieldModSystemAI.
 */
public class BattleNext_ShieldModSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final BattleNext_FiringSolutionEval eval;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final float shieldDamageTakenMult;
	private final float fluxPerUse;
	private final float fluxPerSecond;
	private final float shieldUpkeep;
	private float activeTime;

	public BattleNext_ShieldModSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
		this.eval = new BattleNext_FiringSolutionEval(ship);
		// approximation: ShipStats.create(spec) is engine-only. The vanilla
		// FortressShieldStats applies a 0.1 multiplier at full effect; other
		// scripts retain the public base multiplier of 1.0.
		this.shieldDamageTakenMult = system.getScript() != null
				&& "data.shipsystems.scripts.FortressShieldStats".equals(system.getSpecAPI().getStatsScriptClassName())
					? 0.1F : 1.0F;
		float capacity = ship.getMutableStats().getFluxCapacity().getBaseValue();
		float dissipation = ship.getMutableStats().getFluxDissipation().getBaseValue();
		ShipSystemSpecAPI spec = system.getSpecAPI();
		this.fluxPerUse = spec.getFluxPerUse() + spec.getFluxPerUseBaseCap() * capacity
				+ spec.getFluxPerUseBaseRate() * dissipation;
		this.fluxPerSecond = spec.getFluxPerSecond() + spec.getFluxPerSecondBaseCap() * capacity
				+ spec.getFluxPerSecondBaseRate() * dissipation;
		this.shieldUpkeep = ship.getShield() == null ? 0.0F : ship.getShield().getUpkeep();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.isOutOfAmmo()) return;
		activeTime = system.isOn() ? activeTime + amount : 0.0F;
		if (activeTime > 0.0F && activeTime < 1.0F && ship.getFluxLevel() < 0.99F) return;
		if (!interval.intervalElapsed()) return;
		if (ship.getShield() == null || ship.getShield().isOff()) return;
		eval.resetSolution();
		float fluxLevel = ship.getFluxLevel();
		boolean noSoftFlux = Math.abs(fluxLevel - ship.getHardFluxLevel()) <= 0.01F;
		if (noSoftFlux && system.isOn()) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		if (system.isOn() && fluxLevel < 0.95F) flags.setFlag(AIFlags.KEEP_SHIELDS_ON);
		float fluxCost = fluxPerSecond;
		float damageComparison = 0.0F;
		if (!system.getSpecAPI().isDissipationAllowed() && !noSoftFlux) {
			fluxCost += Math.max(0.0F, BattleNext_ShipAIHelper.getDissipationRate(ship) - shieldUpkeep);
		}
		if (system.isOn()) {
			fluxCost += eval.getHullDamage();
			damageComparison += eval.getHullDamage() / shieldDamageTakenMult;
		} else {
			fluxCost += eval.getHullDamage() * shieldDamageTakenMult;
			damageComparison += eval.getHullDamage();
		}
		if (system.isOn() && damageComparison * 1.5F < fluxCost
				|| !system.isOn() && fluxCost < damageComparison) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
