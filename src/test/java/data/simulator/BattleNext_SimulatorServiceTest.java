package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestGameFixture;
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

class BattleNext_SimulatorServiceTest extends BattleNext_TestGameFixture {

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
		BattleNext_SimulationTask first = service.submit(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.lab());
		BattleNext_SimulationTask second = service.submit(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.lab());
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
	void pluginSubmitsOnceAndReplaysEveryObservedStageExactlyOnce() throws Exception {
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getCurrentState")) return GameState.COMBAT;
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		List<Object> notices = new ArrayList<>();
		CombatUIAPI ui = proxy(CombatUIAPI.class, (method, args) -> {
			if (method.equals("addMessage")) {
				notices.add(args[1]);
			}
			return null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> method.equals("getCombatUI") ? ui : null);
		BattleNext_SimulatorPlugin plugin = new BattleNext_SimulatorPlugin(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.lab());
		plugin.init(engine);
		BattleNext_SimulationTask task = plugin.getTask();
		plugin.init(engine);
		assertSame(task, plugin.getTask());
		long deadline = System.nanoTime() + 15000000000L;
		while (task.getResult() == null && System.nanoTime() < deadline) {
			Thread.sleep(20L);
		}
		assertNotNull(task.getResult());
		plugin.advance(0f, List.of());
		assertEquals(task.getStateHistory().size(), notices.size());
		plugin.advance(0f, List.of());
		assertEquals(task.getStateHistory().size(), notices.size());
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
			BattleNext_SimulationTask active = new BattleNext_SimulationTask(new BattleNext_SimulationRequest(UUID.randomUUID().toString(),
					List.of("player"), List.of("enemy"), BattleNext_SimulationConfig.lab()), service);
			BattleNext_SimulationTask queued = new BattleNext_SimulationTask(new BattleNext_SimulationRequest(UUID.randomUUID().toString(),
					List.of("player"), List.of("enemy"), BattleNext_SimulationConfig.lab()), service);
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
		BattleNext_SimulationRequest request = new BattleNext_SimulationRequest(UUID.randomUUID().toString(), List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.lab());
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
