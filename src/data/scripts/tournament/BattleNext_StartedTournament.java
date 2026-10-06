package data.scripts.tournament;

import data.model.BattleNext_JsonModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class BattleNext_StartedTournament extends BattleNext_JsonModel {

	public enum ExecutionKind { MANUAL, SIMULATION }

	private String id;
	private String sourceTournamentId;
	private ExecutionKind executionKind;
	private long startedAt;
	private final List<String> fleetIds = new ArrayList<>();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private boolean completed;
	private String championFleetId = "";
	private String pendingMatchId = "";
	private long version;

	public BattleNext_StartedTournament() {

	}

	public BattleNext_StartedTournament(String id, String sourceTournamentId, ExecutionKind executionKind, List<String> fleetIds) {
		UUID.fromString(id);
		this.id = id;
		this.sourceTournamentId = sourceTournamentId;
		this.executionKind = executionKind;
		this.fleetIds.addAll(fleetIds);
		startedAt = System.currentTimeMillis();
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		String parsedId = json.getString("id");
		UUID.fromString(parsedId);
		String parsedSource = json.getString("sourceTournamentId");
		ExecutionKind parsedKind = ExecutionKind.valueOf(json.getString("executionKind"));
		long parsedStartedAt = json.getLong("startedAt");
		boolean parsedCompleted = json.getBoolean("completed");
		String parsedChampion = json.getString("championFleetId");
		String parsedPending = json.getString("pendingMatchId");
		long parsedVersion = json.getLong("version");
		List<String> parsedFleets = new ArrayList<>();
		JSONArray participants = json.getJSONArray("fleetIds");
		beginParse();
		for (int index = 0; index < participants.length(); index++) {
			try {
				if (!(participants.get(index) instanceof String value)) throw new JSONException("Expected a fleet ID string");
				parsedFleets.add(value);
			} catch (JSONException exception) {
				parsedFleets.add("");
				addLoadError("fleetIds[" + index + "] " + participants.opt(index) + ": " + exception);
				retainCollection("fleetIds", participants);
			}
		}
		JSONArray values = json.getJSONArray("matches");
		List<BattleNext_Match> parsedMatches = readIdentifiedCollection("matches", values, BattleNext_Match::fromJson);
		for (BattleNext_Match match : parsedMatches) {
			for (String error : match.getLoadErrors()) {
				addLoadError("Match " + match.getId() + ": " + error);
			}
		}
		id = parsedId;
		sourceTournamentId = parsedSource;
		executionKind = parsedKind;
		startedAt = parsedStartedAt;
		completed = parsedCompleted;
		championFleetId = parsedChampion;
		pendingMatchId = parsedPending;
		version = parsedVersion;
		fleetIds.clear();
		fleetIds.addAll(parsedFleets);
		matches.clear();
		matches.addAll(parsedMatches);
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONArray values = new JSONArray();
		for (BattleNext_Match match : matches) {
			values.put(match.toJson());
		}
		return new JSONObject().put("id", id).put("sourceTournamentId", sourceTournamentId)
				.put("executionKind", executionKind.name()).put("startedAt", startedAt).put("fleetIds", writeCollection("fleetIds", new JSONArray(fleetIds))).put("matches", writeCollection("matches", values))
				.put("completed", completed).put("championFleetId", championFleetId)
				.put("pendingMatchId", pendingMatchId).put("version", version);
	}

	public BattleNext_StartedTournament copy() {
		try {
			BattleNext_StartedTournament copy = new BattleNext_StartedTournament();
			copy.fromJson(toJson());
			return copy;
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to copy started tournament: " + id, exception);
		}
	}

	@Override
	public String getId() {
		return id;
	}

	public String getSourceTournamentId() {
		return sourceTournamentId;
	}

	public ExecutionKind getExecutionKind() {
		return executionKind;
	}

	public long getStartedAt() {
		return startedAt;
	}

	public String getName(BattleNext_TournamentModel configuration) {
		String name = configuration == null ? sourceTournamentId : configuration.getName();
		String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(startedAt));
		return (executionKind == ExecutionKind.SIMULATION ? "Simulation " : "Tournament ") + name + " - " + time;
	}

	public long getVersion() {
		return version;
	}

	public void changed() {
		version++;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	public String getChampionFleetId() {
		return championFleetId;
	}

	public void setChampionFleetId(String championFleetId) {
		this.championFleetId = championFleetId;
	}

	public String getPendingMatchId() {
		return pendingMatchId;
	}

	public void setPendingMatchId(String pendingMatchId) {
		this.pendingMatchId = pendingMatchId;
	}

	public List<BattleNext_Match> getMatches() {
		return Collections.unmodifiableList(matches);
	}

	public List<String> getFleetIds() {
		return Collections.unmodifiableList(fleetIds);
	}

	public void addGeneratedMatch(BattleNext_Match match) {
		matches.add(match);
	}

	public void clearGeneratedMatches() {
		matches.clear();
		pendingMatchId = "";
	}

	public BattleNext_Match findMatch(String matchId) {
		for (BattleNext_Match match : matches) {
			if (match.getId().equals(matchId)) return match;
		}
		return null;
	}

	public BattleNext_Match getNextPendingMatch() {
		if (completed) return null;
		BattleNext_Match pending = findMatch(pendingMatchId);
		if (pending != null && pending.getStatus() == BattleNext_Match.MatchStatus.PENDING && !pending.getEnemy().isEmpty()) return pending;
		for (BattleNext_Match match : matches) {
			if (match.getStatus() == BattleNext_Match.MatchStatus.PENDING && !match.getEnemy().isEmpty()) return match;
		}
		return null;
	}

	public int getPlayableMatchCount() {
		int count = 0;
		for (BattleNext_Match match : matches) {
			if (!match.getEnemy().isEmpty()) {
				count++;
			}
		}
		return count;
	}

	public int getPlayableMatchTotal(BattleNext_TournamentModel configuration) {
		return BattleNext_TournamentEngine.playableMatchTotal(configuration, fleetIds) + (configuration.getBracketType() == BattleNext_TournamentModel.TournamentBracketType.DOUBLE_ELIMINATION
				&& findMatch("GF_RESET") != null ? 1 : 0);
	}

	public int getPlayableMatchIndex(String matchId) {
		int index = 0;
		for (BattleNext_Match match : matches) {
			if (match.getEnemy().isEmpty()) continue;
			if (match.getId().equals(matchId)) return index;
			index++;
		}
		return -1;
	}
}
