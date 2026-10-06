package data.simulator;

import data.model.BattleNext_JsonModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_TournamentEngine;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TournamentSnapshot extends BattleNext_JsonModel {

	private BattleNext_SimulationRequest request;
	private BattleNext_TournamentModel tournament;
	private BattleNext_SimulationRequest currentRound;
	private BattleNext_SimulationTask.State state;
	private String diagnostic = "";
	private long version;
	private long createdAt;
	private float simulationSeconds;
	private long executionNanos;
	private long steps;
	private long peakHeapBytes;

	public static BattleNext_TournamentSnapshot create(BattleNext_SimulationRequest request) {
		try {
			BattleNext_TournamentSnapshot snapshot = new BattleNext_TournamentSnapshot();
			snapshot.request = request;
			snapshot.tournament = new BattleNext_TournamentModel();
			snapshot.tournament.fromJson(request.source().getJSONObject("tournament"));
			snapshot.tournament.setId(request.id());
			snapshot.tournament.setName("Simulation " + snapshot.tournament.getName() + " - " + request.id());
			snapshot.tournament.setStarted(false);
			new BattleNext_TournamentEngine().start(snapshot.tournament);
			snapshot.state = BattleNext_SimulationTask.State.QUEUED;
			snapshot.createdAt = System.currentTimeMillis();
			return snapshot;
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Invalid tournament simulation request: " + request.id(), exception);
		}
	}

	@Override
	public synchronized void fromJson(JSONObject json) throws JSONException {
		request = BattleNext_SimulationRequest.fromJson(json.getJSONObject("request"));
		tournament = new BattleNext_TournamentModel();
		tournament.fromJson(json.getJSONObject("tournament"));
		currentRound = json.has("currentRound") ? BattleNext_SimulationRequest.fromJson(json.getJSONObject("currentRound")) : null;
		state = BattleNext_SimulationTask.State.valueOf(json.getString("state"));
		diagnostic = json.getString("diagnostic");
		version = json.getLong("version");
		createdAt = json.getLong("createdAt");
		simulationSeconds = (float) json.getDouble("simulationSeconds");
		executionNanos = json.getLong("executionNanos");
		steps = json.getLong("steps");
		peakHeapBytes = json.getLong("peakHeapBytes");
		if (request.kind() != BattleNext_SimulationRequest.Kind.TOURNAMENT || !request.id().equals(tournament.getId())) {
			throw new IllegalArgumentException("Tournament snapshot identity mismatch: " + request.id());
		}
	}

	@Override
	public synchronized JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject().put("request", request.toJson()).put("tournament", tournament.toJson())
				.put("state", state.name()).put("diagnostic", diagnostic).put("version", version).put("createdAt", createdAt)
				.put("simulationSeconds", simulationSeconds).put("executionNanos", executionNanos)
				.put("steps", steps).put("peakHeapBytes", peakHeapBytes);
		if (currentRound != null) {
			json.put("currentRound", currentRound.toJson());
		}
		return json;
	}

	public synchronized BattleNext_TournamentSnapshot copy() {
		try {
			BattleNext_TournamentSnapshot copy = new BattleNext_TournamentSnapshot();
			copy.fromJson(new JSONObject(toJson().toString()));
			return copy;
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to copy tournament snapshot: " + getId(), exception);
		}
	}

	@Override
	public String getId() {
		return request.id();
	}

	public BattleNext_SimulationRequest getRequest() {
		return request;
	}

	public String getSourceId() {
		try {
			return request.source().getJSONObject("tournament").getString("id");
		} catch (JSONException exception) {
			throw new IllegalStateException("Invalid tournament snapshot source: " + getId(), exception);
		}
	}

	public synchronized BattleNext_TournamentModel getTournament() {
		try {
			BattleNext_TournamentModel model = new BattleNext_TournamentModel();
			model.fromJson(tournament.toJson());
			return model;
		} catch (JSONException exception) {
			throw new IllegalStateException("Invalid tournament snapshot progress: " + getId(), exception);
		}
	}

	public Map<String, String> getFleetNames() {
		try {
			JSONObject fleets = request.source().getJSONObject("fleets");
			Map<String, String> names = new LinkedHashMap<>();
			for (String id : JSONObject.getNames(fleets)) {
				names.put(id, fleets.getJSONObject(id).getJSONObject("definition").getString("name"));
			}
			return Map.copyOf(names);
		} catch (JSONException exception) {
			throw new IllegalStateException("Invalid tournament snapshot participants: " + getId(), exception);
		}
	}

	public synchronized long getVersion() {
		return version;
	}

	public synchronized BattleNext_SimulationTask.State getState() {
		return state;
	}

	public synchronized String getDiagnostic() {
		return diagnostic;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public synchronized BattleNext_SimulationRequest getCurrentRound() {
		return currentRound;
	}

	public synchronized void transition(BattleNext_SimulationTask.State next, String reason) {
		if (state == next && diagnostic.equals(reason)) return;
		state = next;
		diagnostic = reason;
		version++;
	}

	public synchronized BattleNext_SimulationRequest prepareRound() {
		if (currentRound != null) return currentRound;
		BattleNext_Match match = new BattleNext_TournamentEngine().getNextMatch(tournament);
		if (match == null) return null;
		currentRound = request.forMatch(match);
		version++;
		return currentRound;
	}

	public synchronized void record(BattleNext_SimulationResult result) throws JSONException {
		for (BattleNext_Match match : tournament.getMatches()) {
			for (var round : match.getRounds()) {
				if (round.getReport().getId().equals(result.id())) return;
			}
		}
		if (!currentRound.id().equals(result.id())) throw new IllegalArgumentException("Simulation round identity mismatch: " + result.id());
		if (result.outcome() != BattleNext_SimulationResult.Outcome.COMPLETED) {
			transition(BattleNext_SimulationTask.State.PAUSED, result.outcome().name() + ": " + result.diagnostic());
			return;
		}
		BattleNext_Match match = tournament.findMatch(currentRound.source().getJSONObject("match").getString("id"));
		var report = result.report();
		String winner = switch (report.getWinner()) {
			case "player" -> match.getPlayer();
			case "enemy" -> match.getEnemy();
			default -> "";
		};
		new BattleNext_TournamentEngine().recordRound(tournament, match.getId(), winner, report);
		simulationSeconds += result.simulationSeconds();
		executionNanos += result.executionNanos();
		steps += result.steps();
		peakHeapBytes = Math.max(peakHeapBytes, result.peakHeapBytes());
		currentRound = null;
		version++;
	}

	public synchronized BattleNext_SimulationResult result() throws JSONException {
		BattleNext_TournamentSnapshot finished = copy();
		finished.transition(BattleNext_SimulationTask.State.FINISHED, "");
		return new BattleNext_SimulationResult(getId(), BattleNext_SimulationResult.Outcome.COMPLETED, "", finished.toJson().toString(),
				simulationSeconds, executionNanos, steps, peakHeapBytes, "");
	}
}
