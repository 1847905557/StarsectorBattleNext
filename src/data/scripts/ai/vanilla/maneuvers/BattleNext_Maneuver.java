package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.movement.maneuvers.Maneuver.
 * One atomic maneuver the maneuver manager can queue and advance.
 * Deviation from vanilla: getTarget returns CombatEntityAPI instead of the
 * engine-internal CollisionEntity.
 */
public interface BattleNext_Maneuver {
	float unusedRange = Float.MAX_VALUE;

	void advance(float amount);

	boolean isDone();

	void init();

	float getDesiredFacing();

	float getTargetFacing();

	CombatEntityAPI getTarget();
}
