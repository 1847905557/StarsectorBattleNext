package data.simulator;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class BattleNext_SimulatorServiceTest extends BattleNext_SimulatorServiceFixture {

	@Test
	void failedWorkerStartupCompletesTasksAndContinuesTheFifoQueue() throws Exception {
		BattleNext_SimulationTask first = service.submit(BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults()));
		BattleNext_SimulationTask second = service.submit(BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults()));
		long deadline = System.nanoTime() + 15000000000L;
		while (second.getResult() == null && System.nanoTime() < deadline) {
			Thread.sleep(20L);
		}
		assertNotNull(first.getResult());
		assertNotNull(second.getResult());
		assertEquals(BattleNext_SimulationResult.Outcome.FAILED, first.getResult().outcome());
		assertEquals(BattleNext_SimulationResult.Outcome.FAILED, second.getResult().outcome());
		assertEquals(BattleNext_SimulationTask.State.FINISHED, second.getState());
		assertTrue(first.getResult().diagnostic().contains("cold start"));
		service.close();
		for (BattleNext_SimulationTask task : List.of(first, second)) {
			assertEquals(task.getRequest().id(), task.getResult().id());
			assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
			assertTrue(task.getResult().diagnostic().contains("cold start"));
		}
		assertNull(readField("process"));
		assertNull(readField("channel"));
	}

	@Test
	void quickBattleActionsSubmitIndependentFrozenFifoTasksAndOutliveTheWidget() throws Exception {

		quickBattles("alpha,beta,-1,1.5\n# alpha,beta,2,2\nmissing,beta,0,1\n");
		BattleNext_Settings.save(false, true, false, false, 123f, 1f, 3f, 50);
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(900f, 500f, new BaseCustomUIPanelPlugin());
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(5000);
			Path runtime = attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			widget.attach(root);
			BattleNext_QuickBattleDisplay display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
			List<CustomPanelAPI> rows = tooltips.get(display.getContent()).getComponents().stream().map(CustomPanelAPI.class::cast).toList();
			String invalid = "simulator:quick:" + display.getRows().get(2).getModel().getId();
			rows.get(2).getPlugin().buttonPressed(invalid);
			assertEquals(BattleNext_SimulatorService.Phase.IDLE, BattleNext_SimulatorService.status().phase());
			String action = "simulator:quick:" + display.getRows().get(1).getModel().getId();
			assertFalse(display.getRows().get(1).getModel().isEnabled());
			rows.get(1).getPlugin().buttonPressed(action);
			BattleNext_SimulationRequest first = BattleNext_SimulationRequest.fromJson(peer.receive().getJSONObject("payload"));
			rows.get(1).getPlugin().buttonPressed(action);
			assertEquals(1, BattleNext_SimulatorService.status().queued());
			assertEquals(first, BattleNext_SimulatorService.status().current());
			assertEquals(BattleNext_SimulationRequest.Kind.MATCH, first.kind());
			assertEquals(2, first.source().getInt("actualMap"));
			assertEquals(2d, first.source().getJSONObject("match").getDouble("mapScale"));
			assertEquals(123f, first.config().assaultAfterSeconds());
			assertEquals(3600f, first.config().simulationSeconds());
			assertEquals(120f, first.config().executionSeconds());
			peer.send("STATUS", new JSONObject().put("id", first.id()).put("state", "RUNNING"));
			long deadline = System.nanoTime() + 5000000000L;
			while (BattleNext_SimulatorService.status().phase() != BattleNext_SimulatorService.Phase.RUNNING && System.nanoTime() < deadline) {
				Thread.sleep(5L);
			}
			widget.refreshStatus();
			LabelAPI status = (LabelAPI) getField(widget, "statusLabel");
			assertTrue(status.getText().contains(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_simulator_running")));
			TooltipMakerAPI statusContent = (TooltipMakerAPI) getField(widget, "statusContent");
			assertEquals(BattleNext_PageLayout.FOOTER_HEIGHT, statusContent.getHeightSoFar());
			TooltipMakerAPI details = root.createUIElement(480f, 0f, false);
			attachedTooltips.get(status).createTooltip(details, false, null);
			assertTrue(labels.stream().anyMatch(text -> text.contains(first.id())));
			assertTrue(labels.stream().anyMatch(text -> text.contains(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_simulator_map"), 2, 2, 2f))));
			widget.close();
			assertTrue(Files.exists(runtime));
			BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
			report.setId(first.id());
			report.setName("Saved Match simulation");
			peer.send("RESULT", new BattleNext_SimulationResult(first.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
					report.toJson().toString(), 10f, 100000000L, 600L, 1024L, "").toJson());
			peer.send("READY", new JSONObject());
			BattleNext_SimulationRequest second = BattleNext_SimulationRequest.fromJson(peer.receive().getJSONObject("payload"));
			assertNotEquals(first.id(), second.id());
			assertJsonEquals(first.source(), second.source());
			peer.send("RESULT", new BattleNext_SimulationResult(second.id(), BattleNext_SimulationResult.Outcome.FAILED,
					"", 0f, 0L, 0L, 0L, "Injected simulation failure").toJson());
			peer.send("READY", new JSONObject());
			deadline = System.nanoTime() + 5000000000L;
			while ((BattleNext_SimulatorService.status().result() == null || readField("active") != null) && System.nanoTime() < deadline) {
				Thread.sleep(5L);
			}
			assertEquals(second.id(), BattleNext_SimulatorService.status().result().id());
			BattleNext_SimulatorService.advance();
			BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
			reports.reload();
			assertEquals("Saved Match simulation", reports.get(first.id()).getName());
			assertEquals(second.id(), BattleNext_SimulatorService.status().result().id());
			widget.attach(root);
			assertTrue(((LabelAPI) getField(widget, "statusLabel")).getText().contains(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_simulator_finished")));
			status = (LabelAPI) getField(widget, "statusLabel");
			attachedTooltips.get(status).createTooltip(root.createUIElement(480f, 0f, false), false, null);
			assertTrue(labels.stream().anyMatch(text -> text.contains("Injected simulation failure")));
			service.close();
			display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
			rows = tooltips.get(display.getContent()).getComponents().stream().map(CustomPanelAPI.class::cast).toList();
			rows.get(1).getPlugin().buttonPressed(action);
			assertEquals("IllegalStateException: Simulator service is closed", browser.getStatusError());
			assertEquals("", browser.getSelectedId());
			assertEquals("alpha", display.getRows().get(1).getField(BattleNext_QuickBattleDisplay.Column.PLAYER).getText());
			assertEquals(second.id(), BattleNext_SimulatorService.status().result().id());
			widget.close();
			assertFalse(router.getSession().hasInputState());
		} finally {
			widget.close();
		}
	}

	@Test
	void resourceReloadCommitsTheRoundBeforeRestartingAtTheNextRoundBoundary() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(10);
			OwnedProcess process = new OwnedProcess();
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), process);
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			BattleNext_SimulationTask task = service.submit(BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
			BattleNext_SimulationRequest round = nextRequest(peer);
			withServiceMetadata(BattleNext_SimulatorService::resourcesReloaded);
			completeRound(peer, round, "player");
			awaitTournamentState(task, BattleNext_SimulationTask.State.PAUSED);
			assertFalse(process.isAlive());
			BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
			instances.reload();
			assertEquals(round.id(), task.getStartedTournament().getMatches().get(0).getRounds().get(0).getReportId());
			assertNotEquals(round.id(), task.getCurrentRound().id());
			assertTrue(task.getImportError().contains("cold start"));
			assertEquals(1, task.getStartedTournament().getMatches().get(0).getRounds().size());
			assertEquals("alpha", task.getStartedTournament().getMatches().get(0).getRounds().get(0).getWinnerFleetId());
			assertEquals(new BattleNext_SimulationTask.Metrics(10f, 100000000L, 600L, 1024L), task.getMetrics());
			assertNull(readField("process"));
			assertNull(readField("channel"));
			assertNull(readField("active"));
		}
	}

	@Test
	void brokenShutdownChannelStillTerminatesTheOwnedProcessAndRetainsItsLogDirectory() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket peer = listener.accept()) {
			BattleNext_SimulatorProtocol channel = new BattleNext_SimulatorProtocol(client, "session");
			OwnedProcess process = new OwnedProcess();
			Path runtime = attachWorker(channel, process);
			channel.close();
			service.close();
			assertFalse(process.isAlive());
			assertTrue(Files.exists(runtime));
			assertNull(readField("process"));
			assertNull(readField("channel"));
		}
	}

	@Test
	void closingAnExecutingServiceCompletesActiveAndQueuedCancellationBeforeReleasingTheWorker() throws Exception {

		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(5000);
			BattleNext_SimulatorProtocol channel = new BattleNext_SimulatorProtocol(client, "session");
			OwnedProcess process = new OwnedProcess();
			Path runtime = attachWorker(channel, process);
			BattleNext_SimulationTask active = service.submit(BattleNext_SimulationRequest.fromVariants(List.of("player"), List.of("enemy"), BattleNext_SimulationConfig.defaults()));
			BattleNext_SimulationTask queued = service.submit(BattleNext_SimulationRequest.fromVariants(List.of("player"), List.of("enemy"), BattleNext_SimulationConfig.defaults()));
			assertEquals(active.getRequest().id(), peer.receive().getJSONObject("payload").getString("id"));
			Thread supervisor = (Thread) readField("supervisor");
			service.close();
			assertFalse(supervisor.isAlive());
			assertFalse(process.isAlive());
			assertTrue(Files.exists(runtime));
			assertEquals("SHUTDOWN", peer.receive().getString("type"));
			for (BattleNext_SimulationTask task : List.of(active, queued)) {
				assertEquals(BattleNext_SimulationResult.Outcome.CANCELLED, task.getResult().outcome());
				assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
				assertEquals(task.getRequest().id(), task.getResult().id());
				assertEquals("Simulator service closed", task.getResult().diagnostic());
			}
		}
	}

}
