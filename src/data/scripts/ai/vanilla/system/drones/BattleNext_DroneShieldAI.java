package data.scripts.ai.vanilla.system.drones;

import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.shields.BattleNext_ShieldAI;
import data.scripts.ai.vanilla.system.BattleNext_PhaseCloakSystemAI;
import data.scripts.ai.vanilla.system.BattleNext_SystemAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.drones.DroneShieldAI.
 */
class BattleNext_DroneShieldAI implements BattleNext_ShieldAI {
	private final BattleNext_SystemAI systemAI;

	BattleNext_DroneShieldAI(BattleNext_DroneAI droneAI, BattleNext_SystemAI systemAI) {
		this.systemAI = systemAI;
	}

	@Override
	public boolean shouldKeepShieldOn() {
		return false;
	}

	@Override
	public boolean hasFiringSolution() {
		return systemAI instanceof BattleNext_PhaseCloakSystemAI phase
				&& phase.getPhaseCloakShieldAI().getEval().hasSolution();
	}

	@Override
	public BattleNext_FiringSolutionEval getEval() {
		return systemAI instanceof BattleNext_PhaseCloakSystemAI phase
				? phase.getPhaseCloakShieldAI().getEval() : null;
	}

	@Override
	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector,
			Vector2f collisionThreatDir, ShipAPI target) {
		systemAI.advance(amount, avoidanceVector, collisionThreatDir, target);
	}
}
