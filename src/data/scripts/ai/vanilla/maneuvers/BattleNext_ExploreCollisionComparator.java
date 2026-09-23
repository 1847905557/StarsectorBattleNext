package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ExploreCollisionComparator.
 * Orders danger entries by angle for the exploration sweep-line merge.
 */
class BattleNext_ExploreCollisionComparator implements Comparator<BattleNext_CollisionDangerEntry> {
	BattleNext_ExploreCollisionComparator(BattleNext_ExploreManeuver outerManeuver) {
		this.outerManeuver = outerManeuver;
	}

	private BattleNext_ExploreManeuver outerManeuver;

	public BattleNext_ExploreManeuver getOuterManeuver() {
		return this.outerManeuver;
	}

	@Override
	public int compare(BattleNext_CollisionDangerEntry first, BattleNext_CollisionDangerEntry second) {
		return (int) Math.signum(first.getAngle() - second.getAngle());
	}
}
