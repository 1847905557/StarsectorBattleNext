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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import org.json.JSONException;

public class BattleNext_SimulatorService implements AutoCloseable {

	public enum Phase { NOT_STARTED, IDLE, STOPPED, QUEUED, STARTING, PREPARING, RUNNING, FROZEN, COMMITTING, CLEANING, FINISHED, IMPORT_FAILED, PAUSED }

	private static BattleNext_SimulatorService instance;
	private final LinkedBlockingQueue<BattleNext_SimulationTask> queue = new LinkedBlockingQueue<>();
	private final ConcurrentLinkedQueue<BattleNext_SimulationTask> imports = new ConcurrentLinkedQueue<>();
	private final Map<String, BattleNext_SimulationTask> failedImports = new LinkedHashMap<>();
	private final Map<String, BattleNext_SimulationTask> tournaments = new ConcurrentHashMap<>();
	private final BattleNext_TournamentSnapshotStore snapshotStore = new BattleNext_TournamentSnapshotStore();
	private final ConcurrentLinkedQueue<Checkpoint> checkpoints = new ConcurrentLinkedQueue<>();
	private final BattleNext_SimulatorStorage storage;
	private final Path core;
	private volatile List<String> command;
	private volatile boolean closed;
	private volatile boolean reload;
	private volatile BattleNext_SimulationTask active;
	private volatile BattleNext_SimulationTask latest;
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
		try {
			List<BattleNext_TournamentSnapshot> recovered = storage.tournamentSnapshots();
			for (BattleNext_TournamentSnapshot snapshot : recovered) {
				BattleNext_SimulationTask task = new BattleNext_SimulationTask(snapshot, this);
				if (snapshot.getTournament().isCompleted() && snapshot.getState() != BattleNext_SimulationTask.State.IMPORT_FAILED) {
					task.receive(snapshot.result());
					task.imported(true);
				} else if (snapshot.getState() != BattleNext_SimulationTask.State.IMPORT_FAILED) {
					snapshot.transition(BattleNext_SimulationTask.State.PAUSED, "Awaiting manual resume");
				}
				BattleNext_SimulationTask existing = tournaments.get(snapshot.getSourceId());
				if (existing == null || existing.tournamentSnapshot().getCreatedAt() < snapshot.getCreatedAt()) {
					tournaments.put(snapshot.getSourceId(), task);
				}
				latest = task;
			}
			snapshotStore.restoreCheckpoints(recovered);
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.tournament.recovery.failed", BattleNext_Logger.fields().build(), exception);
		}
		for (Path path : storage.pending()) {
			try {
				BattleNext_SimulatorStorage.Stored stored = storage.read(path);
				if (stored.result().outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) continue;
				BattleNext_SimulationTask task = new BattleNext_SimulationTask(stored.request(), this);
				task.receive(stored.result());
				latest = task;
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

	public BattleNext_SimulationTask submit(BattleNext_SimulationRequest request) {
		if (closed) throw new IllegalStateException("Simulator service is closed");
		if (request.kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
			try {
				String sourceId = request.source().getJSONObject("tournament").getString("id");
				BattleNext_SimulationTask existing = tournaments.get(sourceId);
				if (existing != null && existing.getState() != BattleNext_SimulationTask.State.FINISHED) return existing;
				BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, this);
				tournaments.put(sourceId, task);
				latest = task;
					if (saveTournament(task)) {
						enqueue(task);
					}
				return task;
			} catch (JSONException exception) {
				throw new IllegalArgumentException("Invalid tournament submission: " + request.id(), exception);
			}
		}
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, this);
		enqueue(task);
		return task;
	}

	private void enqueue(BattleNext_SimulationTask task) {
		queue.add(task);
		if (supervisor == null) {
			supervisor = new Thread(this::run, "BattleNext simulator supervisor");
			supervisor.setDaemon(true);
			supervisor.start();
		}
		BattleNext_Logger.info(getClass(), "simulator.task.queued", BattleNext_Logger.fields().put("id", task.getRequest().id())
				.put("request", task.getRequest().toJson()).put("queue_size", queue.size()).build());
	}

	public static BattleNext_SimulationTask tournament(String sourceId) {
		return instance == null ? null : instance.tournaments.get(sourceId);
	}

	public static void deleteTournament(String id) throws Exception {
		BattleNext_SimulatorService service = getInstance();
		for (var entry : service.tournaments.entrySet()) {
			BattleNext_SimulationTask task = entry.getValue();
			if (!task.getRequest().id().equals(id)) continue;
			synchronized (task.tournamentSnapshot()) {
				task.markDeleted();
				service.storage.deleteSnapshot(id);
			}
			service.cancel(task);
			service.queue.remove(task);
			service.tournaments.remove(entry.getKey(), task);
			service.failedImports.remove(id);
			if (service.latest == task) {
				service.latest = null;
			}
		}
		service.snapshotStore.reload();
		service.snapshotStore.delete(id);
		service.storage.deleteSnapshot(id);
	}

	private boolean writeCheckpoint(BattleNext_SimulationTask task) throws IOException, JSONException {
		synchronized (task.tournamentSnapshot()) {
			if (task.isDeleted()) return false;
			storage.writeSnapshot(task.tournamentSnapshot());
			return true;
		}
	}

