package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.ShieldFluxSystemAI.
 */
public class BattleNext_ShieldFluxSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final BattleNext_FiringSolutionEval eval;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final float shieldDamageTakenMult;
	private final float fluxPerUse;
	private final float fluxPerSecond;
	private final float shieldUpkeep;

	public BattleNext_ShieldFluxSystemAI(ShipAPI ship) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.eval = new BattleNext_FiringSolutionEval(ship);
		// approximation: ShipStats.create(spec) is engine-only. The vanilla
		// HighEnergyFocusStats leaves shield damage taken unchanged.
		this.shieldDamageTakenMult = 1.0F;
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
		if (system.isOutOfAmmo() || !interval.intervalElapsed()) return;
		eval.resetSolution();
		float fluxLevel = ship.getFluxLevel();
		boolean noSoftFlux = Math.abs(fluxLevel - ship.getHardFluxLevel()) <= 0.01F;
		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;
		boolean matchingWeaponFiring = false;
		for (WeaponAPI weapon : ship.getAllWeapons()) {
			if (system.getSpecAPI().getWeaponTypes().contains(weapon.getType()) && weapon.isFiring()) {
				matchingWeaponFiring = true;
				break;
			}
		}
		if (target == null) return;
		// intentionally kept dead: vanilla reads whether the shield is off and
		// discards the result.
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
		float timeAvailable = BattleNext_ShipAIHelper.getFluxAvailable(ship) / fluxCost;
		float duration = system.getSpecAPI().getActive() + system.getSpecAPI().getIn() + system.getSpecAPI().getOut();
		if (matchingWeaponFiring && !system.isOn() && target.getFluxLevel() > fluxLevel && timeAvailable > duration) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
