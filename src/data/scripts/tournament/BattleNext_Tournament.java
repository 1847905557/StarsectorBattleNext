package data.scripts.tournament;

import data.scripts.match.BattleNext_Match;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_Tournament {

	private int slot;
	private String name;
	private String bracketType;
	private String matchFormat;
	private int size;
	private final List<String> teams = new ArrayList<>();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private int rulesSlot = -1;
	private int mapId = -1;
	private boolean started;
	private boolean completed;
	private String champion = "";
	private String pendingMatchId = "";
	private int kofNextIndex;

	public BattleNext_Tournament(int slot, String name, String bracketType, String matchFormat, int size) {
		if (slot < 0 || slot > 31) {
			throw new IllegalArgumentException("Tournament slot must be between 0 and 31");
		}
		this.slot = slot;
		this.name = name == null ? "" : name.trim();
		this.bracketType = bracketType == null ? "single_elim" : bracketType.trim();
		this.matchFormat = matchFormat == null ? "BO1" : matchFormat.trim();
		this.size = size;
	}

	public static BattleNext_Tournament fromJson(JSONObject json) {
		BattleNext_Tournament tournament = new BattleNext_Tournament(json.optInt("slot", 0), json.optString("name", ""), json.optString("bracketType", "single_elim"), json.optString("matchFormat", "BO1"), json.optInt("size", 2));
		tournament.rulesSlot = json.optInt("rulesSlot", -1);
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
		json.put("formatVersion", 1);
		json.put("slot", slot);
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
		json.put("rulesSlot", rulesSlot);
		json.put("mapId", mapId);
		json.put("started", started);
		json.put("completed", completed);
		json.put("champion", champion);
		json.put("pendingMatchId", pendingMatchId);
		json.put("kofNextIndex", kofNextIndex);
		return json;
	}

	public int getSlot() {
		return slot;
	}

	public String getName() {
		return name;
	}

	public String getBracketType() {
		return bracketType;
	}

	public String getMatchFormat() {
		return matchFormat;
	}

	public int getSize() {
		return size;
	}

	public int getRulesSlot() {
		return rulesSlot;
	}

	public int getMapId() {
		return mapId;
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

	public List<String> getTeams() {
		return Collections.unmodifiableList(teams);
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
