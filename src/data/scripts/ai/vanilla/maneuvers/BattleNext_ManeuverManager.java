package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ManeuverManager.
 * FIFO queue of maneuvers with a single active entry.
 * Deviation from vanilla: the engine sets the MANEUVER_TARGET flag also for the
 * fighter-only AttackRunManeuver; that class is not ported, so the flag is only
 * set for the strafe and assignment-based maneuvers.
 */
public class BattleNext_ManeuverManager {
	private final List<BattleNext_ManeuverEntry> maneuvers = new ArrayList<>();
	private BattleNext_ManeuverEntry currentManeuver = null;
	private ShipAPI ship;

	public BattleNext_ManeuverManager(ShipAPI ship) {
		this.ship = ship;
	}

	public float getTotalRemainingTime() {
		float total = 0.0F;
		for (BattleNext_ManeuverEntry entry : this.maneuvers) {
			total += entry.getDuration() - entry.getElapsedTime();
		}
		return total;
	}

	public void addManeuver(BattleNext_Maneuver maneuver, float duration) {
		this.maneuvers.add(new BattleNext_ManeuverEntry(this, maneuver, duration));
		if (this.currentManeuver == null) {
			this.currentManeuver = this.maneuvers.remove(0);
		}
	}

	public void advance(float amount) {
		if (this.currentManeuver != null && this.currentManeuver.isDone()) {
			this.currentManeuver = null;
		}

		if (this.currentManeuver != null || !this.maneuvers.isEmpty()) {
			if (this.currentManeuver == null) {
				this.currentManeuver = this.maneuvers.remove(0);
			}

			this.currentManeuver.advance(amount);
			if (this.currentManeuver != null && this.currentManeuver.isDone()) {
				BattleNext_Maneuver finished = this.currentManeuver.getManeuver();
				if (finished instanceof BattleNext_StrafeTargetManeuverV2 || finished instanceof BattleNext_AssignmentBasedManeuver) {
					CombatEntityAPI target = finished.getTarget();
					if (this.ship.getAIFlags() != null) {
						this.ship.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, 1.0F, target);
					}
				}
				this.currentManeuver = null;
			}
		}
	}

	public void clearAllManeuvers() {
		this.maneuvers.clear();
		this.currentManeuver = null;
	}

	public void initCurrentManeuver() {
		if (this.currentManeuver != null) {
			this.currentManeuver.getManeuver().init();
		}
	}

	public float getDesiredFacing() {
		return this.currentManeuver == null ? Float.MAX_VALUE : this.currentManeuver.getManeuver().getDesiredFacing();
	}

	public float getTargetFacing() {
		return this.currentManeuver == null ? Float.MAX_VALUE : this.currentManeuver.getManeuver().getTargetFacing();
	}

	public boolean isCurrentManeuverDone() {
		return this.currentManeuver == null ? false : this.currentManeuver.getManeuver().isDone();
	}

	public BattleNext_Maneuver getCurrentManeuver() {
		return this.currentManeuver == null ? null : this.currentManeuver.getManeuver();
	}

	public boolean isManeuverQueueEmpty() {
		return this.currentManeuver == null && this.maneuvers.isEmpty();
	}
}
