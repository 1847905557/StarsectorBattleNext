package data.scripts.ai.vanilla.shields;

import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.ShieldAI.
 * Decides when a ship raises, angles and drops its shields.
 */
public interface BattleNext_ShieldAI {
	void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector, Vector2f collisionThreatDir, ShipAPI target);

	boolean hasFiringSolution();

	boolean shouldKeepShieldOn();

	BattleNext_FiringSolutionEval getEval();
}