	public record Status(Phase phase, BattleNext_SimulationRequest current, int queued,
			BattleNext_SimulationRequest recentRequest, BattleNext_SimulationResult result, String importError, boolean retained) { }

	public static Status status() {
		BattleNext_SimulatorService service = instance;
		if (service == null) return new Status(Phase.NOT_STARTED, null, 0, null, null, "", false);
		BattleNext_SimulationTask current = service.active;
		if (current == null) {
			current = service.queue.peek();
		}
		BattleNext_SimulationTask recent = service.latest;
		Phase phase = service.closed ? Phase.STOPPED : current != null ? Phase.valueOf(current.getState().name())
				: recent == null ? Phase.IDLE : Phase.valueOf(recent.getState().name());
		BattleNext_SimulationRequest currentRequest = current == null ? null : current.getRequest();
		if (current != null && current.tournamentSnapshot() != null && current.tournamentSnapshot().getCurrentRound() != null) {
			currentRequest = current.tournamentSnapshot().getCurrentRound();
		}
		return new Status(phase, currentRequest, service.queue.size(),
				recent == null ? null : recent.getRequest(), recent == null ? null : recent.getResult(),
				recent == null ? "" : recent.getImportError(), recent != null && recent.isRetained());
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
				BattleNext_SimulationRequest request = task.tournamentSnapshot() == null ? task.getRequest() : task.tournamentSnapshot().getCurrentRound();
				if (request != null) {
					channel.send("CANCEL", new JSONObject().put("id", request.id()));
				}
			}
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.cancel.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
		}
	}

	void pause(BattleNext_SimulationTask task) {
		if (task.tournamentSnapshot() == null) throw new IllegalStateException("Only tournament simulations can be paused");
		if (task == active) {
			cancel(task);
			return;
		}
		queue.remove(task);
		task.tournamentSnapshot().transition(BattleNext_SimulationTask.State.PAUSED, "Paused by user");
		saveTournament(task);
	}

	void resume(BattleNext_SimulationTask task) {
		if (closed) throw new IllegalStateException("Simulator service is closed");
		if (task.getState() != BattleNext_SimulationTask.State.PAUSED) return;
		task.resetCancellation();
		task.transition(BattleNext_SimulationTask.State.QUEUED);
		if (saveTournament(task)) {
			enqueue(task);
		}
	}

	void retryImport(BattleNext_SimulationTask task) {
		if (task.getState() != BattleNext_SimulationTask.State.IMPORT_FAILED) return;
		if (task.tournamentSnapshot() != null) {
			task.resetCancellation();
			task.transition(BattleNext_SimulationTask.State.QUEUED);
			if (saveTournament(task)) {
				enqueue(task);
			}
			return;
		}
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
					if (task.isDeleted()) continue;
					if (task.tournamentSnapshot() != null) {
						runTournament(task);
						continue;
					}
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
					runTask(task, task.getRequest());
				} catch (Exception exception) {
					if (task.tournamentSnapshot() != null) {
						task.tournamentSnapshot().transition(BattleNext_SimulationTask.State.PAUSED, exception.getClass().getSimpleName() + ": " + exception.getMessage());
						stopWorker();
						if (!task.isDeleted()) {
							checkpoint(task);
						}
						latest = task;
						continue;
					}
					if (task.getResult() == null) {
						acceptResult(task, failure(task, closed || task.isCancelled()
								? BattleNext_SimulationResult.Outcome.CANCELLED : BattleNext_SimulationResult.Outcome.FAILED,
								exception.getClass().getName() + ": " + exception.getMessage()));
					}
					BattleNext_Logger.error(getClass(), "simulator.worker.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
					stopWorker();
				} finally {
					if (closed && task.getResult() == null && task.tournamentSnapshot() == null) {
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

	private void runTournament(BattleNext_SimulationTask task) throws Exception {

		BattleNext_TournamentSnapshot snapshot = task.tournamentSnapshot();
		while (!task.isDeleted()) {
			if (closed || task.isCancelled()) {
				snapshot.transition(BattleNext_SimulationTask.State.PAUSED, "Simulation paused");
				checkpoint(task);
				latest = task;
				return;
			}
			BattleNext_SimulationRequest round = snapshot.prepareRound();
			if (round == null) {
				task.transition(BattleNext_SimulationTask.State.COMMITTING);
				if (!checkpoint(task)) return;
				task.receive(snapshot.result());
				task.imported(true);
				writeCheckpoint(task);
				latest = task;
				return;
			}
			task.transition(BattleNext_SimulationTask.State.PREPARING);
			if (!checkpoint(task)) return;
			if (closed || task.isCancelled() || task.isDeleted()) continue;
			if (reload || process == null || !process.isAlive()) {
				stopWorker();
				reload = false;
				task.transition(BattleNext_SimulationTask.State.STARTING);
				startWorker();
			}
			channel.send("REQUEST", round.toJson());
			if (task.isCancelled()) {
				cancel(task);
			}
			BattleNext_SimulationResult result = runTask(task, round);
			if (task.isDeleted()) return;
			if (result == null) continue;
			if (!checkpoint(task)) return;
			if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) {
				latest = task;
				return;
			}
		}
	}

	private record Checkpoint(BattleNext_SimulationTask task, CompletableFuture<Boolean> completed) { }

	private boolean checkpoint(BattleNext_SimulationTask task) {
		if (task.isDeleted()) return false;
		try {
			if (!writeCheckpoint(task)) return false;
			if (closed) return false;
			CompletableFuture<Boolean> saved = new CompletableFuture<>();
			checkpoints.add(new Checkpoint(task, saved));
			while (!saved.isDone() && !closed && !task.isDeleted()) {
				Thread.sleep(20L);
			}
			return saved.isDone() && saved.join();
		} catch (Exception exception) {
			task.rejectImport(exception);
			BattleNext_Logger.error(getClass(), "simulator.tournament.checkpoint.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
			return false;
		}
	}

	private boolean saveTournament(BattleNext_SimulationTask task) {
		if (task.isDeleted()) return false;
		try {
			if (!writeCheckpoint(task)) return false;
			BattleNext_TournamentSnapshot published = task.getTournamentSnapshot();
			if (published.getTournament().isCompleted() && published.getState() == BattleNext_SimulationTask.State.COMMITTING) {
				published.transition(BattleNext_SimulationTask.State.FINISHED, "");
			}
			snapshotStore.save(published);
			failedImports.remove(task.getRequest().id());
			return true;
		} catch (Exception exception) {
			task.rejectImport(exception);
			failedImports.put(task.getRequest().id(), task);
			try {
				writeCheckpoint(task);
			} catch (Exception failure) {
				BattleNext_Logger.error(getClass(), "simulator.tournament.journal.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), failure);
			}
			BattleNext_Logger.error(getClass(), "simulator.tournament.save.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
			return false;
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

	private BattleNext_SimulationResult runTask(BattleNext_SimulationTask task, BattleNext_SimulationRequest request) throws Exception {

		long started = 0L;
		long preparing = System.nanoTime();
		boolean received = false;
		long returning = 0L;
		BattleNext_SimulationResult result = null;
		while (!closed) {
			if (!process.isAlive()) throw new IOException("Simulator worker exited with code " + process.exitValue());
			if (cancellationStarted != 0L && System.nanoTime() - cancellationStarted > 5000000000L) throw new IOException("Simulator cancellation grace period exhausted");
			if (started == 0L && System.nanoTime() - preparing > 180000000000L) throw new IOException("Simulator task preparation exceeded 180 seconds");
			if (received && System.nanoTime() - returning > 30000000000L) throw new IOException("Simulator return to idle exceeded 30 seconds");
			if (!received && started != 0L && System.nanoTime() - started > request.config().executionSeconds() * 1000000000d + 5000000000L) {
				if (task.tournamentSnapshot() == null) {
					acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED, "Worker exceeded execution deadline"));
				}
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
			if (type.equals("READY") && received) return result;
			if (!request.id().equals(payload.getString("id"))) throw new IOException("Simulator task identity mismatch");
			if (type.equals("STATUS")) {
				BattleNext_SimulationTask.State state = BattleNext_SimulationTask.State.valueOf(payload.getString("state"));
				task.transition(state);
				if (state == BattleNext_SimulationTask.State.RUNNING) {
					started = System.nanoTime();
				}
			} else if (type.equals("RESULT") && !received) {
				result = BattleNext_SimulationResult.fromJson(payload);
				if (task.tournamentSnapshot() == null) {
					acceptResult(task, result);
				} else if (!task.isDeleted()) {
					task.tournamentSnapshot().record(result);
					writeCheckpoint(task);
				}
				received = true;
				returning = System.nanoTime();
			} else {
				throw new IOException("Unexpected simulator task frame: " + type);
			}
		}
		return result;
	}

	private BattleNext_SimulationResult failure(BattleNext_SimulationTask task, BattleNext_SimulationResult.Outcome outcome, String reason) {
		return new BattleNext_SimulationResult(task.getRequest().id(), outcome, "", 0f, 0L, 0L, 0L, reason);
	}

	private void acceptResult(BattleNext_SimulationTask task, BattleNext_SimulationResult result) {
		task.receive(result);
		latest = task;
		try {
			storage.write(task.getRequest(), result);
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.result.journal.failed", BattleNext_Logger.fields().put("id", result.id()).build(), exception);
		}
		imports.add(task);
	}

	private void importResults() {

		Checkpoint checkpoint;
		while ((checkpoint = checkpoints.poll()) != null) {
			checkpoint.completed().complete(saveTournament(checkpoint.task()));
		}
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
			if (task.tournamentSnapshot() == null) {
				acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.CANCELLED, "Simulator service closed"));
			} else if (!task.isDeleted()) {
				task.tournamentSnapshot().transition(BattleNext_SimulationTask.State.PAUSED, "Application stopped");
				try {
					writeCheckpoint(task);
				} catch (Exception exception) {
					BattleNext_Logger.error(getClass(), "simulator.tournament.shutdown.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
				}
			}
		}
	}
}
