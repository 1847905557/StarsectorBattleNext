package data.scripts.ai.vanilla;

import data.scripts.ai.vanilla.maneuvers.BattleNext_Maneuver;

/**
 * Port of engine class com.fs.starfarer.combat.ai.D$o (deobfuscated result holder).
 * Returned by the threat evaluator: the maneuver that answers the current threat
 * and how long it should run.
 */
import data.scripts.battlesnext.util.annotation.PublicData;

/**
 * Pure data holder; the project rule allows public fields on PublicData types.
 */
@PublicData
public class BattleNext_ThreatResponse {
	public BattleNext_Maneuver maneuver;
	public float duration;

	public BattleNext_ThreatResponse(BattleNext_Maneuver maneuver, float duration) {
		this.maneuver = maneuver;
		this.duration = duration;
	}
}
