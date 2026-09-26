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

	public static final int FORMAT_VERSION = 1;
	public static final int META_FORMAT_VERSION = 1;

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

	public BattleNext_TournamentModel() {
		this("", "", "single_elim", "BO1", 2);
	}

	public BattleNext_TournamentModel(String id, String name, String bracketType, String matchFormat, int size) {
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
		this.bracketType = bracketType == null ? "single_elim" : bracketType.trim();
		this.matchFormat = matchFormat == null ? "BO1" : matchFormat.trim();
		this.size = size;
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported tournament formatVersion: " + version);
		setId(json.optString("id", ""));
		setName(json.optString("name", ""));
		setBracketType(json.optString("bracketType", "single_elim"));
		setMatchFormat(json.optString("matchFormat", "BO1"));
		size = json.optInt("size", 2);
		rulesId = json.optString("rulesId", "");
		mapId = json.optInt("mapId", -1);
		started = json.optBoolean("started", false);
		completed = json.optBoolean("completed", false);
		champion = json.optString("champion", "");
		pendingMatchId = json.optString("pendingMatchId", "");
		kofNextIndex = Math.max(0, json.optInt("kofNextIndex", 0));
		JSONArray teamValues = json.optJSONArray("teams");
		if (teamValues != null) {
			for (int index = 0; index < teamValues.length(); index++) {
				teams.add(teamValues.optString(index, ""));
			}
		}
		JSONArray matchValues = json.optJSONArray("matches");
		if (matchValues != null) {
			for (int index = 0; index < matchValues.length(); index++) {
				if (matchValues.optJSONObject(index) != null) {
					matches.add(BattleNext_Match.fromJson(matchValues.optJSONObject(index)));
				}
			}
		}
	}

	@Override
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

	public List<String> getTeams() {
		return Collections.unmodifiableList(teams);
	}

	public void setTeams(List<String> values) {
		if (started) throw new IllegalStateException("Started tournament teams are locked");
		teams.clear();
		for (String value : values) {
			addTeam(value);
		}
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
