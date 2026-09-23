package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortSpotRangeComparatorV2.
 * Second range ordering over escort spots, identical to the first in the
 * decompiled engine source; both are kept for structural fidelity.
 * The decompiled engine body exposes the comparison as compareByRange without a
 * compare() override, which cannot compile; the port restores compare() with the
 * same ordering.
 */
class BattleNext_EscortSpotRangeComparatorV2 implements Comparator<BattleNext_EscortSpot> {
	BattleNext_EscortSpotRangeComparatorV2(BattleNext_EscortTargetManeuverV3 outerManeuver) {
		this.outerManeuver = outerManeuver;
	}

	private BattleNext_EscortTargetManeuverV3 outerManeuver;

	public BattleNext_EscortTargetManeuverV3 getOuterManeuver() {
		return this.outerManeuver;
	}

	@Override
	public int compare(BattleNext_EscortSpot first, BattleNext_EscortSpot second) {
		float firstRange = BattleNext_ShipAIHelper.getDistance(first.location, this.outerManeuver.getThreatEntityField().getLocation());
		float secondRange = BattleNext_ShipAIHelper.getDistance(second.location, this.outerManeuver.getThreatEntityField().getLocation());
		return (int) Math.signum(firstRange - secondRange);
	}
}
