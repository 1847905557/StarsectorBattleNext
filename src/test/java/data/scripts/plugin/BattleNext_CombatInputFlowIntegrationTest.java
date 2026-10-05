package data.scripts.plugin;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import data.scripts.tools.BattleNext_CombatInput;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_CombatInputFlowIntegrationTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@CsvSource({"false,false,false", "true,false,true", "true,true,false", "false,true,false"})
	void playerGateUsesActualLivingShipAndShuttleState(boolean alive, boolean shuttle, boolean playing) {
		ShipAPI player = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "isAlive" -> alive;
			case "isShuttlePod" -> shuttle;
			default -> null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) ->
				"getPlayerShip".equals(method) ? player : null);
		assertEquals(playing, BattleNext_CombatInput.isPlayerPlaying(engine));
	}

	@ParameterizedTest
	@ValueSource(strings = {"none", "command", "deployment", "dialog"})
	void sharedUiGateKeepsRttSceneInputWithTheUncoveredViewport(String overlay) {
		ShipAPI player = proxy(ShipAPI.class, (method, args) -> "isShuttlePod".equals(method));
		ViewportAPI viewport = proxy(ViewportAPI.class, (method, args) -> "isExternalControl".equals(method));
		CombatUIAPI ui = proxy(CombatUIAPI.class, (method, args) -> switch (method) {
			case "isShowingCommandUI" -> overlay.equals("command");
			case "isShowingDeploymentDialog" -> overlay.equals("deployment");
			default -> null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getPlayerShip" -> player;
			case "getViewport" -> viewport;
			case "getCombatUI" -> ui;
			case "isUIShowingDialog" -> overlay.equals("dialog");
			default -> null;
		});
		assertEquals(!overlay.equals("none"), BattleNext_CombatInput.isUiCovered(engine));
		assertEquals(overlay.equals("none"), BattleNext_RttPlugin.acceptsMouseInput(engine));
	}
}
