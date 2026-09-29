package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ManeuverEntry.
 * One queued maneuver plus its time budget.
 * Hulk termination applies to strafe and assignment maneuvers.
 */
class BattleNext_ManeuverEntry {
	private BattleNext_Maneuver maneuver;
	private float duration;
	private float elapsedTime;

	public float getDuration() {
		return this.duration;
	}

	public float getElapsedTime() {
		return this.elapsedTime;
	}
	private boolean done;

	public BattleNext_ManeuverEntry(BattleNext_ManeuverManager manager, BattleNext_Maneuver maneuver, float duration) {
		this.done = false;
		this.maneuver = maneuver;
		this.duration = duration;
		this.elapsedTime = 0.0F;
	}

	public void advance(float amount) {
		this.maneuver.advance(amount);
		this.elapsedTime += amount;
		if (this.elapsedTime > this.duration) {
			this.done = true;
		}

		if (this.maneuver instanceof BattleNext_StrafeTargetManeuverV2
			|| this.maneuver instanceof BattleNext_AssignmentBasedManeuver) {
			CombatEntityAPI target = this.maneuver.getTarget();
			if (target instanceof ShipAPI && ((ShipAPI) target).isHulk()) {
				this.done = true;
			}
		}
	}

	public boolean isDone() {
		return this.done;
	}

	public BattleNext_Maneuver getManeuver() {
		return this.maneuver;
	}
}
