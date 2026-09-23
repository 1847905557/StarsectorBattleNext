package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ManeuverEntry.
 * One queued maneuver plus its time budget.
 * Deviation from vanilla: the hulk check also covers maneuvers exposing a ship
 * target through the shared Maneuver interface only; the engine additionally
 * special-cases AttackRunManeuver, which is fighter-only and not ported.
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

		CombatEntityAPI target = this.maneuver.getTarget();
		if (target instanceof ShipAPI && ((ShipAPI) target).isHulk()) {
			this.done = true;
		}
	}

	public boolean isDone() {
		return this.done;
	}

	public BattleNext_Maneuver getManeuver() {
		return this.maneuver;
	}
}
