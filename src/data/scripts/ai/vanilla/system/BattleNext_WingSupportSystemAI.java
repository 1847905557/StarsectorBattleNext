package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.WingSupportSystemAI.
 */
public class BattleNext_WingSupportSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);

	public BattleNext_WingSupportSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.isOn()) {
			flags.setFlag(AIFlags.DO_NOT_VENT, 1.0F);
			return;
		}
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;

		float attacking = 0.0F;
		float present = 0.0F;
		float full = 0.0F;
		for (FighterWingAPI wing : ship.getAllWings()) {
			float members = wing.getWingMembers().size();
			float capacity = wing.getSpec().getNumFighters();
			if (wing.getSpec().isBomber()) {
				members *= 2.0F;
				capacity *= 2.0F;
			}
			if (wing.getSpec().isInterceptor()) {
				members *= 0.5F;
				capacity *= 0.5F;
			}
			float firingFraction = 0.0F;
			if (wing.getSpec().isSupport() || wing.getSpec().isInterceptor()) {
				float firingWeight = 0.0F;
				float weaponWeight = 0.0F;
				for (ShipAPI fighter : wing.getWingMembers()) {
					for (WeaponAPI weapon : fighter.getAllWeapons()) {
						if (weapon.isDecorative()) continue;
						float weight = weapon.getSize() == WeaponSize.LARGE ? 4.0F
								: weapon.getSize() == WeaponSize.MEDIUM ? 2.0F : 1.0F;
						if (weapon.isFiring()) firingWeight += weight;
						weaponWeight += weight;
					}
				}
				weaponWeight = Math.max(1.0F, weaponWeight);
				firingFraction = firingWeight / weaponWeight;
				if (firingFraction > 0.45F) {
					members *= 0.4F;
					capacity *= 0.4F;
				} else {
					members = 0.0F;
					capacity = 0.0F;
				}
			}
			if (wing.getLeader().getAIFlags().hasFlag(AIFlags.IN_ATTACK_RUN)
					|| wing.getSpec().isSupport() || wing.getSpec().isInterceptor() && firingFraction > 0.45F) {
				attacking += members;
			}
			present += members;
			full += capacity;
		}
		present = Math.max(1.0F, present);
		full = Math.max(1.0F, full);
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction > 0.75F && fluxFraction < 0.75F) return;
		if (attacking / present < 0.5F || attacking / full < 0.5F) return;
		ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		flags.setFlag(AIFlags.DO_NOT_VENT, 1.0F);
	}
}
