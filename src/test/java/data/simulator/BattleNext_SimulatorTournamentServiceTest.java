package data.simulator;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.report.BattleNext_BattleReportStore;
import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.page.BattleNext_BattleReportPage;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class BattleNext_SimulatorTournamentServiceTest extends BattleNext_SimulatorServiceFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void budgetDrawCommitsOnceAndContinuesTheTournamentWithAnOptionalReport(boolean hasReport) throws Exception {
		quickBattles("alpha,beta,0,1\n");
		var sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		var configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		sources.getTournamentStore().save(configuration);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(100);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			var task = service.submit(BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"),
					sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
			var first = nextRequest(peer);
			var report = new BattleNext_BattleReportModel();
			report.setId(first.id());
			report.setWinner("draw");
			peer.send("RESULT", new BattleNext_SimulationResult(first.id(), BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED,
					hasReport ? report.toJson().toString() : "", 10f, 100L, 600L, 1024L, "Execution budget exhausted").toJson());
			peer.send("READY", new JSONObject());
			var second = nextRequest(peer);
			assertNotEquals(first.id(), second.id());
			var match = task.getStartedTournament().getMatches().get(0);
			assertEquals(1, match.getRounds().size());
			assertEquals("", match.getRounds().get(0).getWinnerFleetId());
			assertEquals(hasReport ? first.id() : "", match.getRounds().get(0).getReportId());
			assertEquals(0, match.getPlayerWins());
			assertEquals(0, match.getEnemyWins());
			assertEquals(first.id(), task.getRoundResults().get(0).id());
			assertEquals("Execution budget exhausted", task.getRoundResults().get(0).diagnostic());
			BattleNext_SimulatorService.advance();
			assertEquals(1, task.getStartedTournament().getMatches().get(0).getRounds().size());
			var reports = new BattleNext_BattleReportStore();
			reports.reload();
			if (hasReport) {
				assertEquals("draw", reports.get(first.id()).getWinner());
			} else {
				assertNull(reports.get(first.id()));
			}
			completeRound(peer, second, "player");
			awaitTournamentState(task, BattleNext_SimulationTask.State.FINISHED);
			assertEquals(2, task.getStartedTournament().getMatches().get(0).getRounds().size());
			assertEquals("alpha", task.getStartedTournament().getChampionFleetId());
		}
	}

	@Test
	void tournamentSubmissionRereadsAssetsAndReturnsQualificationWithoutCreatingATask() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		var sources = new BattleNext_BattleSourceProvider();
		sources.reloadTournamentCatalog();
		var configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		sources.getTournamentStore().save(configuration);
		var request = BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults());
		write("tournament/beta/fleet_data.json", "{broken");
		var expected = sources.checkTournament("cup", List.of("alpha", "beta"));
		var rejected = assertThrows(BattleNext_BattleValidation.Rejection.class, () -> service.submit(request));
		assertEquals(expected, rejected.getResult());
		assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + request.id() + ".json")));
		var instances = new BattleNext_StartedTournamentStore();
		instances.reload();
		assertTrue(instances.getAll().isEmpty());
		assertNull(readField("supervisor"));
	}

	@Test
	void independentTournamentTasksShareSourceAndKeepSeparateMemoryInstances() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		BattleNext_SimulationTask first = service.submit(BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
		BattleNext_SimulationTask second = service.submit(BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
		assertNotEquals(first.getRequest().id(), second.getRequest().id());
		first.pause();
		second.pause();
		service.close();
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		instances.reload();
		assertEquals(2, BattleNext_SimulatorService.simulatedInstances().size());
		assertTrue(instances.getAll().isEmpty());
		assertEquals(first.getRequest().id(), first.getStartedTournament().getId());
		assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + first.getRequest().id() + ".json")));
		assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + second.getRequest().id() + ".json")));
	}

	@Test
	void tournamentFifoPauseAndResumeKeepRoundIdentityMapAndUseCurrentAssets() throws Exception {
		quickBattles("alpha,beta,-1,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		browser.select("cup");
		browser.template(configuration).select(0, "alpha");
		browser.template(configuration).select(1, "beta");
		CustomPanelAPI root = panel(1200f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		widget.attach(root);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(100);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			BattleNext_TournamentDisplay template = (BattleNext_TournamentDisplay) getField(widget, "tournamentDisplay");
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			plugins.get((CustomPanelAPI) getField(template, "viewport")).buttonPressed("simulator:tournament:cup");
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedInstanceId());
			assertEquals(List.of("alpha", "beta"), task.getRequest().tournamentFleetIds());
			assertEquals(List.of("alpha", "beta"), task.getStartedTournament().getFleetIds());
			BattleNext_TournamentDisplay display = (BattleNext_TournamentDisplay) getField(widget, "tournamentDisplay");
			BattleNext_TournamentView chart = (BattleNext_TournamentView) getField(display, "chart");
			chart.zoomAt(90f, 90f, 2);
			chart.panBy(8f, 12f);
			BattleNext_TournamentDisplay.ViewState browseState = display.captureState();
			BattleNext_SimulationRequest first = nextRequest(peer);
			completeRound(peer, first, "player");
			BattleNext_SimulationRequest second = nextRequest(peer);
			widget.refreshStatus();
			assertSame(display, getField(widget, "tournamentDisplay"));
			assertSame(chart, getField(display, "chart"));
			assertEquals(browseState, display.captureState());
			assertEquals(1, ((BattleNext_StartedTournament) getField(display, "started")).getMatches().get(0).getRounds().size());
			assertEquals(1, task.getStartedTournament().getMatches().get(0).getRounds().size());
			int map = second.source().getInt("actualMap");
			task.pause();
			assertEquals("CANCEL", peer.receive().getString("type"));
			peer.send("RESULT", new BattleNext_SimulationResult(second.id(), BattleNext_SimulationResult.Outcome.CANCELLED,
					"", 0f, 0L, 0L, 0L, "Paused").toJson());
			peer.send("READY", new JSONObject());
			awaitTournamentState(task, BattleNext_SimulationTask.State.PAUSED);
			sources.getFleetStore().reload();
			sources.getFleetStore().get("beta").setName("Changed before resume");
			sources.getFleetStore().save(sources.getFleetStore().get("beta"));
			task.resume();
			BattleNext_SimulationRequest resumed = nextRequest(peer);
			assertEquals(second.id(), resumed.id());
			assertEquals(map, resumed.source().getInt("actualMap"));
			assertEquals("Changed before resume", resumed.source().getJSONObject("player").getJSONObject("definition").getString("name"));
			completeRound(peer, resumed, "enemy");
			awaitTournamentState(task, BattleNext_SimulationTask.State.FINISHED);
			assertTrue(task.getStartedTournament().isCompleted());
			assertFalse(task.getResult().startedTournamentJson().isEmpty());
			BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
			instances.reload();
			assertEquals(2, task.getStartedTournament().getMatches().get(0).getRounds().size());
			assertTrue(instances.getActiveId().isEmpty());
			widget.refreshStatus();
			assertSame(chart, getField(display, "chart"));
			assertTrue(((BattleNext_StartedTournament) getField(display, "started")).isCompleted());
			BattleNext_BattleReportPage reports = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (label, action) -> action.run());
			reports.attach(panel(1200f, 900f, new BaseCustomUIPanelPlugin()));
			reports.buttonPressed("report:report:" + first.id());
			assertTrue(buttons.get("save").get());
			assertTrue(buttons.get("delete").get());
			reports.close();
		}
		widget.close();
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void acquiredRoundStaysInMemoryUntilReportCommitAndRetryCountsItOnce() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		sources.getTournamentStore().save(configuration);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(100);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			BattleNext_SimulationTask task = service.submit(BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			BattleNext_SimulationRequest round = nextRequest(peer);
			BattleNext_BattleReportModel inputReport = new BattleNext_BattleReportModel();
			inputReport.setId(round.id());
			inputReport.setName("Worker report 50%");
			inputReport.setCreatedAt(1234L);
			inputReport.setDurationMillis(10000L);
			inputReport.setWinner("player");
			BattleNext_MemberReport player = new BattleNext_MemberReport("parent_variant", "parent", "Player");
			player.addDamageDealt("weapon", "laser", 1d, 2d, 3d, 4d);
			player.setFinalState(0.75f, 0.7f, "alive");
			BattleNext_MemberReport enemy = new BattleNext_MemberReport("child_variant", "child", "Enemy");
			enemy.addDamageTaken("weapon", "laser", 1d, 2d, 3d, 4d);
			enemy.setFinalState(0f, 0.5f, "destroyed");
			inputReport.addPlayerMember(player);
			inputReport.addEnemyMember(enemy);
			BattleNext_SimulationResult received = new BattleNext_SimulationResult(round.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
					inputReport.toJson().toString(), 10f, 100000000L, 600L, 1024L, "");
			BattleNext_BattleSignals.consumePendingResult();
			Path obstruction = directory.resolve("tournament/_reports/" + round.id() + ".json");
			Files.createDirectories(obstruction);
			peer.send("RESULT", received.toJson());
			peer.send("READY", new JSONObject());
			awaitTournamentState(task, BattleNext_SimulationTask.State.IMPORT_FAILED);
			assertEquals(round.id(), task.pendingRound().id());
			assertEquals(received, task.pendingRound());
			assertTrue(task.getStartedTournament().getMatches().get(0).getRounds().isEmpty());
			assertFalse(task.getStartedTournament().isCompleted());
			assertEquals(new BattleNext_SimulationTask.Metrics(0f, 0L, 0L, 0L), task.getMetrics());
			assertFalse(BattleNext_BattleSignals.consumePendingResult());
			BattleNext_SimulatorService.advance();
			assertEquals(received, task.pendingRound());
			Files.delete(obstruction);
			BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
			instances.reload();
			assertTrue(task.getStartedTournament().getMatches().get(0).getRounds().isEmpty());
			BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
			reports.reload();
			assertNull(reports.get(round.id()));
			task.retryImport();
			awaitTournamentState(task, BattleNext_SimulationTask.State.FINISHED);
			instances.reload();
			BattleNext_StartedTournament completed = task.getStartedTournament();
			assertTrue(completed.isCompleted());
			assertEquals("alpha", completed.getChampionFleetId());
			assertEquals(1, completed.getMatches().get(0).getRounds().size());
			assertEquals("alpha", completed.getMatches().get(0).getRounds().get(0).getWinnerFleetId());
			assertEquals(round.id(), completed.getMatches().get(0).getRounds().get(0).getReportId());
			reports.reload();
			BattleNext_BattleReportModel report = reports.get(round.id());
			assertNotNull(report);
			assertEquals(round.id(), report.getId());
			assertTrue(report.getName().startsWith("Standard vs Standard - " + completed.getName(configuration) + " / "
					+ round.source().getJSONObject("match").getString("id") + " / 1 - "));
			assertEquals("player", report.getWinner());
			assertEquals(1234L, report.getCreatedAt());
			assertEquals(10000L, report.getDurationMillis());
			assertJsonEquals(inputReport.toJson().getJSONArray("playerMembers"), report.toJson().getJSONArray("playerMembers"));
			assertJsonEquals(inputReport.toJson().getJSONArray("enemyMembers"), report.toJson().getJSONArray("enemyMembers"));
			BattleNext_SimulationTask.Metrics metrics = task.getMetrics();
			assertEquals(10f, metrics.simulationSeconds());
			assertEquals(100000000L, metrics.executionNanos());
			assertEquals(600L, metrics.steps());
			assertEquals(1024L, metrics.peakHeapBytes());
			assertNull(task.pendingRound());
			assertNull(task.getCurrentRound());
			assertJsonEquals(completed.toJson(), new JSONObject(task.getResult().startedTournamentJson()));
			assertTrue(BattleNext_BattleSignals.consumePendingResult());
			byte[] committedReport = Files.readAllBytes(directory.resolve("tournament/_reports/" + round.id() + ".json"));
			task.retryImport();
			BattleNext_SimulatorService.advance();
			assertArrayEquals(committedReport, Files.readAllBytes(directory.resolve("tournament/_reports/" + round.id() + ".json")));
			assertEquals(metrics, task.getMetrics());
			assertFalse(BattleNext_BattleSignals.consumePendingResult());
		}
	}

	@Test
	void deletingRunningTournamentRejectsLateResultsAndKeepsPreviouslySavedReports() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(100);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			BattleNext_SimulationTask task = service.submit(BattleNext_SimulationRequest.fromTournament(configuration,
					List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
			client.setTcpNoDelay(true);
			socket.setTcpNoDelay(true);
			BattleNext_SimulationRequest first = nextRequest(peer);
			completeRound(peer, first, "player");
			BattleNext_SimulationRequest second = nextRequest(peer);
			BattleNext_SimulatorService.deleteTournament(task.getRequest().id());
			assertEquals("CANCEL", peer.receive().getString("type"));
			assertTrue(task.isDeletionRequested());
			assertThrows(IllegalStateException.class, task::resume);
			completeRound(peer, second, "enemy");
			long deadline = System.nanoTime() + 5000000000L;
			while (BattleNext_SimulatorService.tournament(task.getRequest().id()) != null && System.nanoTime() < deadline) {
				BattleNext_SimulatorService.advance();
				Thread.sleep(10L);
			}
			assertNull(BattleNext_SimulatorService.tournament(task.getRequest().id()));
			BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
			instances.reload();
			assertNull(instances.get(task.getRequest().id()));
			BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
			reports.reload();
			assertNotNull(reports.get(first.id()));
			assertNull(reports.get(second.id()));
			assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + task.getRequest().id() + ".json")));
		}
	}

}
