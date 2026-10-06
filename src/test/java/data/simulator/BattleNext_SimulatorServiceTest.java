package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestMissionFixture;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.scripts.ui.page.BattleNext_TournamentPage;
import data.scripts.ui.page.BattleNext_BattleReportPage;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_DropdownField;
import data.model.report.BattleNext_ReportTarget;
import data.model.tournament.BattleNext_TournamentModel;
import org.json.JSONObject;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
			service = BattleNext_SimulatorService.getInstance();
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
			assertTrue(labels.stream().anyMatch(text -> text.contains("Configured map 2 / actual map 2 / map scale 2.00")));
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
	void tournamentStartupFailureKeepsItsSnapshotAndReuseAllowsManualContinuation() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.setId("cup");
		tournament.setFleetIds(List.of("alpha", "beta"));
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromTournament(tournament,
				sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults());
		BattleNext_SimulationTask task = service.submit(request);
		assertSame(task, service.submit(request));
		long deadline = System.nanoTime() + 15000000000L;
		while (task.getState() != BattleNext_SimulationTask.State.PAUSED && System.nanoTime() < deadline) {
			BattleNext_SimulatorService.advance();
			Thread.sleep(20L);
		}
		BattleNext_SimulatorService.advance();
		assertEquals(BattleNext_SimulationTask.State.PAUSED, task.getState());
		assertTrue(task.getTournamentSnapshot().getDiagnostic().contains("cold start"));
		assertNotNull(task.getTournamentSnapshot().getCurrentRound());
		assertSame(task, BattleNext_SimulatorService.tournament("cup"));
		assertEquals(0, BattleNext_SimulatorService.status().queued());
	}

	@Test
	void applicationRecoveryKeepsFrozenRoundAndScoresAndWaitsForExplicitResume() throws Exception {
		quickBattles("alpha,beta,-1,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel source = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		source.setFleetIds(List.of("alpha", "beta"));
		BattleNext_TournamentSnapshot snapshot = BattleNext_TournamentSnapshot.create(BattleNext_SimulationRequest.fromTournament(
				source, sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
		BattleNext_SimulationRequest first = snapshot.prepareRound();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(first.id());
		report.setName("First round");
		report.setWinner("player");
		snapshot.record(new BattleNext_SimulationResult(first.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 10f, 100000000L, 600L, 1024L, ""));
		BattleNext_SimulationRequest second = snapshot.prepareRound();
		snapshot.transition(BattleNext_SimulationTask.State.RUNNING, "");
		BattleNext_SimulatorStorage storage = (BattleNext_SimulatorStorage) readField("storage");
		storage.writeSnapshot(snapshot);
		service.close();
		singleton.set(null, null);
		createOwnedService();
		BattleNext_SimulationTask recovered = BattleNext_SimulatorService.tournament("cup");
		assertEquals(BattleNext_SimulationTask.State.PAUSED, recovered.getState());
		assertEquals(second, recovered.getTournamentSnapshot().getCurrentRound());
		assertEquals(1, recovered.getTournamentSnapshot().getTournament().getNextPendingMatch().getRounds().size());
		assertEquals(0, BattleNext_SimulatorService.status().queued());
		assertNull(readField("process"));
		BattleNext_TournamentSnapshotStore snapshots = new BattleNext_TournamentSnapshotStore();
		snapshots.reload();
		assertEquals(BattleNext_SimulationTask.State.PAUSED, snapshots.get(snapshot.getId()).getState());
		assertEquals(second, snapshots.get(snapshot.getId()).getCurrentRound());
	}

	@Test
	void resourceReloadFinishesAndSavesTheCurrentRoundBeforeRestartingTheWorker() throws Exception {
		quickBattles("alpha,beta,-1,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel source = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		source.setFleetIds(List.of("alpha", "beta"));
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(20);
			OwnedProcess process = new OwnedProcess();
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), process);
			BattleNext_SimulationTask task = service.submit(BattleNext_SimulationRequest.fromTournament(source,
					sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults()));
			BattleNext_SimulationRequest first = nextRequest(peer);
			SettingsAPI settings = Global.getSettings();
			ModSpecAPI mod = proxy(ModSpecAPI.class, (method, args) -> method.equals("getPath") ? directory.toString() : null);
			ModManagerAPI mods = proxy(ModManagerAPI.class, (method, args) -> switch (method) {
				case "getModSpec" -> mod;
				case "getEnabledModsCopy" -> List.of();
				default -> null;
			});
			Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
					(instance, method, args) -> method.getName().equals("getModManager") ? mods : method.invoke(settings, args)));
			try {
				BattleNext_SimulatorService.resourcesReloaded();
			} finally {
				Global.setSettings(settings);
			}
			assertTrue(process.isAlive());
			completeRound(peer, first, "player");
			awaitTournamentState(task, BattleNext_SimulationTask.State.PAUSED);
			assertFalse(process.isAlive());
			assertEquals("SHUTDOWN", peer.receive().getString("type"));
			BattleNext_TournamentSnapshot snapshot = task.getTournamentSnapshot();
			assertEquals(first.id(), snapshot.getTournament().getNextPendingMatch().getRounds().get(0).getReport().getId());
			assertNotEquals(first.id(), snapshot.getCurrentRound().id());
			assertTrue(snapshot.getDiagnostic().contains("cold start"));
			BattleNext_TournamentSnapshotStore store = new BattleNext_TournamentSnapshotStore();
			store.reload();
			assertEquals(snapshot.getCurrentRound(), store.get(snapshot.getId()).getCurrentRound());
			assertEquals(1, store.get(snapshot.getId()).getTournament().getNextPendingMatch().getRounds().size());
		}
	}

	@Test
	void tournamentRunsConsecutiveRoundsPausesResumesAndUpdatesTheChartWhileKeepingPlayerStateAndSaveRetries() throws Exception {

		quickBattles("alpha,beta,-1,1\n");
		BattleNext_Settings.save(false, true, false, false, 123f, 1f, 3f, 0);
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel source = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		source.setFleetIds(List.of("alpha", "beta"));
		sources.getTournamentStore().save(source);
		sources.getTournamentStore().setActiveId("cup");
		byte[] playerState = Files.readAllBytes(directory.resolve("tournament/_tournaments/cup.json"));
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		browser.select("cup");
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 600f, new BaseCustomUIPanelPlugin());
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket socket = listener.accept();
				BattleNext_SimulatorProtocol peer = new BattleNext_SimulatorProtocol(socket, "session")) {
			client.setSoTimeout(100);
			socket.setSoTimeout(20);
			attachWorker(new BattleNext_SimulatorProtocol(client, "session"), new OwnedProcess());
			widget.attach(root);
			BattleNext_TournamentDisplay display = (BattleNext_TournamentDisplay) getField(widget, "tournamentDisplay");
			BattleNext_TournamentView chart = (BattleNext_TournamentView) getField(display, "chart");
			chart.zoomAt(60f, 60f, 3);
			chart.panBy(12f, 7f);
			BattleNext_TournamentView.ViewState chartState = chart.captureState();
			TooltipMakerAPI content = (TooltipMakerAPI) getField(display, "content");
			content.getExternalScroller().setYOffset(20f);
			float scroll = display.captureState().scroll();
			CustomPanelAPI viewport = (CustomPanelAPI) getField(display, "viewport");
			viewport.getPlugin().buttonPressed("simulator:tournament:cup");
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament("cup");
			assertNotNull(task);
			BattleNext_SimulationTask waiting = service.submit(BattleNext_SimulationRequest.fromVariants(List.of("parent_variant"), List.of("child_variant"), BattleNext_SimulationConfig.defaults()));
			BattleNext_SimulationRequest first = nextRequest(peer);
			widget.refreshStatus();
			assertSame(chart, getField(display, "chart"));
			assertEquals(chartState, chart.captureState());
			assertEquals(scroll, display.captureState().scroll());
			var progress = task.getTournamentSnapshot().getTournament();
			assertTrue(progress.isStarted());
			assertEquals(task.getRequest().id(), progress.getId());
			float[] point = chart.project(60f, 100f);
			chart.handleInput(List.of(proxy(InputEventAPI.class, (method, args) -> switch (method) {
				case "isLMBDownEvent", "isMouseEvent" -> true;
				case "getX" -> (int) point[0];
				case "getY" -> (int) point[1];
				default -> null;
			})));
			assertTrue((boolean) getField(chart, "dragging"));
			BattleNext_DropdownField dropdown = (BattleNext_DropdownField) getField(getField(widget, "sourceField"), "dropdown");
			plugins.get((CustomPanelAPI) getField(dropdown, "row")).buttonPressed(buttonIds.get("TOGGLE"));
			assertTrue(dropdown.isOpen());
			completeRound(peer, first, "player");
			BattleNext_SimulationRequest second = nextRequest(peer);
			widget.refreshStatus();
			assertEquals(chartState, chart.captureState());
			assertEquals(scroll, display.captureState().scroll());
			assertTrue(dropdown.isOpen());
			widget.closeOverlays();
			assertTrue((boolean) getField(chart, "dragging"));
			assertSame(display, getField(widget, "tournamentDisplay"));
			assertNotEquals(first.id(), second.id());
			assertEquals(1, task.getTournamentSnapshot().getTournament().getNextPendingMatch().getRounds().size());
			assertEquals(1, BattleNext_SimulatorService.status().queued());
			assertNull(waiting.getResult());
			task.pause();
			JSONObject cancelled = peer.receive();
			assertEquals("CANCEL", cancelled.getString("type"));
			assertEquals(second.id(), cancelled.getJSONObject("payload").getString("id"));
			peer.send("RESULT", new BattleNext_SimulationResult(second.id(), BattleNext_SimulationResult.Outcome.CANCELLED,
					"", 0f, 0L, 0L, 0L, "Paused").toJson());
			peer.send("READY", new JSONObject());
			BattleNext_SimulationRequest independent = nextRequest(peer);
			assertEquals(waiting.getRequest(), independent);
			assertEquals(BattleNext_SimulationTask.State.PAUSED, task.getState());
			widget.refreshStatus();
			assertEquals("Continue simulation", buttonLabels.get("simulator:resume:cup"));
			task.resume();
			assertEquals(1, BattleNext_SimulatorService.status().queued());
			completeRound(peer, independent, "player");
			BattleNext_SimulationRequest resumed = nextRequest(peer);
			assertEquals(second, resumed);
			Path snapshotPath = directory.resolve("tournament/_simulations/" + task.getRequest().id() + ".json");
			Path savedPath = snapshotPath.resolveSibling("checkpoint.saved");
			Files.move(snapshotPath, savedPath);
			Files.createDirectory(snapshotPath);
			try {
				completeRound(peer, resumed, "enemy");
				awaitTournamentState(task, BattleNext_SimulationTask.State.IMPORT_FAILED);
			} finally {
				Files.delete(snapshotPath);
				Files.move(savedPath, snapshotPath);
			}
			assertTrue(task.getTournamentSnapshot().getTournament().isCompleted());
			assertEquals(2, task.getTournamentSnapshot().getTournament().getMatches().get(0).getRounds().size());
			task.retryImport();
			awaitTournamentState(task, BattleNext_SimulationTask.State.FINISHED);
			assertNotNull(task.getResult());
			assertFalse(task.getResult().tournamentSnapshotJson().isEmpty());
			widget.refreshStatus();
			assertEquals("Simulate again", buttonLabels.get("simulator:tournament:cup"));
			BattleNext_TournamentSnapshotStore snapshots = new BattleNext_TournamentSnapshotStore();
			snapshots.reload();
			assertEquals(2, snapshots.get(task.getRequest().id()).getTournament().getMatches().get(0).getRounds().size());
			assertEquals(BattleNext_SimulationTask.State.FINISHED, snapshots.get(task.getRequest().id()).getState());
			assertArrayEquals(playerState, Files.readAllBytes(directory.resolve("tournament/_tournaments/cup.json")));
			sources.getTournamentStore().reload();
			assertEquals("cup", sources.getTournamentStore().getActiveId());
			assertEquals(List.of("cup"), sources.getTournamentStore().getAll().stream().map(value -> value.getId()).toList());
			widget.close();
			BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (label, action) -> action.run());
			page.attach(root);
			page.buttonPressed("simulation:select:" + task.getRequest().id());
			assertFalse(buttons.get("save").get());
			assertFalse(page.hasUnsavedChanges());
			page.close();
			BattleNext_BattleReportPage reports = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (label, action) -> action.run());
			reports.attach(root);
			String identity = "simulation:" + task.getRequest().id() + "/W1-1/0";
			reports.buttonPressed("report:" + identity);
			assertFalse(buttons.get("save").get());
			assertFalse(buttons.get("delete").get());
			assertNull(getField(getField(reports, "editor"), "nameField"));
			BattleNext_ReportTarget target = (BattleNext_ReportTarget) getField(getField(reports, "editor"), "target");
			assertTrue(target.isReadOnly());
			assertThrows(IllegalStateException.class, () -> target.rename(new BattleNext_BattleReportStore(), sources.getTournamentStore(), "Changed"));
			reports.close();
			BattleNext_SimulatorService.deleteTournament(task.getRequest().id());
			snapshots.reload();
			assertTrue(snapshots.getAll().isEmpty());
			assertNull(BattleNext_SimulatorService.tournament("cup"));
		} catch (Exception | AssertionError exception) {
			try {
				widget.close();
			} catch (Exception | AssertionError cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			throw exception;
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
		Path runtime = storage.directory("runtime/owned-session");
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
