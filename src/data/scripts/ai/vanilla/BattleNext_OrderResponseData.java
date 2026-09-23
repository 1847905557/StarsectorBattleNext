package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_Maneuver;

/**
 * Port of engine class com.fs.starfarer.combat.ai.I$o (deobfuscated result holder).
 * Carries the answer of the order response module: a maneuver to run, its duration,
 * and an optional forced attack target.
 */
import data.scripts.battlesnext.util.annotation.PublicData;

/**
 * Pure data holder; the project rule allows public fields on PublicData types.
 */
@PublicData
public class BattleNext_OrderResponseData {
	public BattleNext_Maneuver maneuver;
	public float duration;
	public ShipAPI forcedTarget;

	// The decompiled engine class also stores a back-reference to its creating
	// OrderResponseModule in a field the decompiler did not declare. It is never
	// read by any ported code, so it is kept only for structural fidelity.
	public BattleNext_OrderResponseModule outer;

	public BattleNext_OrderResponseData(BattleNext_OrderResponseModule outer) {
		this.outer = outer;
	}
}
