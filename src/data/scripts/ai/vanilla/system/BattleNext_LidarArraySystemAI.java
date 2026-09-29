package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.LidarArrayStats;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.LidarArraySystemAI.
 */
public class BattleNext_LidarArraySystemAI implements BattleNext_SystemAI {
	private static final String TEMP_RANGE_ID = "BattleNext_LidarArraySystemAI_temp";
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final float fluxPerUseFraction;
	private ShipAPI targetOverride;

	public BattleNext_LidarArraySystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
		float baseCapacity = ship.getMutableStats().getFluxCapacity().getBaseValue();
		float baseDissipation = ship.getMutableStats().getFluxDissipation().getBaseValue();
		this.fluxPerUseFraction = (system.getSpecAPI().getFluxPerUse()
				+ system.getSpecAPI().getFluxPerUseBaseCap() * baseCapacity
				+ system.getSpecAPI().getFluxPerUseBaseRate() * baseDissipation) / ship.getMaxFlux();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.isActive()) {
			BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
			if (ai != null) ai.setTargetOverride(targetOverride);
		} else {
			targetOverride = null;
		}
		if (!interval.intervalElapsed()) return;
		setBackOffRange(target);
		if (system.isActive()) {
			flags.setFlag(AIFlags.DO_NOT_VENT);
			return;
		}
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (missileDangerDir != null && (ai == null || ai.getEval().hasSolution())) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;
		if (target == null) return;
		if (ship.getFluxLevel() > 0.9F || ship.getFluxLevel() + fluxPerUseFraction > 0.95F) return;

		float angle = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), target.getLocation());
		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		distance -= ship.getCollisionRadius() * 0.5F;
		distance -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(ship.getLocation(), target, false);
		float closeWeapons = 0.0F;
		float inRangeWeapons = 0.0F;
		float eligibleWeapons = 0.0F;
		float rangeBonus = LidarArrayStats.RANGE_BONUS - LidarArrayStats.PASSIVE_RANGE_BONUS;
		ship.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(TEMP_RANGE_ID, rangeBonus);
		ship.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(TEMP_RANGE_ID, rangeBonus);
		try {
			for (WeaponAPI weapon : ship.getAllWeapons()) {
				if (weapon.isDecorative() || !weapon.getSlot().isHardpoint() || weapon.isBeam()) continue;
				if (weapon.getType() != WeaponType.BALLISTIC && weapon.getType() != WeaponType.ENERGY) continue;
				if (BattleNext_ShipAIHelper.isInArc(weapon.getCurrAngle(), 5.0F, angle) && distance < weapon.getRange()) {
					inRangeWeapons++;
					if (distance < weapon.getRange() * 0.85F) closeWeapons++;
				}
				eligibleWeapons++;
			}
		} finally {
			ship.getMutableStats().getBallisticWeaponRangeBonus().unmodify(TEMP_RANGE_ID);
			ship.getMutableStats().getEnergyWeaponRangeBonus().unmodify(TEMP_RANGE_ID);
		}
		boolean inRange = inRangeWeapons / eligibleWeapons > 0.5F;
		boolean close = closeWeapons / eligibleWeapons > 0.5F;
		if (inRange && BattleNext_ShipAIHelper.getClosingSpeed(ship, target) > 5.0F || close) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			targetOverride = target;
		}
	}

	public void setBackOffRange(ShipAPI target) {
		if (target == null) return;
		float rangeBonus = LidarArrayStats.RANGE_BONUS - LidarArrayStats.PASSIVE_RANGE_BONUS;
		ship.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(TEMP_RANGE_ID, rangeBonus);
		ship.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(TEMP_RANGE_ID, rangeBonus);
		float range = 3000.0F;
		boolean found = false;
		try {
			for (WeaponAPI weapon : ship.getAllWeapons()) {
				if (weapon.isDecorative() || !weapon.getSlot().isHardpoint()) continue;
				if (weapon.getType() != WeaponType.BALLISTIC && weapon.getType() != WeaponType.ENERGY) continue;
				range = Math.min(range, weapon.getRange() * 0.85F);
				found = true;
			}
		} finally {
			ship.getMutableStats().getBallisticWeaponRangeBonus().unmodify(TEMP_RANGE_ID);
			ship.getMutableStats().getEnergyWeaponRangeBonus().unmodify(TEMP_RANGE_ID);
		}
		if (found && !BattleNext_ShipAIHelper.hasInterceptTask(ship)) {
			range += (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
			flags.setFlag(AIFlags.BACK_OFF_MIN_RANGE, 1.0F, range);
		}
	}
}
