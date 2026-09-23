package data.scripts.ai.vanilla.collisions;

/**
 * Port of engine class com.fs.starfarer.combat.ai.collisions.CollisionTimeRange.
 * A merged angular arc that is unsafe to head into, produced by the sweep-line.
 */
public class BattleNext_CollisionTimeRange {
	private float startAngle;
	private float width;

	public float getStartAngle() {
		return this.startAngle;
	}

	public void setStartAngle(float startAngle) {
		this.startAngle = startAngle;
	}

	public float getWidth() {
		return this.width;
	}

	public void setWidth(float width) {
		this.width = width;
	}
}
