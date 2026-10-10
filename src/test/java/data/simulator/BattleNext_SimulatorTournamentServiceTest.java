package data.simulator;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.page.BattleNext_BattleReportPage;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class BattleNext_SimulatorTournamentServiceTest extends BattleNext_SimulatorServiceFixture {


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
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, 80);
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
			socket.setSoTimeout(10);
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
			assertEquals(80, first.config().deploymentPointLimit());
			BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 50, 160);
			completeRound(peer, first, "player");
			BattleNext_SimulationRequest second = nextRequest(peer);
			assertEquals(80, second.config().deploymentPointLimit());
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
			assertEquals(80, resumed.config().deploymentPointLimit());
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
			socket.setSoTimeout(10);
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
