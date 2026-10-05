package data.simulator;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_SimulationTask {

	public enum State { QUEUED, STARTING, PREPARING, RUNNING, FROZEN, COMMITTING, CLEANING, FINISHED, IMPORT_FAILED }

	private final BattleNext_SimulationRequest request;
	private final BattleNext_SimulatorService service;
	private volatile State state = State.QUEUED;
	private volatile BattleNext_SimulationResult result;
	private volatile boolean cancelled;
	private volatile boolean retained;
	private volatile String importError = "";
	private final List<State> history = new ArrayList<>(List.of(State.QUEUED));

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
		if (state == next) return;
		state = next;
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
}
