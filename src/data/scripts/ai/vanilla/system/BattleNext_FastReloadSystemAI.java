package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.FastReloadSystemAI.
 */
public class BattleNext_FastReloadSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final IntervalUtil decisionInterval = new IntervalUtil(0.5F, 1.0F);
	// intentionally kept dead: vanilla constructs this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval firingSolutionEval;
	private final float fluxPerUseFraction;
	// intentionally kept dead: vanilla updates this high-water mark without reading it.
	private float maxReloadRatio;
	private float timeSinceUse;

	public BattleNext_FastReloadSystemAI(ShipAPI ship) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship);
		this.fluxPerUseFraction = system.getFluxPerUse() / ship.getMaxFlux();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		decisionInterval.advance(amount);
		timeSinceUse += amount;
		if (!decisionInterval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (system.isActive()) return;
		if (target == null) return;

		// intentionally kept dead: vanilla reads system in-time and discards it.
		float totalCooldown = 0.0F;
		float remainingCooldown = 0.0F;
		float totalWeight = 0.0F;
		float matchingWeight = 0.0F;
		for (WeaponAPI weapon : ship.getAllWeapons()) {
			float weight = weapon.getSpec().getOrdnancePointCost(null);
			if (weapon.hasAIHint(AIHints.PD)) weight *= 0.1F;
			if (weapon.hasAIHint(AIHints.STRIKE)) weight *= 2.0F;
			if (weapon.getType() == WeaponType.MISSILE) weight *= 1.5F;
			totalWeight += weight;
			if (system.getSpecAPI().getWeaponTypes().contains(weapon.getType())
					&& weapon.getCooldown() >= 2.0F + fluxPerUseFraction * 5.0F) {
				totalCooldown += weapon.getCooldown();
				if (!weapon.isFiring() || !weapon.isInBurst()) {
					remainingCooldown += weapon.getCooldownRemaining();
				}
				matchingWeight += weight;
			}
		}
		if (totalCooldown <= 0.0F || remainingCooldown <= 0.0F || totalWeight <= 0.0F) return;

		float matchingFraction = matchingWeight / totalWeight;
		if (target.getFluxTracker().isOverloadedOrVenting()
				&& (target.getFluxTracker().getOverloadTimeRemaining() > 5.0F
						|| target.getFluxTracker().getTimeToVent() > 5.0F)) {
			matchingFraction *= 2.0F;
		}
		float fluxLevel = ship.getFluxLevel();
		if (fluxPerUseFraction > 1.0F - fluxLevel) return;
		float fluxAfterUse = fluxLevel + fluxPerUseFraction;
		if (fluxAfterUse > matchingFraction || fluxAfterUse > 0.9F && fluxPerUseFraction > 0.025F) return;

		float reloadRatio = remainingCooldown / totalCooldown;
		if (reloadRatio > maxReloadRatio) maxReloadRatio = reloadRatio;
		if (reloadRatio >= system.getSpecAPI().getMinFractionToReload()) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			timeSinceUse = 0.0F;
		}
	}
}
