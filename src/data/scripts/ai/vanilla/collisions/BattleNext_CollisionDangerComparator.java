package data.scripts.ai.vanilla.collisions;

import java.util.Comparator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.collisions.CollisionDangerComparator.
 * Orders danger entries by angle for the sweep-line merge.
 * The decompiled engine body exposes the comparison as compareByAngle without a
 * compare() override, which cannot compile; the port restores compare() with the
 * same ordering.
 */
class BattleNext_CollisionDangerComparator implements Comparator<BattleNext_CollisionDangerEntry> {
	BattleNext_CollisionDangerComparator(BattleNext_CollisionAnalysisModule analysisModule) {
		this.analysisModule = analysisModule;
	}

	private BattleNext_CollisionAnalysisModule analysisModule;

	public BattleNext_CollisionAnalysisModule getAnalysisModule() {
		return this.analysisModule;
	}

	@Override
	public int compare(BattleNext_CollisionDangerEntry first, BattleNext_CollisionDangerEntry second) {
		return (int) Math.signum(first.getAngle() - second.getAngle());
	}
}
