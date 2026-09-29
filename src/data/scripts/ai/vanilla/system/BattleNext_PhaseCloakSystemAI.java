package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.scripts.ai.vanilla.BattleNext_PhaseCloakShieldAI;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.PhaseCloakSystemAI.
 */
public class BattleNext_PhaseCloakSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_PhaseCloakShieldAI shieldAI;

	public BattleNext_PhaseCloakSystemAI(ShipAPI ship, ShipwideAIFlags flags,
			BattleNext_ThreatEvalAI threatEval, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.threatEval = threatEval;
		this.shieldAI = new BattleNext_PhaseCloakShieldAI(ship, flags, maneuverAPI, false);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		shieldAI.advance(amount, threatEval, missileDangerDir, collisionDangerDir, target);
	}

	public ShipSystemAPI getSystem() {
		return ship.getSystem();
	}

	public BattleNext_PhaseCloakShieldAI getPhaseCloakShieldAI() {
		return shieldAI;
	}
}
