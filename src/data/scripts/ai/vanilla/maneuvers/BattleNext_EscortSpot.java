package data.scripts.ai.vanilla.maneuvers;

import data.scripts.battlesnext.util.annotation.PublicData;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortSpot.
 * One candidate escort station around the escorted ship.
 * The engine constructor is private; the port widens it to package-private
 * because the escort maneuver builds spots directly. This is a residual
 * artifact of the decompilation, annotated where the maneuver constructs spots.
 */

@PublicData
public class BattleNext_EscortSpot {
	public Vector2f location = new Vector2f();
	public int index = 0;
	public int sequenceIndex;
	public final float angle;

	BattleNext_EscortSpot(Vector2f location, float angle) {
		this.location = location;
		this.angle = angle;
	}
}
