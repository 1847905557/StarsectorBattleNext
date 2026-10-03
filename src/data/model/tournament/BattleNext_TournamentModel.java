package data.model.tournament;

import data.model.BattleNext_JsonModel;
import data.model.BattleNext_LoadSource;
import data.scripts.battle.BattleNext_Match;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_TournamentModel extends BattleNext_JsonModel {

	public enum TournamentBracketType { SINGLE_ELIMINATION, DOUBLE_ELIMINATION, ROUND_ROBIN, KING_OF_THE_HILL }

	public static final int FORMAT_VERSION = 1;
	public static final int META_FORMAT_VERSION = 1;
	public static final int MIN_FLEET_COUNT = 2;
	public static final int MAX_FLEET_COUNT = 32;

	private String id;
	private String name;
	private TournamentBracketType bracketType;
	private int boCount;
	private int maxFleet;
	private final List<String> fleetIds = new ArrayList<>();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private String ruleId = "";
	private int mapId = -1;
	private boolean started;
	private boolean completed;
	private String championFleetId = "";
	private String pendingMatchId = "";

	public BattleNext_TournamentModel() {
		this("", "", TournamentBracketType.SINGLE_ELIMINATION, 1, MIN_FLEET_COUNT);
	}

	public BattleNext_TournamentModel(String id, String name, TournamentBracketType bracketType, int boCount, int maxFleet) {
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
		this.bracketType = bracketType;
		this.boCount = boCount;
		this.maxFleet = maxFleet;
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {

		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported tournament formatVersion: " + version);
		String parsedId = json.optString("id", "");
		String parsedName = json.optString("name", "");
		TournamentBracketType parsedBracket;
		try {
			parsedBracket = TournamentBracketType.valueOf(json.getString("bracketType"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid tournament.bracketType: " + json.getString("bracketType"), exception);
		}
		int parsedBo = json.getInt("boCount");
		int parsedMaxFleet = json.getInt("maxFleet");
		if (parsedBo < 1 || parsedMaxFleet < MIN_FLEET_COUNT || parsedMaxFleet > MAX_FLEET_COUNT) {
			throw new IllegalArgumentException("Invalid tournament limits");
		}
		String parsedRule = json.optString("ruleId", "");
		int parsedMap = json.optInt("mapId", -1);
		boolean parsedStarted = json.optBoolean("started", false);
		boolean parsedCompleted = json.optBoolean("completed", false);
		String parsedChampion = json.optString("championFleetId", "");
		String parsedPending = json.optString("pendingMatchId", "");
		List<String> parsedFleets = new ArrayList<>();
		JSONArray teamValues = json.getJSONArray("fleetIds");
		for (int index = 0; index < teamValues.length(); index++) {
			parsedFleets.add(teamValues.getString(index));
		}
		if (parsedFleets.size() > parsedMaxFleet) throw new IllegalArgumentException("fleetIds exceeds maxFleet");
		List<BattleNext_Match> parsedMatches = new ArrayList<>();
		JSONArray matchValues = json.optJSONArray("matches");
		if (matchValues != null) {
			for (int index = 0; index < matchValues.length(); index++) {
				if (matchValues.optJSONObject(index) != null) {
					BattleNext_Match match = BattleNext_Match.fromJson(matchValues.optJSONObject(index));
					match.setSourceType(getSourceType());
					match.setSourcePath(getSourcePath());
					parsedMatches.add(match);
				}
			}
		}

		setId(parsedId);
		setName(parsedName);
		bracketType = parsedBracket;
		boCount = parsedBo;
		maxFleet = parsedMaxFleet;
		ruleId = parsedRule;
		mapId = parsedMap;
		started = parsedStarted;
		completed = parsedCompleted;
		championFleetId = parsedChampion;
		pendingMatchId = parsedPending;
		fleetIds.clear();
		fleetIds.addAll(parsedFleets);
		matches.clear();
		matches.addAll(parsedMatches);
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("id", id);
		json.put("name", name);
		json.put("bracketType", bracketType.name());
		json.put("boCount", boCount);
		json.put("maxFleet", maxFleet);
		json.put("fleetIds", new JSONArray(fleetIds));
		JSONArray matchArray = new JSONArray();
		for (BattleNext_Match match : matches) {
			matchArray.put(match.toJson());
		}
		json.put("matches", matchArray);
		json.put("ruleId", ruleId);
		json.put("mapId", mapId);
		json.put("started", started);
		json.put("completed", completed);
		json.put("championFleetId", championFleetId);
		json.put("pendingMatchId", pendingMatchId);
		return json;
	}

	@Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id == null ? "" : id.trim();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name.trim();
	}

	public TournamentBracketType getBracketType() {
		return bracketType;
	}

	public void setBracketType(TournamentBracketType bracketType) {
		this.bracketType = bracketType;
	}

	public int getBoCount() {
		return boCount;
	}

	public int getWinTarget() {
		return boCount / 2 + 1;
	}

	public void setBoCount(int boCount) {
		this.boCount = boCount;
	}

	public int getMaxFleet() {
		return maxFleet;
	}

	public void setMaxFleet(int maxFleet) {
		this.maxFleet = maxFleet;
	}

	public String getRuleId() {
		return ruleId;
	}

	public void setRuleId(String ruleId) {
		this.ruleId = ruleId == null ? "" : ruleId.trim();
	}

	public int getMapId() {
		return mapId;
	}

	public void setMapId(int mapId) {
		this.mapId = mapId;
	}

	public boolean isStarted() {
		return started;
	}

	public void setStarted(boolean started) {
		this.started = started;
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
		this.championFleetId = championFleetId == null ? "" : championFleetId.trim();
	}

	public String getPendingMatchId() {
		return pendingMatchId;
	}

	public void setPendingMatchId(String pendingMatchId) {
		this.pendingMatchId = pendingMatchId == null ? "" : pendingMatchId.trim();
	}

	@Override
	public void setSourceType(BattleNext_LoadSource sourceType) {
		super.setSourceType(sourceType);
		for (BattleNext_Match match : matches) {
			match.setSourceType(sourceType);
		}
	}

	@Override
	public void setSourcePath(String sourcePath) {
		super.setSourcePath(sourcePath);
		for (BattleNext_Match match : matches) {
			match.setSourcePath(sourcePath);
		}
	}

	public List<String> getFleetIds() {
		return Collections.unmodifiableList(fleetIds);
	}

	public void setFleetIds(List<String> values) {
		if (started) throw new IllegalStateException("Started tournament teams are locked");
		List<String> parsed = new ArrayList<>();
		for (String value : values) {
			parsed.add(value == null ? "" : value.trim());
		}
		fleetIds.clear();
		fleetIds.addAll(parsed);
	}

	public List<BattleNext_Match> getMatches() {
		return Collections.unmodifiableList(matches);
	}

	public int getPlayableMatchCount() {
		int count = 0;
		for (BattleNext_Match match : matches) {
			if (!match.getEnemy().isEmpty()) count++;
		}
		return count;
	}

	public int getPlayableMatchTotal() {
		int fleetCount = fleetIds.size();
		if (bracketType == TournamentBracketType.ROUND_ROBIN) return fleetCount * (fleetCount - 1) / 2;
		if (bracketType == TournamentBracketType.DOUBLE_ELIMINATION) {
			return 2 * fleetCount - 2 + (findMatch("GF_RESET") == null ? 0 : 1);
		}
		return Math.max(0, fleetCount - 1);
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

	public void addFleetId(String fleetId) {
		if (!started && fleetIds.size() < MAX_FLEET_COUNT) {
			fleetIds.add(fleetId == null ? "" : fleetId.trim());
		}
	}

	public void addMatch(BattleNext_Match match) {
		if (match != null && !started) {
			match.setSourceType(getSourceType());
			match.setSourcePath(getSourcePath());
			matches.add(match);
		}
	}

	public void addGeneratedMatch(BattleNext_Match match) {
		if (match != null) {
			match.setSourceType(getSourceType());
			match.setSourcePath(getSourcePath());
			matches.add(match);
		}
	}

	public BattleNext_Match findMatch(String matchId) {
		for (BattleNext_Match match : matches) {
			if (match.getId().equals(matchId)) {
				return match;
			}
		}
		return null;
	}

	public void clearMatches() {
		if (!started) {
			matches.clear();
		}
	}

	public void clearGeneratedMatches() {
		matches.clear();
		pendingMatchId = "";
	}
}
