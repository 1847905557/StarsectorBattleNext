package data.scripts.tournament;

import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_Tournament {

	public static final int FORMAT_VERSION = 2;
	public static final int META_FORMAT_VERSION = 2;

	private String id;
	private String name;
	private String bracketType;
	private String matchFormat;
	private int size;
	private final List<String> teams = new ArrayList<>();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private String rulesId = "";
	private int mapId = -1;
	private boolean started;
	private boolean completed;
	private String champion = "";
	private String pendingMatchId = "";
	private int kofNextIndex;
	private String sourceType = BattleNext_LoadSource.NATIVE;
	private String sourcePath = "";

	public BattleNext_Tournament(String id, String name, String bracketType, String matchFormat, int size) {
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
		this.bracketType = bracketType == null ? "single_elim" : bracketType.trim();
		this.matchFormat = matchFormat == null ? "BO1" : matchFormat.trim();
		this.size = size;
	}

	public static BattleNext_Tournament fromJson(JSONObject json) {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported tournament formatVersion: " + version);
		BattleNext_Tournament tournament = new BattleNext_Tournament(json.optString("id", ""), json.optString("name", ""), json.optString("bracketType", "single_elim"), json.optString("matchFormat", "BO1"), json.optInt("size", 2));
		tournament.rulesId = json.optString("rulesId", "");
		tournament.mapId = json.optInt("mapId", -1);
		tournament.started = json.optBoolean("started", false);
		tournament.completed = json.optBoolean("completed", false);
		tournament.champion = json.optString("champion", "");
		tournament.pendingMatchId = json.optString("pendingMatchId", "");
		tournament.kofNextIndex = Math.max(0, json.optInt("kofNextIndex", 0));
		JSONArray teams = json.optJSONArray("teams");
		if (teams != null) {
			for (int index = 0; index < teams.length(); index++) {
				tournament.teams.add(teams.optString(index, ""));
			}
		}
		JSONArray matches = json.optJSONArray("matches");
		if (matches != null) {
			for (int index = 0; index < matches.length(); index++) {
				if (matches.optJSONObject(index) != null) {
					tournament.matches.add(BattleNext_Match.fromJson(matches.optJSONObject(index)));
				}
			}
		}
		return tournament;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("id", id);
		json.put("name", name);
		json.put("bracketType", bracketType);
		json.put("matchFormat", matchFormat);
		json.put("size", size);
		json.put("teams", new JSONArray(teams));
		JSONArray matchArray = new JSONArray();
		for (BattleNext_Match match : matches) {
			matchArray.put(match.toJson());
		}
		json.put("matches", matchArray);
		json.put("rulesId", rulesId);
		json.put("mapId", mapId);
		json.put("started", started);
		json.put("completed", completed);
		json.put("champion", champion);
		json.put("pendingMatchId", pendingMatchId);
		json.put("kofNextIndex", kofNextIndex);
		return json;
	}

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

	public String getBracketType() {
		return bracketType;
	}

	public void setBracketType(String bracketType) {
		this.bracketType = bracketType == null ? "" : bracketType.trim();
	}

	public String getMatchFormat() {
		return matchFormat;
	}

	public void setMatchFormat(String matchFormat) {
		this.matchFormat = matchFormat == null ? "" : matchFormat.trim();
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getRulesId() {
		return rulesId;
	}

	public void setRulesId(String rulesId) {
		this.rulesId = rulesId == null ? "" : rulesId.trim();
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

	public String getChampion() {
		return champion;
	}

	public void setChampion(String champion) {
		this.champion = champion == null ? "" : champion.trim();
	}

	public String getPendingMatchId() {
		return pendingMatchId;
	}

	public void setPendingMatchId(String pendingMatchId) {
		this.pendingMatchId = pendingMatchId == null ? "" : pendingMatchId.trim();
	}

	public int getKofNextIndex() {
		return kofNextIndex;
	}

	public void setKofNextIndex(int kofNextIndex) {
		this.kofNextIndex = Math.max(0, kofNextIndex);
	}

	public String getSourceType() {
		return sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public List<String> getTeams() {
		return Collections.unmodifiableList(teams);
	}

	public void setTeams(List<String> values) {
		if (started) throw new IllegalStateException("Started tournament teams are locked");
		teams.clear();
		for (String value : values) addTeam(value);
		size = teams.size();
	}

	public List<BattleNext_Match> getMatches() {
		return Collections.unmodifiableList(matches);
	}

	public void addTeam(String team) {
		if (!started && teams.size() < 32) {
			teams.add(team == null ? "" : team.trim());
		}
	}

	public void addMatch(BattleNext_Match match) {
		if (match != null && !started) {
			matches.add(match);
		}
	}

	public void addGeneratedMatch(BattleNext_Match match) {
		if (match != null) {
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
}
