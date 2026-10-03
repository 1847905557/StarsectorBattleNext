package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.WeaponTypeSystemAI.
 */
public class BattleNext_WeaponTypeSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);

	public BattleNext_WeaponTypeSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
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
		if (target == null || ship.getFluxLevel() > 0.9F) return;

		boolean matchingWeapon = false;
		float totalWeight = 0.0F;
		float firingWeight = 0.0F;
		float maxRange = 0.0F;
		if (system.getSpecAPI().getWeaponTypes() == null) {
			matchingWeapon = true;
		} else {
			for (WeaponAPI weapon : ship.getAllWeapons()) {
				if (!system.getSpecAPI().getWeaponTypes().contains(weapon.getType())) continue;
				matchingWeapon = true;
				maxRange = Math.max(maxRange, weapon.getRange());
				float weight = weapon.getSize() == WeaponSize.LARGE ? 7.0F
						: weapon.getSize() == WeaponSize.MEDIUM ? 2.0F : 1.0F;
				if (weapon.hasAIHint(AIHints.STRIKE)) weight *= 2.0F;
				if (weapon.isBeam() && !weapon.isBurstBeam()) weight *= 0.5F;
				totalWeight += weight;
				if (weapon.isFiring() && (weapon.getCooldown() < 1.0F || weapon.getCooldownRemaining() <= 0.0F)) {
					firingWeight += weight;
				}
			}
		}
		if (!matchingWeapon) return;
		if (maxRange <= 0.0F) maxRange = 100000.0F;
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float combinedRadius = (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		boolean closing = BattleNext_ShipAIHelper.getClosingSpeed(ship, target) > 5.0F;
		boolean lowAmmo = system.getSpecAPI().usesAmmo() && system.getMaxAmmo() > 1 && system.getAmmo() <= 1;
		if (maxRange >= distance - combinedRadius && closing && !lowAmmo
				|| maxRange * 0.5F >= distance - combinedRadius && !lowAmmo
				|| firingWeight > 0.0F && firingWeight >= totalWeight * 0.5F) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
