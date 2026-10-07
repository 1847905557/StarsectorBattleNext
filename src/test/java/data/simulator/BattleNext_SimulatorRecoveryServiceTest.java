package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.page.BattleNext_TournamentPage;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.widget.BattleNext_StatusBar;
import com.fs.starfarer.api.ui.LabelAPI;
import data.test.fixture.BattleNext_TestTextCatalog;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class BattleNext_SimulatorRecoveryServiceTest extends BattleNext_SimulatorServiceFixture {

	@Test
	void simulatorSessionStartsEmptyAndReportsRemainIndependent() throws Exception {
		BattleNext_SimulationTask completed = completedTaskThroughService("Saved result");
		BattleNext_SimulatorService.advance();
		assertTrue(completed.isRetained());
		quickBattles("alpha,beta,-1,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		var request = BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"),
				sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults());
		BattleNext_SimulationTask task = pausedTournamentThroughService(request);
		assertTrue(sources.getTournamentStore().isLocked("cup"));
		assertEquals(task.getStartedTournament().getId(), BattleNext_SimulatorService.simulatedInstances().get(0).getId());
		assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + request.id() + ".json")));
		var page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (name, action) -> action.run());
		page.attach(panel(1200f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("tournament:select:cup");
		var editor = (BattleNext_TournamentEditor) getField(page, "editor");
		assertTrue(editor.isReadOnly());
		editor.buttonPressed("instance:toggle:" + request.id());
		assertTrue(buttons.get("instance:delete:" + request.id()).get());
		page.close();
		service.close();
		singleton.set(null, null);
		createOwnedService();
		assertNull(BattleNext_SimulatorService.tournament(request.id()));
		assertTrue(BattleNext_SimulatorService.simulatedInstances().isEmpty());
		assertFalse(sources.getTournamentStore().isLocked("cup"));
		assertNull(readField("process"));
		assertNull(readField("supervisor"));
		var reports = new BattleNext_BattleReportStore();
		reports.reload();
		assertEquals("Saved result", reports.get(completed.getRequest().id()).getName());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void bothMonitorHostsImportCompletedReportsIdempotentlyAndUseZeroRetention(boolean campaign) throws Exception {

		BattleNext_SimulatorMonitor combatMonitor = new BattleNext_SimulatorMonitor();
		combatMonitor.init(proxy(CombatEngineAPI.class, (method, args) -> null));
		assertSame(service, BattleNext_SimulatorService.getInstance());
		BattleNext_SimulatorCampaignMonitor campaignMonitor = new BattleNext_SimulatorCampaignMonitor();
		assertFalse(campaignMonitor.isDone());
		assertTrue(campaignMonitor.runWhilePaused());
		Runnable advance = campaign ? () -> campaignMonitor.advance(0f) : () -> combatMonitor.advance(0f, List.of());
		var manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		manager.open();
		BattleNext_SimulationTask task = completedTaskThroughService("Saved simulation");
		advance.run();
		manager.advance(0f);
		var status = (BattleNext_StatusBar) getField(manager, "statusBar");
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_report_updated"), task.getRequest().id()),
				((LabelAPI) getField(status, "statusLabel")).getText());
		manager.buttonPressed("close");
		assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
		assertTrue(task.isRetained());
		BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
		reports.reload();
		assertEquals(task.getRequest().id(), reports.getAll().get(0).getId());
		service.close();
		singleton.set(null, null);
		createOwnedService();
		advance.run();
		reports.reload();
		assertEquals(List.of(task.getRequest().id()), reports.getAll().stream().map(BattleNext_BattleReportModel::getId).toList());
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 0);
		BattleNext_SimulationTask discarded = completedTaskThroughService("Not retained");
		advance.run();
		assertFalse(discarded.isRetained());
		assertEquals("", BattleNext_BattleSignals.consumePendingResult());
		reports.reload();
		assertTrue(reports.getAll().isEmpty());
	}

	@Test
	void reportImportFailureKeepsTheCompleteResultAndSupportsExplicitRetry() throws Exception {

		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults());
		BattleNext_BattleReportModel expectedReport = new BattleNext_BattleReportModel();
		expectedReport.setId(request.id());
		expectedReport.setName("Retry simulation");
		expectedReport.setCreatedAt(1234L);
		expectedReport.setDurationMillis(10000L);
		expectedReport.setWinner("player");
		BattleNext_MemberReport player = new BattleNext_MemberReport("local_wolf", "wolf", "Player 50%");
		player.addDamageDealt("weapon", "laser", 1d, 2d, 3d, 4d);
		player.setFinalState(0.75f, 0.7f, "alive");
		BattleNext_MemberReport enemy = new BattleNext_MemberReport("local_lasher", "lasher", "Enemy");
		enemy.addDamageTaken("weapon", "laser", 1d, 2d, 3d, 4d);
		enemy.setFinalState(0f, 0.5f, "destroyed");
		expectedReport.addPlayerMember(player);
		expectedReport.addEnemyMember(enemy);
		BattleNext_SimulationResult expectedResult = new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				expectedReport.toJson().toString(), 10f, 100000000L, 600L, 1024L, "");
		BattleNext_SimulationTask task = completedTaskThroughService(request, expectedResult);
		BattleNext_BattleReportModel existing = new BattleNext_BattleReportModel();
		existing.setId("existing");
		existing.setName("Existing report");
		BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
		reports.reload();
		reports.save(existing);
		String existingJson = existing.toJson().toString();
		BattleNext_BattleSignals.consumePendingResult();
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("loadJSON")) throw new IllegalStateException("Injected report read failure");
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		try {
			BattleNext_SimulatorService.advance();
		} finally {
			Global.setSettings(settings);
		}
		assertEquals(BattleNext_SimulationTask.State.IMPORT_FAILED, task.getState());
		BattleNext_BattleReportModel resultReport = task.getResult().report();
		assertEquals(task.getRequest().id(), resultReport.getId());
		assertEquals("Retry simulation", resultReport.getName());
		assertJsonEquals(expectedReport.toJson(), resultReport.toJson());
		assertEquals(expectedResult, task.getResult());
		assertEquals(List.of(task), service.getFailedImports());
		assertTrue(task.getImportError().contains("Injected report read failure"));
		assertEquals("", BattleNext_BattleSignals.consumePendingResult());
		reports.reload();
		assertEquals(existingJson, reports.get("existing").toJson().toString());
		assertNull(reports.get(request.id()));
		task.retryImport();
		BattleNext_SimulatorService.advance();
		assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
		assertTrue(service.getFailedImports().isEmpty());
		reports.reload();
		BattleNext_BattleReportModel imported = reports.get(task.getRequest().id());
		assertNotNull(imported);
		assertEquals(resultReport.toJson().toString(), imported.toJson().toString());
		assertEquals(existingJson, reports.get("existing").toJson().toString());
		assertEquals(expectedResult, task.getResult());
		assertEquals(10f, task.getResult().simulationSeconds());
		assertEquals(100000000L, task.getResult().executionNanos());
		assertEquals(600L, task.getResult().steps());
		assertEquals(1024L, task.getResult().peakHeapBytes());
		assertEquals(request.id(), BattleNext_BattleSignals.consumePendingResult());
		task.retryImport();
		BattleNext_SimulatorService.advance();
		assertEquals(expectedResult, task.getResult());
		assertEquals("", BattleNext_BattleSignals.consumePendingResult());
	}

	private BattleNext_SimulationTask completedTaskThroughService(String name) throws Exception {
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults());
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(request.id());
		report.setName(name);
		report.setWinner("player");
		return completedTaskThroughService(request, new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 10f, 100000000L, 600L, 1024L, ""));
	}

	private BattleNext_SimulationTask pausedTournamentThroughService(BattleNext_SimulationRequest request) throws Exception {

		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(10);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			BattleNext_SimulationTask task = service.submit(request);
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			BattleNext_SimulationRequest round = nextRequest(peer);
			task.pause();
			JSONObject cancellation = peer.receive();
			assertEquals("CANCEL", cancellation.getString("type"));
			assertEquals(round.id(), cancellation.getJSONObject("payload").getString("id"));
			peer.send("RESULT", new BattleNext_SimulationResult(round.id(), BattleNext_SimulationResult.Outcome.CANCELLED,
					"", 0f, 0L, 0L, 0L, "Paused").toJson());
			peer.send("READY", new JSONObject());
			awaitTournamentState(task, BattleNext_SimulationTask.State.PAUSED);
			assertNull(readField("active"));
			return task;
		}
	}

	private BattleNext_SimulationTask completedTaskThroughService(BattleNext_SimulationRequest request, BattleNext_SimulationResult result) throws Exception {

		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(10);
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			BattleNext_SimulationTask task = service.submit(request);
			BattleNext_SimulationRequest submitted = nextRequest(peer);
			assertEquals(request, submitted);
			peer.send("RESULT", result.toJson());
			peer.send("READY", new JSONObject());
			long deadline = System.nanoTime() + 5000000000L;
			while ((task.getResult() == null || readField("active") != null) && System.nanoTime() < deadline) {
				Thread.sleep(10L);
			}
			assertNotNull(task.getResult());
			assertNull(readField("active"));
			assertEquals(result, task.getResult());
			assertEquals(task, importQueue().peek());
			return task;
		}
	}

}
