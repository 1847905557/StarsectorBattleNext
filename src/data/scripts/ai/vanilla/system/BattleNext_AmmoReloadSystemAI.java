package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.AmmoReloadSystemAI.
 */
public class BattleNext_AmmoReloadSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final BattleNext_ThreatEvalAI threatEval;
	private final IntervalUtil interval = new IntervalUtil(1.5F, 2.5F);
	// intentionally kept dead: vanilla constructs this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval firingSolutionEval;
	private final float fluxPerUseFraction;
	private float reloadCooldownTimer;

	public BattleNext_AmmoReloadSystemAI(ShipAPI ship, BattleNext_ThreatEvalAI threatEval) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.threatEval = threatEval;
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship);
		this.fluxPerUseFraction = system.getFluxPerUse() / ship.getMaxFlux();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		reloadCooldownTimer += amount;
		if (!interval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (system.isActive()) return;

		float totalWeight = 0.0F;
		float missingWeight = 0.0F;
		float eligibleWeight = 0.0F;
		boolean emptyWeapon = false;
		boolean baseAmmoOnly = system.getSpecAPI().isReloadBaseAmmoAmountOnly();
		for (WeaponAPI weapon : ship.getAllWeapons()) {
			float weight = weapon.getSpec().getOrdnancePointCost(null);
			totalWeight += weight;
			if (system.getSpecAPI().getWeaponTypes().contains(weapon.getType()) && weapon.usesAmmo()) {
				eligibleWeight += weight;
				float maxAmmo = weapon.getMaxAmmo();
				float missingAmmo = maxAmmo - weapon.getAmmo();
				float baseMaxAmmo = weapon.getSpec().getMaxAmmo();
				if (baseAmmoOnly && missingAmmo >= baseMaxAmmo) missingAmmo = maxAmmo;
				if (maxAmmo <= 0.0F || missingAmmo <= 0.0F) continue;

				float reloadWeight = 1.0F;
				float ammoPerSecond = weapon.getAmmoPerSecond();
				if (ammoPerSecond > 0.0F) {
					reloadWeight = Math.min(1.0F, missingAmmo / ammoPerSecond / 60.0F);
				}
				if (weapon.getAmmo() <= 0) emptyWeapon = true;
				missingWeight += weight * missingAmmo / maxAmmo * reloadWeight;
			}
		}
		if (!emptyWeapon || missingWeight <= 0.0F || totalWeight <= 0.0F) return;

		float fractionMissing = missingWeight / eligibleWeight;
		if (fractionMissing < system.getSpecAPI().getMinFractionToReload()) return;
		float fluxLevel = ship.getFluxLevel();
		if (fluxPerUseFraction > 1.0F - fluxLevel) return;
		float fluxAfterUse = fluxLevel + fluxPerUseFraction;
		if (fluxAfterUse > 0.99F && fluxPerUseFraction > 0.025F) return;
		if (fluxAfterUse > 0.7F && fluxPerUseFraction > 0.25F && fractionMissing < 1.0F && threatEval.isThreatHigh()) return;
		if (reloadCooldownTimer < 10.0F) return;
		float currentCr = ship.getCurrentCR();
		if (currentCr < 0.01F) return;

		float crPerUse = system.getSpecAPI().getCrPerUse();
		float aggregateMissing = missingWeight / totalWeight;
		float crThreshold = crPerUse / (currentCr + (float) Math.random() * crPerUse);
		if (aggregateMissing > crThreshold || crPerUse <= 0.0F) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			reloadCooldownTimer = 0.0F;
		}
	}
}
