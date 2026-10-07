package data.simulator;

import data.scripts.tournament.BattleNext_StartedTournament;
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
	private final List<BattleNext_SimulationResult> rounds = new ArrayList<>();

	BattleNext_SimulationTask(BattleNext_SimulationRequest request, BattleNext_SimulatorService service) {
		this.request = request;
		this.service = service;
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

	public synchronized List<BattleNext_SimulationResult> getRoundResults() {
		return List.copyOf(rounds);
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

	synchronized void committedRound() {
		rounds.add(pendingRound);
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
		transition(result.reportJson().isEmpty() ? State.FINISHED : State.COMMITTING);
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
