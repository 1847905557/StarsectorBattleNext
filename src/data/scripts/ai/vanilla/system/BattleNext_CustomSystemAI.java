package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import org.lwjgl.util.vector.Vector2f;

/**
 * Adapter for the engine's CUSTOM system AI branch.
 */
class BattleNext_CustomSystemAI implements BattleNext_SystemAI {
	private final ShipSystemAIScript script;

	BattleNext_CustomSystemAI(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags) {
		this.script = system.getSpecAPI().getAIScript();
		if (script == null) {
			throw new IllegalArgumentException("CUSTOM ship system AI has no aiScript: " + system.getSpecAPI().getId());
		}
		script.init(ship, ship.getSystem(), flags, Global.getCombatEngine());
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		script.advance(amount, missileDangerDir, collisionDangerDir, target);
	}
}
