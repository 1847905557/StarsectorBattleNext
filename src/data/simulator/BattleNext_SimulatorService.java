package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import data.missions.BattleNext_MissionBattleMap;
import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_TournamentEngine;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class BattleNext_SimulatorService implements AutoCloseable {

	public enum Phase { NOT_STARTED, IDLE, STOPPED, QUEUED, STARTING, PREPARING, RUNNING, FROZEN, COMMITTING, CLEANING, FINISHED, IMPORT_FAILED, PAUSED }

	private static BattleNext_SimulatorService instance;
	private final LinkedBlockingQueue<BattleNext_SimulationTask> queue = new LinkedBlockingQueue<>();
	private final ConcurrentLinkedQueue<BattleNext_SimulationTask> imports = new ConcurrentLinkedQueue<>();
	private final Map<String, BattleNext_SimulationTask> failedImports = new LinkedHashMap<>();
	private final Map<String, BattleNext_SimulationTask> tournaments = new ConcurrentHashMap<>();
	private final BattleNext_StartedTournamentStore startedStore = new BattleNext_StartedTournamentStore();
	private final ConcurrentLinkedQueue<Checkpoint> checkpoints = new ConcurrentLinkedQueue<>();
	private final Map<String, BattleNext_SimulationTask> deletions = new ConcurrentHashMap<>();
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
		storage = new BattleNext_SimulatorStorage(mod.resolve("tournament/_simulations"));
		command = buildCommand();
		try {
			startedStore.reload();
			for (JSONObject json : storage.readTasks()) {
				try {
					BattleNext_SimulationTask task = BattleNext_SimulationTask.fromJson(json, this, startedStore);
					if (task.isDeletionRequested()) {
						deletions.put(task.getRequest().id(), task);
						tournaments.put(task.getRequest().id(), task);
						continue;
					}
					if (task.getRequest().kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
						if (task.getState() != BattleNext_SimulationTask.State.FINISHED && task.getState() != BattleNext_SimulationTask.State.IMPORT_FAILED) {
							task.interrupted("Awaiting manual resume");
						}
						tournaments.put(task.getRequest().id(), task);
					} else if (task.getState() != BattleNext_SimulationTask.State.FINISHED && task.getResult() != null) {
						imports.add(task);
					}
					latest = task;
				} catch (JSONException | IllegalArgumentException exception) {
					storage.rejectTask(json, exception);
				}
			}
			for (Path path : storage.pending()) {
				try {
					BattleNext_SimulatorStorage.Stored stored = storage.read(path);
					if (stored.result().outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) continue;
					boolean alreadyPending = imports.stream().anyMatch(task -> task.getRequest().id().equals(stored.request().id()));
					if (alreadyPending) continue;
					BattleNext_SimulationTask task = new BattleNext_SimulationTask(stored.request(), this);
					task.receive(stored.result());
					imports.add(task);
				} catch (IOException | JSONException | IllegalArgumentException exception) {
					BattleNext_Logger.warn(getClass(), "simulator.pending.item.rejected", BattleNext_Logger.fields().put("path", path.toString()).build(), exception);
				}
			}
		} catch (IOException | JSONException exception) {
			BattleNext_Logger.error(getClass(), "simulator.tasks.recovery.failed", BattleNext_Logger.fields().build(), exception);
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
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(request, this);
		try {
			if (request.kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
				BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
				String sourceId = request.source().getString("sourceTournamentId");
				List<String> fleetIds = request.tournamentFleetIds();
				BattleNext_BattleValidation.Result result = sources.checkTournament(sourceId, fleetIds);
				if (!result.failures().isEmpty()) throw new BattleNext_BattleValidation.Rejection(sourceId, result);
				BattleNext_TournamentModel configuration = sources.getTournamentStore().get(sourceId);
				BattleNext_StartedTournament started = new BattleNext_StartedTournament(request.id(), configuration.getId(),
						BattleNext_StartedTournament.ExecutionKind.SIMULATION, fleetIds);
				new BattleNext_TournamentEngine().initialize(configuration, started);
				task.publishStarted(started);
				storage.writeTask(task);
				startedStore.save(started);
				tournaments.put(request.id(), task);
			}
			storage.writeTask(task);
		} catch (BattleNext_BattleValidation.Rejection rejection) {
			throw rejection;
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to submit simulator task: " + request.id(), exception);
		}
		latest = task;
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

	public static BattleNext_SimulationTask tournament(String instanceId) {
		return instance == null ? null : instance.tournaments.get(instanceId);
	}

	public static void deleteTournament(String id) throws IOException, JSONException {
		BattleNext_StartedTournamentStore store = new BattleNext_StartedTournamentStore();
		store.reload();
		BattleNext_StartedTournament selected = store.get(id);
		if (selected != null && selected.getExecutionKind() == BattleNext_StartedTournament.ExecutionKind.MANUAL) {
			store.delete(id);
			return;
		}
		BattleNext_SimulatorService service = getInstance();
		BattleNext_SimulationTask task = service.tournaments.get(id);
		if (task == null) {
			store.delete(id);
			return;
		}
		synchronized (service) {
			task.requestDeletion();
			service.deletions.put(id, task);
			try {
				service.storage.writeTask(task);
			} finally {
				service.queue.remove(task);
				if (task == service.active) {
					service.cancel(task);
				}
			}
		}
		service.finishDeletions();
	}

	public static List<BattleNext_SimulationTask> pendingDeletions(String sourceId) {
		if (instance == null) return List.of();
		return instance.tournaments.values().stream().filter(task -> task.isDeletionRequested()
				&& task.getStartedTournament().getSourceTournamentId().equals(sourceId)).toList();
	}

	public static void deleteInvalidTournament(BattleNext_StartedTournamentStore.CatalogEntry entry) throws IOException, JSONException {
		BattleNext_StartedTournamentStore store = new BattleNext_StartedTournamentStore();
		store.delete(entry);
		if (store.getCatalog().stream().anyMatch(other -> other.id().equals(entry.id()))) return;
		BattleNext_SimulationTask task = tournament(entry.id());
		if (task != null && task.getStartedTournament().getSourceTournamentId().equals(entry.sourceTournamentId())) {
			deleteTournament(entry.id());
			return;
		}
		Path mod = Path.of(Global.getSettings().getModManager().getModSpec("AI_Battles_Next").getPath()).toAbsolutePath();
		BattleNext_SimulatorStorage storage = new BattleNext_SimulatorStorage(mod.resolve("tournament/_simulations"));
		for (JSONObject json : storage.readTasks()) {
			JSONObject request = json.getJSONObject("request");
			if (!request.getString("id").equals(entry.id())) continue;
			if (!request.optString("kind", "").equals(BattleNext_SimulationRequest.Kind.TOURNAMENT.name())) continue;
			JSONObject source = request.optJSONObject("source");
			if (source == null || !source.optString("sourceTournamentId", "").equals(entry.sourceTournamentId())) continue;
			JSONObject round = json.optJSONObject("currentRound");
			if (round != null && BattleNext_EntryId.isValidPathSegment(round.optString("id", ""))) {
				storage.acknowledge(round.getString("id"));
			}
			storage.deleteTask(entry.id());
		}
	}

	private void finishDeletions() {
		for (BattleNext_SimulationTask task : List.copyOf(deletions.values())) {
			if (task == active) continue;
			try {
				storage.writeTask(task);
				startedStore.reload();
				if (task.getCurrentRound() != null) {
					storage.acknowledge(task.getCurrentRound().id());
				}
				if (startedStore.get(task.getRequest().id()) != null) {
					startedStore.delete(task.getRequest().id());
				}
				storage.deleteTask(task.getRequest().id());
				tournaments.remove(task.getRequest().id());
				deletions.remove(task.getRequest().id());
				failedImports.remove(task.getRequest().id());
				if (latest == task) {
					latest = null;
				}
			} catch (Exception exception) {
				task.rejectImport(exception);
				deletions.remove(task.getRequest().id());
				BattleNext_Logger.error(getClass(), "simulator.tournament.delete.failed",
						BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
			}
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
		if (current != null && current.getCurrentRound() != null) {
			currentRequest = current.getCurrentRound();
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
				BattleNext_SimulationRequest request = task.getRequest().kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT ? task.getRequest() : task.getCurrentRound();
				if (request != null) {
					channel.send("CANCEL", new JSONObject().put("id", request.id()));
				}
			}
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.cancel.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
		}
	}

	void pause(BattleNext_SimulationTask task) {
		if (task.getRequest().kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT) throw new IllegalStateException("Only tournament simulations can be paused");
		if (task == active) {
			cancel(task);
			return;
		}
		queue.remove(task);
		task.interrupted("Paused by user");
		persistTask(task);
	}

	void resume(BattleNext_SimulationTask task) {
		if (closed) throw new IllegalStateException("Simulator service is closed");
		if (task.getState() != BattleNext_SimulationTask.State.PAUSED) return;
		task.resetCancellation();
		task.transition(BattleNext_SimulationTask.State.QUEUED);
		if (persistTask(task)) {
			enqueue(task);
		}
	}

	void retryImport(BattleNext_SimulationTask task) {
		if (task.getState() != BattleNext_SimulationTask.State.IMPORT_FAILED) return;
		if (task.getRequest().kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
			task.resetCancellation();
			task.transition(BattleNext_SimulationTask.State.QUEUED);
			if (persistTask(task)) {
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
		List<String> launch = BattleNext_SimulatorLaunch.javaCommand(core, storage.directory("_diagnostics"));
		launch.add("-cp");
		launch.add(String.join(System.getProperty("path.separator"), classpath));
		return List.copyOf(launch);
	}

	private void run() {

		try {
			while (!closed) {
				BattleNext_SimulationTask task = queue.poll(100L, TimeUnit.MILLISECONDS);
				if (task == null) continue;
				synchronized (this) {
					if (task.isDeletionRequested()) continue;
					active = task;
					cancellationStarted = 0L;
				}
				try {
					if (task.getRequest().kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
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
					if (task.getRequest().kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
						task.interrupted(exception.getClass().getSimpleName() + ": " + exception.getMessage());
						stopWorker();
						persistTask(task);
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
					if (closed && task.getResult() == null && task.getRequest().kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT) {
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
		if (!checkpoint(task)) return;
		while (!closed && !task.isCancelled()) {
			if (task.getState() == BattleNext_SimulationTask.State.FINISHED) return;
			BattleNext_SimulationRequest round = task.getCurrentRound();
			if (closed || task.isCancelled()) break;
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
			if (result == null) break;
			if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) {
				task.interrupted(result.outcome().name() + ": " + result.diagnostic());
				persistTask(task);
				latest = task;
				return;
			}
			if (!checkpoint(task)) return;
			if (task.getState() == BattleNext_SimulationTask.State.FINISHED) return;
		}
		task.interrupted("Simulation paused");
		persistTask(task);
		latest = task;
	}

	private record Checkpoint(BattleNext_SimulationTask task, CompletableFuture<Boolean> completed) { }

	private boolean checkpoint(BattleNext_SimulationTask task) throws Exception {
		CompletableFuture<Boolean> saved = new CompletableFuture<>();
		checkpoints.add(new Checkpoint(task, saved));
		while (!saved.isDone() && !closed) {
			Thread.sleep(20L);
		}
		return saved.isDone() && saved.join();
	}

	private boolean persistTask(BattleNext_SimulationTask task) {
		try {
			storage.writeTask(task);
			return true;
		} catch (Exception exception) {
			task.rejectImport(exception);
			BattleNext_Logger.error(getClass(), "simulator.task.save.failed",
					BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
			return false;
		}
	}

	private boolean prepareTournament(BattleNext_SimulationTask task) {
		if (task.isDeletionRequested()) return false;
		try {
			BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
			List<String> errors = sources.reloadTournamentCatalog();
			if (!errors.isEmpty()) throw new IllegalStateException(String.join("; ", errors));
			startedStore.reload();
			BattleNext_StartedTournament started = startedStore.get(task.getRequest().id());
			if (started == null) {
				throw new IllegalStateException("Missing started tournament instance: " + task.getRequest().id());
			}
			BattleNext_TournamentModel configuration = sources.getTournamentStore().get(started.getSourceTournamentId());
			if (configuration == null) throw new IllegalArgumentException("Missing tournament configuration: " + started.getSourceTournamentId());
			BattleNext_SimulationResult pending = task.pendingRound();
			if (pending != null) {
				BattleNext_Match match = BattleNext_Match.fromJson(task.getCurrentRound().source().getJSONObject("match"));
				String winner = switch (pending.report().getWinner()) {
					case "player" -> match.getPlayer();
					case "enemy" -> match.getEnemy();
					default -> "";
				};
				BattleNext_BattleReportModel report = pending.report();
				BattleNext_BattleResultRecorder.prepareTournamentRoundReport(report, started.getName(configuration), match,
						sources.getFleetStore().get(match.getPlayer()).getName(), sources.getFleetStore().get(match.getEnemy()).getName());
				BattleNext_BattleResultRecorder.saveStandalone(report);
				new BattleNext_TournamentEngine().recordRound(configuration, started, match.getId(), winner, report.getId());
				startedStore.save(started);
				task.publishStarted(started);
				task.committedRound();
				storage.writeTask(task);
			}
			task.publishStarted(started);
			if (started.isCompleted()) {
				task.completeTournament();
				storage.writeTask(task);
				failedImports.remove(task.getRequest().id());
				latest = task;
				return true;
			}
			if (task.isCancelled()) {
				task.interrupted("Simulation paused");
				storage.writeTask(task);
				return true;
			}
			BattleNext_Match match = started.getNextPendingMatch();
			List<String> reasons = new ArrayList<>();
			reasons.addAll(sources.getValidation().ruleResult(configuration.getRuleId(), sources.getRuleStore()).messages());
			reasons.addAll(sources.getValidation().fleetRuleResult(match.getPlayer(), configuration.getRuleId(), sources.getRuleStore()).messages());
			reasons.addAll(sources.getValidation().fleetRuleResult(match.getEnemy(), configuration.getRuleId(), sources.getRuleStore()).messages());
			if (!reasons.isEmpty()) throw new IllegalArgumentException(String.join("; ", reasons));
			int actualMap = task.getCurrentRound() == null ? BattleNext_MissionBattleMap.resolve(configuration.getMapId())
					: task.getCurrentRound().source().getInt("actualMap");
			task.prepareRound(task.getRequest().forMatch(configuration, started, match, sources.getFleetStore(), actualMap));
			storage.writeTask(task);
			failedImports.remove(task.getRequest().id());
			return true;
		} catch (Exception exception) {
			if (task.pendingRound() != null) {
				task.rejectImport(exception);
				failedImports.put(task.getRequest().id(), task);
			} else {
				task.interrupted(exception.toString());
			}
			persistTask(task);
			BattleNext_Logger.error(getClass(), "simulator.tournament.prepare.failed",
					BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
			return false;
		}
	}

	private void startWorker() throws Exception {

		String token = UUID.randomUUID().toString();
		runtime = storage.directory("_runtime/" + UUID.randomUUID());
		Path logs = storage.directory("_diagnostics");
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
				if (task.getRequest().kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT) {
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
				if (task.getRequest().kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT) {
					acceptResult(task, result);
				} else if (result.outcome() == BattleNext_SimulationResult.Outcome.COMPLETED) {
					task.receiveRound(result);
					storage.writeTask(task);
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
			storage.writeTask(task);
		} catch (Exception exception) {
			BattleNext_Logger.error(getClass(), "simulator.result.journal.failed", BattleNext_Logger.fields().put("id", result.id()).build(), exception);
		}
		imports.add(task);
	}

	private void importResults() {
		finishDeletions();

		Checkpoint checkpoint;
		while ((checkpoint = checkpoints.poll()) != null) {
			checkpoint.completed().complete(prepareTournament(checkpoint.task()));
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
				storage.writeTask(task);
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
			if (task.getRequest().kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT) {
				acceptResult(task, failure(task, BattleNext_SimulationResult.Outcome.CANCELLED, "Simulator service closed"));
			} else {
				task.interrupted("Application stopped");
				try {
					storage.writeTask(task);
				} catch (Exception exception) {
					BattleNext_Logger.error(getClass(), "simulator.tournament.shutdown.failed", BattleNext_Logger.fields().put("id", task.getRequest().id()).build(), exception);
				}
			}
		}
	}
}
