package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.IdleManeuver.
 * Placeholder maneuver that finishes immediately; nothing is ever commanded.
 */
public class BattleNext_IdleManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private BattleNext_ManeuverAPI maneuverAPI;
	private final BattleNext_FlockingAI flockingAI;
	private float elapsedTime = 0.0F;

	public BattleNext_IdleManeuver(ShipAPI ship, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
	}

	@Override
	public void advance(float amount) {
		this.elapsedTime += amount;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return null;
	}

	@Override
	public float getDesiredFacing() {
		return Float.MAX_VALUE;
	}

	@Override
	public void init() {
	}

	@Override
	public float getTargetFacing() {
		return Float.MAX_VALUE;
	}
}
