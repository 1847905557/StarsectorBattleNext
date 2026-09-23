package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.StrafeCollisionComparator.
 * Orders danger entries by angle for the sweep-line merge.
 * The decompiled engine body exposes the comparison as compareByAngle without a
 * compare() override, which cannot compile; the port restores compare() with the
 * same ordering.
 */
class BattleNext_StrafeCollisionComparator implements Comparator<BattleNext_CollisionDangerEntry> {
	BattleNext_StrafeCollisionComparator(BattleNext_StrafeTargetManeuverV2 outerManeuver) {
		this.outerManeuver = outerManeuver;
	}

	private BattleNext_StrafeTargetManeuverV2 outerManeuver;

	public BattleNext_StrafeTargetManeuverV2 getOuterManeuver() {
		return this.outerManeuver;
	}

	@Override
	public int compare(BattleNext_CollisionDangerEntry first, BattleNext_CollisionDangerEntry second) {
		return (int) Math.signum(first.getAngle() - second.getAngle());
	}
}
