package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.RetreatCollisionComparator.
 * Orders danger entries by angle for the retreat sweep-line merge.
 * The decompiled engine body exposes the comparison as compare without a
 * Comparator override contract visible in source; the port restores the
 * compare() override with the same ordering.
 */
class BattleNext_RetreatCollisionComparator implements Comparator<BattleNext_CollisionDangerEntry> {
	BattleNext_RetreatCollisionComparator(BattleNext_RetreatManeuver outerManeuver) {
		this.outerManeuver = outerManeuver;
	}

	private BattleNext_RetreatManeuver outerManeuver;

	public BattleNext_RetreatManeuver getOuterManeuver() {
		return this.outerManeuver;
	}

	@Override
	public int compare(BattleNext_CollisionDangerEntry first, BattleNext_CollisionDangerEntry second) {
		return (int) Math.signum(first.getAngle() - second.getAngle());
	}
}
