package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

abstract class BattleNext_SimulatorServiceFixture extends BattleNext_TestFleetIoFixture {

	private Object previousService;
	private String previousSaves;
	protected Field singleton;
	protected BattleNext_SimulatorService service;

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

	protected void createOwnedService() throws Exception {
		withServiceMetadata(() -> service = BattleNext_SimulatorService.getInstance());
	}

	protected void withServiceMetadata(Runnable action) throws Exception {
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

	protected void awaitTournamentState(BattleNext_SimulationTask task, BattleNext_SimulationTask.State state) throws Exception {
		long deadline = System.nanoTime() + 5000000000L;
		while ((task.getState() != state || readField("active") != null) && System.nanoTime() < deadline) {
			BattleNext_SimulatorService.advance();
			Thread.sleep(10L);
		}
		assertEquals(state, task.getState());
	}

	protected BattleNext_SimulationRequest nextRequest(BattleNext_SimulatorProtocol peer) throws Exception {
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

	protected void completeRound(BattleNext_SimulatorProtocol peer, BattleNext_SimulationRequest request, String winner) throws Exception {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(request.id());
		report.setName("Simulation report " + request.id());
		report.setWinner(winner);
		peer.send("RESULT", new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 10f, 100000000L, 600L, 1024L, "").toJson());
		peer.send("READY", new JSONObject());
	}

	protected Path attachWorker(BattleNext_SimulatorProtocol channel, Process process) throws Exception {
		BattleNext_SimulatorStorage storage = (BattleNext_SimulatorStorage) readField("storage");
		Path runtime = storage.directory(UUID.randomUUID().toString());
		writeField("channel", channel);
		writeField("process", process);
		return runtime;
	}

	protected Object readField(String name) throws Exception {
		Field field = BattleNext_SimulatorService.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(service);
	}

	protected void writeField(String name, Object value) throws Exception {
		Field field = BattleNext_SimulatorService.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(service, value);
	}

	protected BattleNext_SimulationTask completedTask(String name) throws Exception {
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromVariants(List.of("wolf_CS"), List.of("wolf_CS"), BattleNext_SimulationConfig.defaults());
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, service);
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(request.id());
		report.setName(name);
		report.setCreatedAt(System.currentTimeMillis());
		BattleNext_SimulationResult result = new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 100f, 500000000L, 6000L, 1024L, "");
		task.receive(result);
		return task;
	}

	protected ConcurrentLinkedQueue<BattleNext_SimulationTask> importQueue() throws Exception {
		Field field = BattleNext_SimulatorService.class.getDeclaredField("imports");
		field.setAccessible(true);
		return (ConcurrentLinkedQueue<BattleNext_SimulationTask>) field.get(service);
	}

	protected static class OwnedProcess extends Process {
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
