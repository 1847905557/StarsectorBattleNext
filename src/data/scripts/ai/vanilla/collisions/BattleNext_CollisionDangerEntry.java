package data.scripts.ai.vanilla.collisions;

/**
 * Port of engine class com.fs.starfarer.combat.ai.collisions.CollisionDangerEntry.
 * Sweep-line endpoint of one collision segment during danger arc merging.
 */
public class BattleNext_CollisionDangerEntry {
	public static final int DANGER_START = 1;
	public static final int DANGER_END = 0;
	private BattleNext_CollisionCheckSegment segment;
	private int entryType;
	private float angle;

	public BattleNext_CollisionDangerEntry(BattleNext_CollisionCheckSegment segment, int entryType, float angle) {
		this.segment = segment;
		this.entryType = entryType;
		this.angle = angle;
	}

	public float getAngle() {
		return this.angle;
	}

	public boolean isDangerStart() {
		return this.entryType == 1;
	}

	public boolean isDangerEnd() {
		return this.entryType == 0;
	}

	public BattleNext_CollisionCheckSegment getSegment() {
		return this.segment;
	}
}
