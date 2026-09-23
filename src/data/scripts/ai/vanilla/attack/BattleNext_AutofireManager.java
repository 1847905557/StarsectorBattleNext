package data.scripts.ai.vanilla.attack;

import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.attack.AutofireManager.
 * Decides which weapon groups stay on autofire.
 */
public interface BattleNext_AutofireManager {
	void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector);
}
