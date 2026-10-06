package data.simulator;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_SimulationTask {

	public enum State { QUEUED, STARTING, PREPARING, RUNNING, FROZEN, COMMITTING, CLEANING, FINISHED, IMPORT_FAILED, PAUSED }

	private final BattleNext_SimulationRequest request;
	private final BattleNext_SimulatorService service;
	private final BattleNext_TournamentSnapshot snapshot;
	private volatile State state = State.QUEUED;
	private volatile BattleNext_SimulationResult result;
	private volatile boolean cancelled;
	private volatile boolean deleted;
	private volatile boolean retained;
	private volatile String importError = "";
	private final List<State> history = new ArrayList<>(List.of(State.QUEUED));

	BattleNext_SimulationTask(BattleNext_SimulationRequest request, BattleNext_SimulatorService service) {
		this.request = request;
		this.service = service;
		snapshot = request.kind() == BattleNext_SimulationRequest.Kind.TOURNAMENT ? BattleNext_TournamentSnapshot.create(request) : null;
	}

	BattleNext_SimulationTask(BattleNext_TournamentSnapshot snapshot, BattleNext_SimulatorService service) {
		request = snapshot.getRequest();
		this.service = service;
		this.snapshot = snapshot;
		state = snapshot.getState();
	}

	public BattleNext_SimulationRequest getRequest() {
		return request;
	}

	public State getState() {
		return snapshot == null ? state : snapshot.getState();
	}

	public BattleNext_TournamentSnapshot getTournamentSnapshot() {
		return snapshot == null ? null : snapshot.copy();
	}

	BattleNext_TournamentSnapshot tournamentSnapshot() {
		return snapshot;
	}

	public void pause() {
		cancelled = true;
		service.pause(this);
	}

	void markDeleted() {
		deleted = true;
		cancelled = true;
	}

	boolean isDeleted() {
		return deleted;
	}

	public void resume() {
		service.resume(this);
	}

	void resetCancellation() {
		cancelled = false;
		result = null;
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
		if (snapshot != null) {
			pause();
			return;
		}
		if (result != null) return;
		cancelled = true;
		service.cancel(this);
	}

	public void retryImport() {
		service.retryImport(this);
	}

	boolean isCancelled() {
		return cancelled;
	}

	synchronized void transition(State next) {
		if (getState() == next) return;
		state = next;
		if (snapshot != null) {
			snapshot.transition(next, "");
		}
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
		if (snapshot != null) {
			snapshot.transition(State.IMPORT_FAILED, importError);
		}
	}
}
