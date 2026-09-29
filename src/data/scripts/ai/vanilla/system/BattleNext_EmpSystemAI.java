package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.EmpSystemAI.
 */
public class BattleNext_EmpSystemAI implements BattleNext_SystemAI {
	private final ShipwideAIFlags flags;
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);

	public BattleNext_EmpSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.flags = flags;
		this.ship = ship;
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
		if (ship.getFluxLevel() > 0.9F) return;

		// approximation: EmpSystem.getPotentialTargets(false) is internal; visible enemy
		// ships within the public system range stand in for its target pool.
		float range = system.getSpecAPI().getRange(ship.getMutableStats());
		for (ShipAPI other : Global.getCombatEngine().getShips()) {
			if (other.getOwner() == ship.getOwner() || !other.isAlive() || other.isHulk()) continue;
			if (!BattleNext_ShipAIHelper.isEntityVisible(ship.getOriginalOwner(), other)) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), other.getLocation());
			if (distance > range + other.getCollisionRadius()) continue;
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
	}
}
