package data.scripts.ai.vanilla.maneuvers;

import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortSpotIndexComparator.
 * Orders escort spots by how many allies already hold them.
 * The decompiled engine body exposes the comparison as compareByIndex without a
 * compare() override, which cannot compile; the port restores compare() with the
 * same ordering.
 */
class BattleNext_EscortSpotIndexComparator implements Comparator<BattleNext_EscortSpot> {
	BattleNext_EscortSpotIndexComparator(BattleNext_EscortTargetManeuverV3 outerManeuver) {
		this.outerManeuver = outerManeuver;
	}

	private BattleNext_EscortTargetManeuverV3 outerManeuver;

	public BattleNext_EscortTargetManeuverV3 getOuterManeuver() {
		return this.outerManeuver;
	}

	@Override
	public int compare(BattleNext_EscortSpot first, BattleNext_EscortSpot second) {
		return first.index - second.index;
	}
}
