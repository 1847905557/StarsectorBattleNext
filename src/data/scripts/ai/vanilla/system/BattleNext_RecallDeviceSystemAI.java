package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.RecallDeviceStats;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.RecallDeviceSystemAI.
 */
public class BattleNext_RecallDeviceSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(1.0F, 2.0F);
	private float reloadDelay = 100000.0F;

	public BattleNext_RecallDeviceSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		reloadDelay -= amount;
		if (!interval.intervalElapsed()) return;
		if (system.isActive()) {
			flags.setFlag(AIFlags.DO_NOT_VENT, 3.0F);
			return;
		}
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		if (target == null) return;

		float needsRefit = 0.0F;
		float active = 0.0F;
		for (ShipAPI fighter : RecallDeviceStats.getFighters(ship)) {
			FighterWingAPI wing = fighter.getWing();
			if (wing == null) continue;
			// approximation: FighterWing.fighterNeedsRefit() is internal; public
			// returning state and current CR identify refit demand.
			boolean refit = wing.isReturning(fighter) || fighter.getCurrentCR() < 0.5F;
			if (fighter.getHullLevel() < 0.4F) refit = true;
			if (fighter.getFluxTracker().getOverloadTimeRemaining() > 3.0F) refit = true;
			if (fighter.getEngineController().isFlamedOut()) refit = true;
			float weight = wing.getSpec().isBomber() ? 2.0F : wing.getSpec().isInterceptor() ? 0.1F : 1.0F;
			if (refit) needsRefit += weight;
			active += weight;
		}
		needsRefit = Math.max(0.1F, needsRefit);
		active = Math.max(0.1F, active);
		float full = 0.0F;
		for (FighterWingAPI wing : ship.getAllWings()) {
			float weight = wing.getSpec().isBomber() ? 2.0F : wing.getSpec().isInterceptor() ? 0.1F : 1.0F;
			full += wing.getSpec().getNumFighters() * weight;
		}
		full = Math.max(0.1F, full);
		float fractionActive = needsRefit / active;
		float fractionFull = needsRefit / full;
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction > 0.75F && fluxFraction < 0.75F) return;
		if (reloadDelay > 0.0F) {
			if (fractionActive > 0.6F && fractionFull > 0.4F) reloadDelay = 5.0F;
			if (fractionActive < 0.75F || fractionFull < 0.33F) return;
		}
		ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		reloadDelay = 100000.0F;
		flags.setFlag(AIFlags.DO_NOT_VENT, 3.0F);
	}
}
