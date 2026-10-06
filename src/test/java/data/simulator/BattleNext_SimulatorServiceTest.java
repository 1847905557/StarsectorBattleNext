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
		previousSaves = System.getProperty("com.fs.starfarer.settings.paths.saves");
		System.setProperty("com.fs.starfarer.settings.paths.saves", directory.resolve("saves").toString());
		SettingsAPI settings = Global.getSettings();
		ModSpecAPI mod = proxy(ModSpecAPI.class, (method, args) -> method.equals("getPath") ? Path.of("").toAbsolutePath().toString() : null);
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
	void tournamentSubmissionReportsUnsupportedExecutionAndLeavesTheServiceIdle() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.setId("cup");
		tournament.setFleetIds(List.of("alpha", "beta"));
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromTournament(tournament,
				sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.defaults());
		UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class, () -> service.submit(request));
		assertEquals("Tournament simulation is not supported", failure.getMessage());
		assertEquals(BattleNext_SimulatorService.Phase.IDLE, BattleNext_SimulatorService.status().phase());
		assertEquals(0, BattleNext_SimulatorService.status().queued());
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
