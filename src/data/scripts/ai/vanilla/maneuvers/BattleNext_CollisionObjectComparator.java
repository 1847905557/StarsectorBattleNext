package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.CollisionObjectComparator.
 * Orders danger entries by angle for the escort sweep-line merge.
 * The decompiled engine body exposes the comparison as compareByDanger without a
 * compare() override, which cannot compile; the port restores compare() with the
 * same ordering.
 */
class BattleNext_CollisionObjectComparator implements Comparator<BattleNext_CollisionDangerEntry> {
	BattleNext_CollisionObjectComparator(BattleNext_EscortTargetManeuverV3 outerManeuver) {
		this.outerManeuver = outerManeuver;
	}

	private BattleNext_EscortTargetManeuverV3 outerManeuver;

	public BattleNext_EscortTargetManeuverV3 getOuterManeuver() {
		return this.outerManeuver;
	}

	@Override
	public int compare(BattleNext_CollisionDangerEntry first, BattleNext_CollisionDangerEntry second) {
		return (int) Math.signum(first.getAngle() - second.getAngle());
	}
}
