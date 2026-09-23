package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.system.SystemAI.
 * Decides when the ship's system should activate based on threat directions.
 */
public interface BattleNext_SystemAI {
	void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target);
}
