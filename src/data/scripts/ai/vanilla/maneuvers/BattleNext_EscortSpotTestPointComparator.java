package data.scripts.ai.vanilla.maneuvers;

import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import java.util.Comparator;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortSpotTestPointComparator.
 * Orders escort spots by the closer of two test points, with a light pull
 * toward the own ship.
 * The decompiled engine body exposes the comparison as compareByTestPointDistance
 * without a compare() override, which cannot compile; the port restores
 * compare() with the same ordering.
 */
class BattleNext_EscortSpotTestPointComparator implements Comparator<BattleNext_EscortSpot> {
	BattleNext_EscortSpotTestPointComparator(BattleNext_EscortTargetManeuverV3 outerManeuver, Vector2f test1, Vector2f test2) {
		this.outerManeuver = outerManeuver;
		this.test1 = test1;
		this.test2 = test2;
	}

	private BattleNext_EscortTargetManeuverV3 outerManeuver;
	private Vector2f test1;
	private Vector2f test2;

	public BattleNext_EscortTargetManeuverV3 getOuterManeuver() {
		return this.outerManeuver;
	}

	public Vector2f getTest1() {
		return this.test1;
	}

	public Vector2f getTest2() {
		return this.test2;
	}

	@Override
	public int compare(BattleNext_EscortSpot first, BattleNext_EscortSpot second) {
		if (this.outerManeuver.getThreatEntityField() != null && first.index == second.index) {
			float firstScore = Math.min(BattleNext_ShipAIHelper.getDistance(first.location, this.test1), BattleNext_ShipAIHelper.getDistance(first.location, this.test2));
			float secondScore = Math.min(BattleNext_ShipAIHelper.getDistance(second.location, this.test1), BattleNext_ShipAIHelper.getDistance(second.location, this.test2));
			firstScore += BattleNext_ShipAIHelper.getDistance(this.outerManeuver.getShipField().getLocation(), first.location) * 0.01F;
			secondScore += BattleNext_ShipAIHelper.getDistance(this.outerManeuver.getShipField().getLocation(), second.location) * 0.01F;
			return (int) Math.signum(firstScore - secondScore);
		} else {
			return first.index == second.index ? first.sequenceIndex - second.sequenceIndex : first.index - second.index;
		}
	}
}
