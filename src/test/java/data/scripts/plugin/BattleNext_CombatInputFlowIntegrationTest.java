package data.scripts.plugin;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import data.scripts.tools.BattleNext_CombatInput;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

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

	@Test
	void bothPluginsShareFrameworkOrderAndSynchronizeBeforeTheirInputLoops() throws Exception {

		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
		List<String> framework = List.of("init", "processInputPreCoreControls", "advance", "updateControlState", "acceptsMouseInput");
		for (String name : List.of("BattleNext_FreeCameraPlugin", "BattleNext_RttPlugin")) {
			var result = parser.parse(Path.of("src/data/scripts/plugin", name + ".java"));
			assertTrue(result.isSuccessful(), result.getProblems().toString());
			var type = result.getResult().orElseThrow().getClassByName(name).orElseThrow();
			assertEquals(framework, type.getMethods().stream().map(MethodDeclaration::getNameAsString)
					.filter(framework::contains).distinct().toList(), name);
			MethodDeclaration input = type.getMethodsByName("processInputPreCoreControls").get(0);
			assertTrue(input.getBody().orElseThrow().getStatement(0).isIfStmt(), name);
			var synchronization = input.findAll(MethodCallExpr.class).stream()
					.filter(call -> call.getNameAsString().equals("updateControlState")).findFirst().orElseThrow();
			ForEachStmt loop = input.findFirst(ForEachStmt.class).orElseThrow();
			assertTrue(synchronization.getBegin().orElseThrow().line < loop.getBegin().orElseThrow().line, name);
			MethodDeclaration advance = type.getMethodsByName("advance").get(0);
			assertTrue(advance.findAll(MethodCallExpr.class).stream()
					.anyMatch(call -> call.getNameAsString().equals("updateControlState")), name);
			for (String gate : List.of("isPlayerPlaying", "isUiCovered")) {
				assertTrue(type.findAll(MethodCallExpr.class).stream().anyMatch(call -> call.getNameAsString().equals(gate)
						&& call.getScope().map(Object::toString).orElse("").equals("BattleNext_CombatInput")), name + "/" + gate);
			}
		}
	}
}
