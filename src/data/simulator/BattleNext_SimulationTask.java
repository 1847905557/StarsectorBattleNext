package data.simulator;

import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_SimulationTask {

	public enum State { QUEUED, STARTING, PREPARING, RUNNING, FROZEN, COMMITTING, CLEANING, FINISHED, IMPORT_FAILED, PAUSED }

	private final BattleNext_SimulationRequest request;
	private final BattleNext_SimulatorService service;
	private volatile BattleNext_StartedTournament started;
	private volatile State state = State.QUEUED;
	private volatile BattleNext_SimulationResult result;
	private volatile BattleNext_SimulationRequest currentRound;
	private volatile BattleNext_SimulationResult pendingRound;
	private volatile boolean cancelled;
	private volatile boolean deletionRequested;
	private volatile boolean retained;
	private volatile String importError = "";
	private volatile long version;
	private float simulationSeconds;
	private long executionNanos;
	private long steps;
	private long peakHeapBytes;
	private final List<State> history = new ArrayList<>(List.of(State.QUEUED));

	BattleNext_SimulationTask(BattleNext_SimulationRequest request, BattleNext_SimulatorService service) {
		this.request = request;
		this.service = service;
	}

	public static BattleNext_SimulationTask fromJson(JSONObject json, BattleNext_SimulatorService service,
			BattleNext_StartedTournamentStore store) throws JSONException {
		BattleNext_SimulationTask task = new BattleNext_SimulationTask(BattleNext_SimulationRequest.fromJson(json.getJSONObject("request")), service);
		task.state = State.valueOf(json.getString("state"));
		task.importError = json.getString("diagnostic");
		task.version = json.getLong("version");
		task.retained = json.getBoolean("retained");
		task.deletionRequested = json.optBoolean("deletionRequested", false);
		task.simulationSeconds = (float) json.getDouble("simulationSeconds");
		task.executionNanos = json.getLong("executionNanos");
		task.steps = json.getLong("steps");
		task.peakHeapBytes = json.getLong("peakHeapBytes");
		task.started = json.has("startedTournamentId") ? store.get(json.getString("startedTournamentId")) : null;
		if (task.deletionRequested) {
			task.started = new BattleNext_StartedTournament();
			task.started.fromJson(json.getJSONObject("deletionInstance"));
		}
		if (task.request.kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
			if (task.started == null) throw new IllegalArgumentException("Simulator tournament instance is unavailable: " + task.request.id());
			if (!task.started.getId().equals(task.request.id()) || task.started.getExecutionKind() != BattleNext_StartedTournament.ExecutionKind.SIMULATION
					|| !task.started.getSourceTournamentId().equals(task.request.source().getString("sourceTournamentId"))) {
				throw new IllegalArgumentException("Simulator task and tournament ownership differ: " + task.request.id());
			}
		}
		if (json.has("result")) {
			task.result = BattleNext_SimulationResult.fromJson(json.getJSONObject("result"));
		}
		if (json.has("currentRound")) {
			task.currentRound = BattleNext_SimulationRequest.fromJson(json.getJSONObject("currentRound"));
		}
		if (json.has("pendingRound")) {
			task.pendingRound = BattleNext_SimulationResult.fromJson(json.getJSONObject("pendingRound"));
		}
		return task;
	}

	public synchronized JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject().put("request", request.toJson()).put("state", state.name())
				.put("diagnostic", importError).put("retained", retained).put("version", version).put("deletionRequested", deletionRequested)
				.put("simulationSeconds", simulationSeconds).put("executionNanos", executionNanos)
				.put("steps", steps).put("peakHeapBytes", peakHeapBytes);
		if (started != null) {
			json.put("startedTournamentId", started.getId());
		}
		if (deletionRequested) {
			json.put("deletionInstance", started.toJson());
		}
		if (result != null) {
			json.put("result", result.toJson());
		}
		if (currentRound != null) {
			json.put("currentRound", currentRound.toJson());
		}
		if (pendingRound != null) {
			json.put("pendingRound", pendingRound.toJson());
		}
		return json;
	}

	public BattleNext_SimulationRequest getRequest() {
		return request;
	}

	public State getState() {
		return state;
	}

	public long getVersion() {
		return version;
	}

	public record Metrics(float simulationSeconds, long executionNanos, long steps, long peakHeapBytes) { }

	public synchronized Metrics getMetrics() {
		return new Metrics(simulationSeconds, executionNanos, steps, peakHeapBytes);
	}

	public BattleNext_StartedTournament getStartedTournament() {
		return started == null ? null : started.copy();
	}

	void publishStarted(BattleNext_StartedTournament started) {
		this.started = started.copy();
		version++;
	}

	public BattleNext_SimulationRequest getCurrentRound() {
		return currentRound;
	}

	void prepareRound(BattleNext_SimulationRequest round) {
		currentRound = round;
		transition(State.PREPARING);
		version++;
	}

	synchronized void receiveRound(BattleNext_SimulationResult completed) {
		if (deletionRequested) return;
		pendingRound = completed;
		transition(State.COMMITTING);
	}

	BattleNext_SimulationResult pendingRound() {
		return pendingRound;
	}

	void committedRound() {
		simulationSeconds += pendingRound.simulationSeconds();
		executionNanos += pendingRound.executionNanos();
		steps += pendingRound.steps();
		peakHeapBytes = Math.max(peakHeapBytes, pendingRound.peakHeapBytes());
		pendingRound = null;
		currentRound = null;
		version++;
	}

	void completeTournament() throws JSONException {
		result = new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				"", started.toJson().toString(), simulationSeconds, executionNanos, steps, peakHeapBytes, "");
		imported(true);
	}

	public void pause() {
		cancelled = true;
		service.pause(this);
	}

	public void resume() {
		if (deletionRequested) throw new IllegalStateException("Tournament deletion is pending: " + request.id());
		service.resume(this);
	}

	public boolean isDeletionRequested() {
		return deletionRequested;
	}

	synchronized void requestDeletion() {
		deletionRequested = true;
		cancelled = true;
		pendingRound = null;
		version++;
	}

	void resetCancellation() {
		cancelled = false;
		importError = "";
	}

	public synchronized List<State> getStateHistory() {
		return List.copyOf(history);
	}

	public BattleNext_SimulationResult getResult() {
		return result;
	}

	public boolean isRetained() {
		return retained;
	}

	public String getImportError() {
		return importError;
	}

	public void cancel() {
		if (request.kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT) {
			pause();
			return;
		}
		if (result != null) return;
		cancelled = true;
		service.cancel(this);
	}

	public void retryImport() {
		if (deletionRequested) throw new IllegalStateException("Tournament deletion is pending: " + request.id());
		service.retryImport(this);
	}

	boolean isCancelled() {
		return cancelled;
	}

	synchronized void transition(State next) {
		if (state == next) return;
		state = next;
		version++;
		history.add(next);
	}

	void receive(BattleNext_SimulationResult result) {
		this.result = result;
		transition(result.outcome() == BattleNext_SimulationResult.Outcome.COMPLETED ? State.COMMITTING : State.FINISHED);
	}

	void imported(boolean retained) {
		this.retained = retained;
		importError = "";
		transition(State.FINISHED);
	}

	void rejectImport(Exception exception) {
		importError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
		transition(State.IMPORT_FAILED);
	}

	void interrupted(String diagnostic) {
		importError = diagnostic;
		transition(State.PAUSED);
	}
}
