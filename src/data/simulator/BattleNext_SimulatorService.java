package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import data.scripts.tools.BattleNext_Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class BattleNext_SimulatorService implements AutoCloseable {

	private static BattleNext_SimulatorService instance;
	private final LinkedBlockingQueue<BattleNext_SimulationTask> queue = new LinkedBlockingQueue<>();
	private final ConcurrentLinkedQueue<BattleNext_SimulationTask> imports = new ConcurrentLinkedQueue<>();
	private final Map<String, BattleNext_SimulationTask> failedImports = new LinkedHashMap<>();
	private final BattleNext_SimulatorStorage storage;
	private final Path core;
	private volatile List<String> command;
	private volatile boolean closed;
	private volatile boolean reload;
	private volatile BattleNext_SimulationTask active;
	private volatile BattleNext_SimulatorProtocol channel;
	private volatile Process process;
	private volatile long cancellationStarted;
	private Path runtime;
	private int generation;
	private Thread supervisor;

	private BattleNext_SimulatorService() throws IOException {

		Path mod = Path.of(Global.getSettings().getModManager().getModSpec("AI_Battles_Next").getPath()).toAbsolutePath();
		core = mod.getParent().getParent().resolve("starsector-core");
		Path saves = Path.of(System.getProperty("com.fs.starfarer.settings.paths.saves", "../saves")).toAbsolutePath().normalize();
		storage = new BattleNext_SimulatorStorage(saves.resolve("common/AI_Battles_Next/simulator"));
		command = buildCommand();
		for (Path path : storage.pending()) {
			try {
				BattleNext_SimulatorStorage.Stored stored = storage.read(path);
				if (stored.result().outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) continue;
				BattleNext_SimulationTask task = new BattleNext_SimulationTask(stored.request(), this);
				task.receive(stored.result());
				imports.add(task);
			} catch (Exception exception) {
				BattleNext_Logger.error(getClass(), "simulator.result.recovery.failed", BattleNext_Logger.fields().put("path", path.toString()).build(), exception);
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread(this::close, "BattleNext simulator shutdown"));
	}

	@LifecycleEntry
	public static synchronized BattleNext_SimulatorService getInstance() {
		if (instance == null) {
			try {
				instance = new BattleNext_SimulatorService();
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to initialize simulator service", exception);
			}
		}
		return instance;
	}

	public BattleNext_SimulationTask submit(List<String> player, List<String> enemy, BattleNext_SimulationConfig config) {
		if (closed) throw new IllegalStateException("Simulator service is closed");
		BattleNext_SimulationRequest request = new BattleNext_SimulationRequest(UUID.randomUUID().toString(), player, enemy, config);
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, this);
		queue.add(task);
		if (supervisor == null) {
			supervisor = new Thread(this::run, "BattleNext simulator supervisor");
			supervisor.setDaemon(true);
			supervisor.start();
		}
		BattleNext_Logger.info(getClass(), "simulator.task.queued", BattleNext_Logger.fields().put("id", request.id())
				.put("player", player).put("enemy", enemy).put("config", config).put("queue_size", queue.size()).build());
		return task;
	}

	public static void advance() {
		if (instance != null) {
			instance.importResults();
		}
	}

	public static void resourcesReloaded() {
		if (instance != null) {
			try {
				instance.command = instance.buildCommand();
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to refresh simulator launch resources", exception);
			}
			instance.reload = true;
		}
	}

	public List<BattleNext_SimulationTask> getFailedImports() {
		return List.copyOf(failedImports.values());
	}

	void cancel(BattleNext_SimulationTask task) {
		if (task != active) return;
		cancellationStarted = System.nanoTime();
		try {
			if (channel != null) {
				channel.send("CANCEL", new JSONObject().put("id", task.getRequest().id()));
			}
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.cancel.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
		}
	}

	void retryImport(BattleNext_SimulationTask task) {
		if (task.getState() != BattleNext_SimulationTask.State.IMPORT_FAILED) return;
		task.transition(BattleNext_SimulationTask.State.COMMITTING);
		imports.add(task);
	}

	private List<String> buildCommand() throws IOException {
		List<String> classpath = new ArrayList<>();
		classpath.addAll(storage.listFiles(core, false).stream().filter(path -> path.toString().endsWith(".jar")).map(Path::toString).toList());
		for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy()) {
			for (String jar : mod.getJars()) {
				classpath.add(Path.of(mod.getPath(), jar).toAbsolutePath().toString());
			}
		}
		List<String> launch = BattleNext_SimulatorLaunch.javaCommand(core, storage.directory("diagnostics"));
		launch.add("-cp");
		launch.add(String.join(System.getProperty("path.separator"), classpath));
		return List.copyOf(launch);
	}

	private void run() {

		try {
			while (!closed) {
				BattleNext_SimulationTask task = queue.poll(100L, TimeUnit.MILLISECONDS);
				if (task == null) continue;
				active = task;
				cancellationStarted = 0L;
				try {
					if (closed || task.isCancelled()) {
						acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.CANCELLED,
								closed ? "Simulator service closed" : "Cancelled while queued"));
						continue;
					}
					if (reload || process == null || !process.isAlive()) {
						stopWorker();
						reload = false;
						task.transition(BattleNext_SimulationTask.State.STARTING);
						startWorker();
					}
					channel.send("REQUEST", task.getRequest().toJson());
					if (task.isCancelled()) {
						cancel(task);
					}
					runTask(task);
				} catch (Exception exception) {
					if (task.getResult() == null) {
						acceptResult(task, failure(task, closed || task.isCancelled()
								? BattleNext_SimulationResult.Outcome.CANCELLED : BattleNext_SimulationResult.Outcome.FAILED,
								exception.getClass().getName() + ": " + exception.getMessage()));
					}
					BattleNext_Logger.error(getClass(), "simulator.worker.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
					stopWorker();
				} finally {
					if (closed && task.getResult() == null) {
						acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.CANCELLED, "Simulator service closed"));
					}
					active = null;
				}
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} finally {
			stopWorker();
		}
	}

	private void startWorker() throws Exception {

		String token = UUID.randomUUID().toString();
		runtime = storage.directory("runtime/" + UUID.randomUUID());
		Path sourceSaves = Path.of(System.getProperty("com.fs.starfarer.settings.paths.saves", "../saves")).toAbsolutePath().normalize();
		storage.snapshotCommon(sourceSaves, runtime.resolve("saves"));
		storage.configureGraphics(runtime.resolve("saves"));
		Path logs = storage.directory("diagnostics");
		List<String> launch = new ArrayList<>(command);
		launch.add("-Dcom.fs.starfarer.settings.paths.saves=" + runtime.resolve("saves"));
		launch.add("-Dcom.fs.starfarer.settings.paths.screenshots=" + runtime.resolve("screenshots"));
		launch.add("-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"));
		launch.add("-Dcom.fs.starfarer.settings.paths.logs=" + logs);
		launch.add(BattleNext_SimulatorWorker.class.getName());
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			server.setSoTimeout(1000);
			launch.add(Integer.toString(server.getLocalPort()));
			launch.add(token);
			process = BattleNext_SimulatorLaunch.start(launch, core, logs.resolve("worker-" + (++generation) + "-" + token + ".log"));
			BattleNext_Logger.info(getClass(), "simulator.worker.started", BattleNext_Logger.fields().put("pid", process.pid())
					.put("command", launch.subList(0, launch.size() - 1)).put("runtime", runtime.toString()).build());
			long deadline = System.nanoTime() + 180000000000L;
			Socket socket = null;
			while (socket == null) {
				if (closed || !process.isAlive() || System.nanoTime() >= deadline || cancellationStarted != 0L
						&& System.nanoTime() - cancellationStarted >= 5000000000L) throw new IOException("Simulator cold start failed, was cancelled or exceeded 180 seconds");
				try {
					socket = server.accept();
				} catch (SocketTimeoutException exception) {
					continue;
				}
			}
			socket.setSoTimeout(1000);
			channel = new BattleNext_SimulatorProtocol(socket, token);
			while (true) {
				if (closed || !process.isAlive() || System.nanoTime() >= deadline || cancellationStarted != 0L
						&& System.nanoTime() - cancellationStarted >= 5000000000L) throw new IOException("Simulator initialization cancelled or exceeded 180 seconds");
				try {
					JSONObject ready = channel.receive();
					if (!ready.getString("type").equals("READY")) throw new IOException("Expected simulator READY handshake");
					break;
				} catch (SocketTimeoutException exception) {
					continue;
				}
			}
		}
	}

	private void runTask(BattleNext_SimulationTask task) throws Exception {

		long started = 0L;
		long preparing = System.nanoTime();
		boolean received = false;
		long returning = 0L;
		while (!closed) {
			if (!process.isAlive()) throw new IOException("Simulator worker exited with code " + process.exitValue());
			if (cancellationStarted != 0L && System.nanoTime() - cancellationStarted > 5000000000L) throw new IOException("Simulator cancellation grace period exhausted");
			if (started == 0L && System.nanoTime() - preparing > 180000000000L) throw new IOException("Simulator task preparation exceeded 180 seconds");
			if (received && System.nanoTime() - returning > 30000000000L) throw new IOException("Simulator return to idle exceeded 30 seconds");
			if (!received && started != 0L && System.nanoTime() - started > task.getRequest().config().executionSeconds() * 1000000000d + 5000000000L) {
				acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED, "Worker exceeded execution deadline"));
				throw new IOException("Simulator execution deadline exceeded");
			}
			JSONObject message;
			try {
				message = channel.receive();
			} catch (SocketTimeoutException exception) {
				continue;
			}
			JSONObject payload = message.getJSONObject("payload");
			String type = message.getString("type");
			if (type.equals("READY") && received) return;
			if (!task.getRequest().id().equals(payload.getString("id"))) throw new IOException("Simulator task identity mismatch");
			if (type.equals("STATUS")) {
				BattleNext_SimulationTask.State state = BattleNext_SimulationTask.State.valueOf(payload.getString("state"));
				task.transition(state);
				if (state == BattleNext_SimulationTask.State.RUNNING) {
					started = System.nanoTime();
				}
			} else if (type.equals("RESULT") && !received) {
				acceptResult(task, BattleNext_SimulationResult.fromJson(payload));
				received = true;
				returning = System.nanoTime();
			} else {
				throw new IOException("Unexpected simulator task frame: " + type);
			}
		}
	}

	private BattleNext_SimulationResult failure(BattleNext_SimulationTask task, BattleNext_SimulationResult.Outcome outcome, String reason) {
		return new BattleNext_SimulationResult(task.getRequest().id(), outcome, "", 0f, 0L, 0L, 0L, reason);
	}

	private void acceptResult(BattleNext_SimulationTask task, BattleNext_SimulationResult result) {
		task.receive(result);
		try {
			storage.write(task.getRequest(), result);
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.result.journal.failed", BattleNext_Logger.fields().put("id", result.id()).build(), exception);
		}
		imports.add(task);
	}

	private void importResults() {

		BattleNext_SimulationTask task;
		while ((task = imports.poll()) != null) {
			BattleNext_SimulationResult result = task.getResult();
			if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) continue;
			try {
				BattleNext_BattleReportModel report = result.report();
				boolean retained = BattleNext_BattleResultRecorder.saveStandalone(report);
				storage.acknowledge(result.id());
				task.imported(retained);
				failedImports.remove(result.id());
				BattleNext_Logger.info(getClass(), "simulator.report.imported", BattleNext_Logger.fields().put("result", result.toJson()).put("retained", retained).build());
			} catch (Exception exception) {
				task.rejectImport(exception);
				failedImports.put(result.id(), task);
				BattleNext_Logger.error(getClass(), "simulator.report.import.failed", BattleNext_Logger.fields().put("id", result.id()).build(), exception);
			}
		}
	}

	private synchronized void stopWorker() {

		try {
			if (channel != null) {
				channel.send("SHUTDOWN", new JSONObject());
			}
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.worker.shutdown.failed", BattleNext_Logger.fields().build(), exception);
		}
		try {
			if (process != null && process.isAlive() && !process.waitFor(5L, TimeUnit.SECONDS)) {
				process.destroy();
				if (!process.waitFor(5L, TimeUnit.SECONDS)) {
					process.destroyForcibly().waitFor();
				}
			}
		} catch (InterruptedException exception) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			BattleNext_Logger.error(getClass(), "simulator.worker.termination.interrupted", BattleNext_Logger.fields().build(), exception);
		}
		try {
			if (channel != null) {
				channel.close();
			}
		} catch (IOException exception) {
			BattleNext_Logger.error(getClass(), "simulator.worker.channel.close.failed", BattleNext_Logger.fields().build(), exception);
		} finally {
			channel = null;
			process = null;
		}
		try {
			if (runtime != null) {
				storage.cleanup(runtime);
			}
		} catch (IOException exception) {
			BattleNext_Logger.error(getClass(), "simulator.worker.runtime.cleanup.failed", BattleNext_Logger.fields()
					.put("path", runtime.toString()).build(), exception);
		} finally {
			runtime = null;
		}
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		try {
			if (supervisor != null && supervisor != Thread.currentThread()) {
				supervisor.join();
			} else if (supervisor == null) {
				stopWorker();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
		BattleNext_SimulationTask task;
		while ((task = queue.poll()) != null) {
			acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.CANCELLED, "Simulator service closed"));
		}
	}
}
