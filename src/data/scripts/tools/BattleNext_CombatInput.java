package data.scripts.tools;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class BattleNext_CombatInput {

	private BattleNext_CombatInput() {

	}

	public static boolean isPlayerPlaying(CombatEngineAPI engine) {
		ShipAPI player = engine.getPlayerShip();
		return player.isAlive() && !player.isShuttlePod();
	}

	public static boolean isUiCovered(CombatEngineAPI engine) {
		return engine.getCombatUI().isShowingCommandUI()
				|| engine.getCombatUI().isShowingDeploymentDialog()
				|| engine.isUIShowingDialog();
	}
}
