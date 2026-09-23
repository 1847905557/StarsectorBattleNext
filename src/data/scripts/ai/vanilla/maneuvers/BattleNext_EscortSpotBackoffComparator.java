package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import java.util.Comparator;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortSpotBackoffComparator.
 * Orders escort spots so back-off picks the free spot farthest from the threat.
 * The decompiled engine body exposes the comparison as compareByBackoffDistance
 * without a compare() override, which cannot compile; the port restores
 * compare() with the same ordering.
 */
class BattleNext_EscortSpotBackoffComparator implements Comparator<BattleNext_EscortSpot> {
	BattleNext_EscortSpotBackoffComparator(BattleNext_EscortTargetManeuverV3 outerManeuver, Vector2f backOffAwayFrom) {
		this.outerManeuver = outerManeuver;
		this.backOffAwayFrom = backOffAwayFrom;
	}

	private BattleNext_EscortTargetManeuverV3 outerManeuver;
	private Vector2f backOffAwayFrom;

	public BattleNext_EscortTargetManeuverV3 getOuterManeuver() {
		return this.outerManeuver;
	}

	public Vector2f getBackOffAwayFrom() {
		return this.backOffAwayFrom;
	}

	@Override
	public int compare(BattleNext_EscortSpot first, BattleNext_EscortSpot second) {
		if (this.outerManeuver.getThreatEntityField() != null && first.index == second.index) {
			float firstRange = BattleNext_ShipAIHelper.getDistance(first.location, this.backOffAwayFrom);
			float secondRange = BattleNext_ShipAIHelper.getDistance(second.location, this.backOffAwayFrom);
			return (int) Math.signum(firstRange - secondRange);
		} else {
			return first.index == second.index ? first.sequenceIndex - second.sequenceIndex : first.index - second.index;
		}
	}
}
