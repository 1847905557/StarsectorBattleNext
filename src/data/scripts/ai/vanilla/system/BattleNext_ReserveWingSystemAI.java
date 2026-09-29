package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.ReserveWingStats;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.ReserveWingSystemAI.
 */
public class BattleNext_ReserveWingSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);

	public BattleNext_ReserveWingSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.isActive()) {
			flags.setFlag(AIFlags.DO_NOT_VENT, 1.0F);
			return;
		}
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		if (target == null) return;

		float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float minimumRange = Float.MAX_VALUE;
		for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
			if (bay.getWing() == null) continue;
			float range = bay.getWing().getRange();
			if (range > 0.0F && range < minimumRange) minimumRange = range;
		}
		if (distance > minimumRange) return;

		float attacking = 0.0F;
		float present = 0.0F;
		float full = 0.0F;
		float extra = 0.0F;
		float capacity = 0.0F;
		int bays = ship.getLaunchBaysCopy().size();
		for (FighterWingAPI wing : ship.getAllWings()) {
			float additional = ReserveWingStats.getAdditionalFor(wing.getSpec(), bays);
			float excess = Math.max(0.0F, wing.getWingMembers().size() - wing.getSpec().getNumFighters());
			float members = wing.getWingMembers().size();
			float normal = wing.getSpec().getNumFighters();
			if (wing.getSpec().isBomber()) {
				members *= 2.0F;
				normal *= 2.0F;
				excess *= 2.0F;
				additional *= 2.0F;
			}
			if (wing.getSpec().isInterceptor()) {
				members *= 0.5F;
				normal *= 0.5F;
				excess *= 0.5F;
				additional *= 0.5F;
			}
			if (wing.getLeader().getAIFlags().hasFlag(AIFlags.IN_ATTACK_RUN)) attacking += members;
			present += members;
			full += normal;
			extra += excess;
			capacity += additional;
		}
		present = Math.max(1.0F, present);
		full = Math.max(1.0F, full);
		capacity = Math.max(1.0F, capacity);
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction > 0.75F && fluxFraction < 0.75F) return;
		if (attacking / present > 0.5F || attacking / full > 0.5F) return;
		if (extra / capacity > 0.25F) return;
		ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		flags.setFlag(AIFlags.DO_NOT_VENT, 1.0F);
	}
}
