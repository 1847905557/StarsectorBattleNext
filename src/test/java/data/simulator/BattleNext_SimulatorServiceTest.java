package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.page.BattleNext_BattleReportPage;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.test.fixture.BattleNext_TestMissionFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class BattleNext_SimulatorServiceTest extends BattleNext_TestMissionFixture {

	private Object previousService;
	private String previousSaves;
	private Field singleton;
	private BattleNext_SimulatorService service;

	@BeforeEach
	void initializeOwnedServiceThroughGameMetadata() throws Exception {

		singleton = BattleNext_SimulatorService.class.getDeclaredField("instance");
		singleton.setAccessible(true);
		previousService = singleton.get(null);
		singleton.set(null, null);
		directory = Files.createDirectories(directory.resolve("game/mods/fixture"));
		Files.createDirectories(directory.getParent().getParent().resolve("starsector-core"));
		previousSaves = System.getProperty("com.fs.starfarer.settings.paths.saves");
		System.setProperty("com.fs.starfarer.settings.paths.saves", directory.resolve("saves").toString());
		createOwnedService();
	}

	private void createOwnedService() throws Exception {
		withServiceMetadata(() -> service = BattleNext_SimulatorService.getInstance());
	}

	private void withServiceMetadata(Runnable action) throws Exception {
		SettingsAPI settings = Global.getSettings();
		ModSpecAPI mod = proxy(ModSpecAPI.class, (method, args) -> method.equals("getPath") ? directory.toString() : null);
		ModManagerAPI mods = proxy(ModManagerAPI.class, (method, args) -> switch (method) {
			case "getModSpec" -> mod;
			case "getEnabledModsCopy" -> List.of();
			default -> null;
		});
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getModManager")) return mods;
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		try {
			action.run();
		} finally {
			Global.setSettings(settings);
		}
	}

	@AfterEach
	void closeOwnedProcessesAndRestoreApplicationState() throws Exception {
		if (service != null) {
			service.close();
		}
		singleton.set(null, previousService);
		if (previousSaves == null) {
			System.clearProperty("com.fs.starfarer.settings.paths.saves");
		} else {
			System.setProperty("com.fs.starfarer.settings.paths.saves", previousSaves);
		}
	}

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
	void independentTournamentTasksShareSourceAndPersistSeparateTaskFiles() throws Exception {
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
		assertEquals(2, instances.getForSource("cup", BattleNext_StartedTournament.ExecutionKind.SIMULATION).size());
		assertEquals(first.getRequest().id(), first.getStartedTournament().getId());
		assertTrue(Files.exists(directory.resolve("tournament/_simulations/" + first.getRequest().id() + ".json")));
		assertTrue(Files.exists(directory.resolve("tournament/_simulations/" + second.getRequest().id() + ".json")));
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
			assertEquals(2, instances.get(task.getRequest().id()).getMatches().get(0).getRounds().size());
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

	@ParameterizedTest
	@ValueSource(strings = {"report", "instance"})
	void acquiredRoundPersistsBeforeScoreCommitAndRetryCountsItOnce(String failureStage) throws Exception {
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
			Path progress = directory.resolve(BattleNext_StartedTournamentStore.PATH);
			byte[] original = Files.readAllBytes(progress);
			Path obstruction = failureStage.equals("report") ? directory.resolve("tournament/_reports/" + round.id() + ".json") : progress;
			if (failureStage.equals("instance")) {
				Files.delete(progress);
			}
			Files.createDirectories(obstruction);
			completeRound(peer, round, "player");
			awaitTournamentState(task, BattleNext_SimulationTask.State.IMPORT_FAILED);
			assertEquals(round.id(), task.pendingRound().id());
			JSONObject savedTask = new JSONObject(Files.readString(directory.resolve("tournament/_simulations/" + task.getRequest().id() + ".json")));
			assertEquals(round.id(), savedTask.getJSONObject("pendingRound").getString("id"));
			Files.delete(obstruction);
			if (failureStage.equals("instance")) {
				Files.write(progress, original);
			}
			task.retryImport();
			awaitTournamentState(task, BattleNext_SimulationTask.State.FINISHED);
			BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
			instances.reload();
			assertEquals(1, instances.get(task.getRequest().id()).getMatches().get(0).getRounds().size());
			assertEquals(round.id(), instances.get(task.getRequest().id()).getMatches().get(0).getRounds().get(0).getReportId());
		}
	}

	@Test
	void recoveredTaskReadsItsInstanceAndWaitsForManualContinuation() throws Exception {
		quickBattles("alpha,beta,-1,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults());
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(request.id(), "cup", BattleNext_StartedTournament.ExecutionKind.SIMULATION, List.of("alpha", "beta"));
		new BattleNext_TournamentEngine().initialize(configuration, started);
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, service);
		task.publishStarted(started);
		BattleNext_SimulationRequest round = request.forMatch(configuration, started, started.getNextPendingMatch(), sources.getFleetStore(), 2);
		task.prepareRound(round);
		((BattleNext_SimulatorStorage) readField("storage")).writeTask(task);
		new BattleNext_StartedTournamentStore().save(started);
		service.close();
		singleton.set(null, null);
		createOwnedService();
		BattleNext_SimulationTask recovered = BattleNext_SimulatorService.tournament(request.id());
		assertEquals(BattleNext_SimulationTask.State.PAUSED, recovered.getState());
		assertEquals(round.id(), recovered.getCurrentRound().id());
		assertEquals(2, recovered.getCurrentRound().source().getInt("actualMap"));
		assertEquals(started.toJson().toString(), recovered.getStartedTournament().toJson().toString());
		assertNull(readField("process"));
		assertNull(readField("supervisor"));
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
			socket.setSoTimeout(100);
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
			assertEquals(round.id(), instances.get(task.getRequest().id()).getMatches().get(0).getRounds().get(0).getReportId());
			assertNotEquals(round.id(), task.getCurrentRound().id());
		}
	}

	private void awaitTournamentState(BattleNext_SimulationTask task, BattleNext_SimulationTask.State state) throws Exception {
		long deadline = System.nanoTime() + 5000000000L;
		while ((task.getState() != state || readField("active") != null) && System.nanoTime() < deadline) {
			BattleNext_SimulatorService.advance();
			Thread.sleep(10L);
		}
		assertEquals(state, task.getState());
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

	@Test
	void failedDeletionPersistsIntentAndRecoveryCompletesOwnedCleanup() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		sources.getTournamentStore().save(configuration);
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromTournament(configuration, List.of("alpha", "beta"), sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults());
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(request.id(), "cup", BattleNext_StartedTournament.ExecutionKind.SIMULATION, List.of("alpha", "beta"));
		new BattleNext_TournamentEngine().initialize(configuration, started);
		new BattleNext_StartedTournamentStore().save(started);
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, service);
		task.publishStarted(started);
		task.requestDeletion();
		((BattleNext_SimulatorStorage) readField("storage")).writeTask(task);
		Path obstruction = directory.resolve("tournament/_simulations/_pending/" + request.id() + ".json");
		Files.createDirectories(obstruction);
		Files.writeString(obstruction.resolve("owned"), "Keep");
		service.close();
		singleton.set(null, null);
		createOwnedService();
		BattleNext_SimulatorService.advance();
		task = BattleNext_SimulatorService.tournament(request.id());
		assertNotNull(task);
		assertTrue(task.isDeletionRequested());
		assertTrue(task.getImportError().contains("DirectoryNotEmptyException"));
		assertTrue(Files.exists(directory.resolve("tournament/_simulations/" + request.id() + ".json")));
		assertEquals("Keep", Files.readString(obstruction.resolve("owned")));
		Files.delete(obstruction.resolve("owned"));
		service.close();
		singleton.set(null, null);
		createOwnedService();
		BattleNext_SimulatorService.advance();
		assertNull(BattleNext_SimulatorService.tournament(request.id()));
		assertFalse(Files.exists(directory.resolve("tournament/_simulations/" + request.id() + ".json")));
		assertFalse(Files.exists(obstruction));
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		instances.reload();
		assertNull(instances.get(request.id()));
	}

	private BattleNext_SimulationRequest nextRequest(BattleNext_SimulatorProtocol peer) throws Exception {
		long deadline = System.nanoTime() + 5000000000L;
		while (System.nanoTime() < deadline) {
			BattleNext_SimulatorService.advance();
			try {
				JSONObject frame = peer.receive();
				assertEquals("REQUEST", frame.getString("type"));
				return BattleNext_SimulationRequest.fromJson(frame.getJSONObject("payload"));
			} catch (SocketTimeoutException exception) {
				continue;
			}
		}
		throw new AssertionError("Expected a simulation request after committed progress");
	}

	@Test
	void recoveryIsolatesMalformedTasksAndPendingFilesAndImportsEveryCompleteResult() throws Exception {
		BattleNext_SimulationTask valid = completedTask("Recovered result");
		BattleNext_SimulatorStorage storage = (BattleNext_SimulatorStorage) readField("storage");
		storage.writeTask(valid);
		Path root = directory.resolve("tournament/_simulations");
		Files.writeString(root.resolve("000-broken.json"), "{ invalid task", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("_pending/000-broken.json"), "{ invalid pending", StandardCharsets.UTF_8);
		BattleNext_SimulationTask invalidState = new BattleNext_SimulationTask(BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults()), service);
		Files.writeString(root.resolve(invalidState.getRequest().id() + ".json"), invalidState.toJson().put("state", "UNKNOWN").toString(), StandardCharsets.UTF_8);
		service.close();
		singleton.set(null, null);
		createOwnedService();
		BattleNext_SimulatorService.advance();
		var reports = new BattleNext_BattleReportStore();
		reports.reload();
		assertEquals("Recovered result", reports.get(valid.getRequest().id()).getName());
		storage = (BattleNext_SimulatorStorage) readField("storage");
		assertEquals(3, storage.getLoadErrors().size());
		assertTrue(storage.getLoadErrors().get(root.resolve("000-broken.json")).contains("invalid task"));
		assertTrue(storage.getLoadErrors().get(root.resolve("_pending/000-broken.json")).contains("invalid pending"));
		assertTrue(storage.getLoadErrors().get(root.resolve(invalidState.getRequest().id() + ".json")).contains("UNKNOWN"));
		assertTrue(Files.exists(root.resolve("000-broken.json")));
	}

	@Test
	void taskIdentityConflictsRejectBothFilesAndRecoverAfterTheConflictingSourceIsRemoved() throws Exception {
		var task = new BattleNext_SimulationTask(BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults()), service);
		var storage = (BattleNext_SimulatorStorage) readField("storage");
		storage.writeTask(task);
		var conflict = directory.resolve("tournament/_simulations/conflict.json");
		Files.writeString(conflict, task.toJson().toString(), StandardCharsets.UTF_8);
		assertTrue(storage.readTasks().isEmpty());
		assertEquals(2, storage.getLoadErrors().size());
		assertTrue(storage.getLoadErrors().values().stream().anyMatch(error -> error.contains("Duplicate simulator task ID")));
		Files.delete(conflict);
		assertEquals(task.getRequest().id(), storage.readTasks().get(0).getJSONObject("request").getString("id"));
		assertTrue(storage.getLoadErrors().isEmpty());
	}

	private void completeRound(BattleNext_SimulatorProtocol peer, BattleNext_SimulationRequest request, String winner) throws Exception {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(request.id());
		report.setName("Simulation report " + request.id());
		report.setWinner(winner);
		peer.send("RESULT", new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 10f, 100000000L, 600L, 1024L, "").toJson());
		peer.send("READY", new JSONObject());
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
		BattleNext_SimulationTask task = completedTask("Saved simulation");
		importQueue().add(task);
		advance.run();
		assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
		assertTrue(task.isRetained());
		BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
		reports.reload();
		assertEquals(task.getRequest().id(), reports.getAll().get(0).getId());
		importQueue().add(task);
		advance.run();
		reports.reload();
		assertEquals(List.of(task.getRequest().id()), reports.getAll().stream().map(BattleNext_BattleReportModel::getId).toList());
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, 0);
		BattleNext_SimulationTask discarded = completedTask("Not retained");
		importQueue().add(discarded);
		advance.run();
		assertFalse(discarded.isRetained());
		reports.reload();
		assertTrue(reports.getAll().isEmpty());
	}

	@Test
	void reportImportFailureKeepsTheCompleteResultAndSupportsExplicitRetry() throws Exception {
		BattleNext_SimulationTask task = completedTask("Retry simulation");
		BattleNext_BattleReportModel existing = new BattleNext_BattleReportModel();
		existing.setId("existing");
		existing.setName("Existing report");
		BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
		reports.reload();
		reports.save(existing);
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
			importQueue().add(task);
			BattleNext_SimulatorService.advance();
		} finally {
			Global.setSettings(settings);
		}
		assertEquals(BattleNext_SimulationTask.State.IMPORT_FAILED, task.getState());
		assertEquals("Retry simulation", task.getResult().report().getName());
		assertEquals(List.of(task), service.getFailedImports());
		task.retryImport();
		BattleNext_SimulatorService.advance();
		assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
		assertTrue(service.getFailedImports().isEmpty());
	}

	@Test
	void brokenShutdownChannelStillTerminatesTheOwnedProcessAndReleasesItsRuntime() throws Exception {
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket peer = listener.accept()) {
			BattleNext_SimulatorProtocol channel = new BattleNext_SimulatorProtocol(client, "session");
			OwnedProcess process = new OwnedProcess();
			Path runtime = attachWorker(channel, process);
			channel.close();
			service.close();
			assertFalse(process.isAlive());
			assertFalse(Files.exists(runtime));
			assertNull(readField("process"));
			assertNull(readField("channel"));
			assertNull(readField("runtime"));
		}
	}

	@Test
	void closingAnExecutingServiceJournalsActiveAndQueuedCancellationBeforeReleasingTheWorker() throws Exception {

		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(5000);
			BattleNext_SimulatorProtocol channel = new BattleNext_SimulatorProtocol(client, "session");
			OwnedProcess process = new OwnedProcess();
			Path runtime = attachWorker(channel, process);
			BattleNext_SimulationTask active = new BattleNext_SimulationTask(BattleNext_SimulationRequest.fromVariants(List.of("player"), List.of("enemy"), BattleNext_SimulationConfig.defaults()), service);
			BattleNext_SimulationTask queued = new BattleNext_SimulationTask(BattleNext_SimulationRequest.fromVariants(List.of("player"), List.of("enemy"), BattleNext_SimulationConfig.defaults()), service);
			LinkedBlockingQueue<BattleNext_SimulationTask> tasks = (LinkedBlockingQueue<BattleNext_SimulationTask>) readField("queue");
			tasks.add(active);
			tasks.add(queued);
			Method run = BattleNext_SimulatorService.class.getDeclaredMethod("run");
			run.setAccessible(true);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			Thread supervisor = new Thread(() -> {
				try {
					run.invoke(service);
				} catch (Exception exception) {
					failure.set(exception);
				}
			});
			writeField("supervisor", supervisor);
			supervisor.start();
			assertEquals(active.getRequest().id(), peer.receive().getJSONObject("payload").getString("id"));
			service.close();
			assertFalse(supervisor.isAlive());
			assertNull(failure.get());
			assertFalse(process.isAlive());
			assertFalse(Files.exists(runtime));
			assertEquals("SHUTDOWN", peer.receive().getString("type"));
			for (BattleNext_SimulationTask task : List.of(active, queued)) {
				assertEquals(BattleNext_SimulationResult.Outcome.CANCELLED, task.getResult().outcome());
				assertEquals(BattleNext_SimulationTask.State.FINISHED, task.getState());
			}
			BattleNext_SimulatorStorage storage = (BattleNext_SimulatorStorage) readField("storage");
			Set<String> expected = Set.of(active.getRequest().id() + ".json", queued.getRequest().id() + ".json");
			assertEquals(expected, storage.pending().stream().map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
		}
	}

	private Path attachWorker(BattleNext_SimulatorProtocol channel, Process process) throws Exception {
		BattleNext_SimulatorStorage storage = (BattleNext_SimulatorStorage) readField("storage");
		Path runtime = storage.directory("_runtime/owned-session");
		writeField("runtime", runtime);
		writeField("channel", channel);
		writeField("process", process);
		return runtime;
	}

	private Object readField(String name) throws Exception {
		Field field = BattleNext_SimulatorService.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(service);
	}

	private void writeField(String name, Object value) throws Exception {
		Field field = BattleNext_SimulatorService.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(service, value);
	}

	private BattleNext_SimulationTask completedTask(String name) throws Exception {
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults());
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, service);
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(request.id());
		report.setName(name);
		report.setCreatedAt(System.currentTimeMillis());
		BattleNext_SimulationResult result = new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 100f, 500000000L, 6000L, 1024L, "");
		Field storageField = BattleNext_SimulatorService.class.getDeclaredField("storage");
		storageField.setAccessible(true);
		((BattleNext_SimulatorStorage) storageField.get(service)).write(request, result);
		task.receive(result);
		return task;
	}

	private ConcurrentLinkedQueue<BattleNext_SimulationTask> importQueue() throws Exception {
		Field field = BattleNext_SimulatorService.class.getDeclaredField("imports");
		field.setAccessible(true);
		return (ConcurrentLinkedQueue<BattleNext_SimulationTask>) field.get(service);
	}

	private static class OwnedProcess extends Process {
		private boolean alive = true;

		@Override
		public OutputStream getOutputStream() {
			return OutputStream.nullOutputStream();
		}

		@Override
		public InputStream getInputStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public InputStream getErrorStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public int waitFor() {
			return 0;
		}

		@Override
		public boolean waitFor(long timeout, TimeUnit unit) {
			return !alive;
		}

		@Override
		public int exitValue() {
			if (alive) throw new IllegalThreadStateException("Owned process is still running");
			return 0;
		}

		@Override
		public void destroy() {
			alive = false;
		}

		@Override
		public boolean isAlive() {
			return alive;
		}
	}
}
