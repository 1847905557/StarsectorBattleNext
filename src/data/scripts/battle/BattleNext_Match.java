package data.scripts.battle;

import data.model.BattleNext_JsonModel;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_Match {

	public enum MatchStatus { PENDING, COMPLETE }
	public enum MatchStage { MAIN, WINNERS, LOSERS, GRAND_FINAL, GRAND_FINAL_RESET, ROUND_ROBIN, KING_OF_THE_HILL, BYE }

	private String id;
	private String player;
	private String enemy;
	private int map;
	private float mapScale;
	private final List<BattleNext_Round> rounds = new ArrayList<>();
	private MatchStatus status = MatchStatus.PENDING;
	private MatchStage stage = MatchStage.MAIN;
	private final List<String> dependencies = new ArrayList<>();
	private BattleNext_LoadSource sourceType = BattleNext_LoadSource.CREATED;
	private String sourcePath = "";
	private final List<String> loadErrors = new ArrayList<>();
	private JSONObject incompleteJson;

	public BattleNext_Match(String id, String player, String enemy, int map, float mapScale) {
		this.id = id == null ? "" : id.trim();
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map;
		this.mapScale = mapScale;
	}

	public static BattleNext_Match fromQuickBattle(BattleNext_QuickBattleModel model) {
		BattleNext_Match match = new BattleNext_Match(model.getId(), model.getPlayer(), model.getEnemy(), model.getMap(), model.getMapScale());
		match.setSourceType(model.getSourceType());
		match.setSourcePath(model.getSourcePath());
		return match;
	}

	public static BattleNext_Match fromJson(JSONObject json) throws JSONException {
		BattleNext_Match match = new BattleNext_Match(json.getString("id"), json.getString("player"), json.getString("enemy"), json.getInt("map"), (float)json.getDouble("mapScale"));
		if (!Float.isFinite(match.mapScale) || match.mapScale <= 0f) throw new IllegalArgumentException("Invalid match mapScale");
		JSONArray values = json.optJSONArray("rounds");
		if (values != null) {
			match.rounds.addAll(BattleNext_JsonModel.parseCollection("rounds", values, BattleNext_Round::fromJson, match.loadErrors));
		}
		try {
			match.status = MatchStatus.valueOf(json.getString("status"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid match.status: " + json.getString("status"), exception);
		}
		try {
			match.stage = MatchStage.valueOf(json.getString("stage"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid match.stage: " + json.getString("stage"), exception);
		}
		JSONArray sources = json.optJSONArray("dependencies");
		if (sources != null) {
			for (int index = 0; index < sources.length(); index++) {
				try {
					if (!(sources.get(index) instanceof String value)) throw new JSONException("Expected a match ID string");
					match.dependencies.add(value);
				} catch (JSONException exception) {
					match.loadErrors.add("dependencies[" + index + "] " + sources.opt(index) + ": " + exception);
				}
			}
		}
		if (!match.loadErrors.isEmpty()) {
			match.incompleteJson = new JSONObject(json.toString());
		}
		return match;
	}

	public JSONObject toJson() throws JSONException {
		if (incompleteJson != null) return new JSONObject(incompleteJson.toString());
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("map", map);
		json.put("mapScale", mapScale);
		JSONArray values = new JSONArray();
		for (BattleNext_Round round : rounds) {
			values.put(round.toJson());
		}
		json.put("rounds", values);
		json.put("status", status.name());
		json.put("stage", stage.name());
		json.put("dependencies", new JSONArray(dependencies));
		return json;
	}

	public List<String> getLoadErrors() {
		return List.copyOf(loadErrors);
	}

	public void addRound(BattleNext_Round round) {
		if (round != null) {
			rounds.add(round);
		}
	}

	public String getId() {
		return id;
	}

	public String getPlayer() {
		return player;
	}

	public String getEnemy() {
		return enemy;
	}

	public int getMap() {
		return map;
	}

	public float getMapScale() {
		return mapScale;
	}

	public List<BattleNext_Round> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	public int getPlayerWins() {
		return winsFor(player);
	}

	public int getEnemyWins() {
		return winsFor(enemy);
	}

	public MatchStatus getStatus() {
		return status;
	}

	public MatchStage getStage() {
		return stage;
	}

	public void setStatus(MatchStatus status) {
		this.status = status;
	}

	public void setStage(MatchStage stage) {
		this.stage = stage;
	}

	public void setSides(String player, String enemy) {
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
	}

	public void addDependency(String matchId) {
		if (matchId != null && !matchId.isEmpty() && !dependencies.contains(matchId)) dependencies.add(matchId);
	}

	public List<String> getDependencies() {
		return Collections.unmodifiableList(dependencies);
	}

	public String getWinnerFleetId() {
		if (status != MatchStatus.COMPLETE) return "";
		if (enemy.isEmpty()) return player;
		return getPlayerWins() > getEnemyWins() ? player : enemy;
	}

	public String getLoserFleetId() {
		if (status != MatchStatus.COMPLETE || enemy.isEmpty()) return "";
		return getWinnerFleetId().equals(player) ? enemy : player;
	}

	private int winsFor(String fleetId) {
		int wins = 0;
		for (BattleNext_Round round : rounds) {
			if (fleetId.equals(round.getWinnerFleetId())) wins++;
		}
		return wins;
	}

	public BattleNext_LoadSource getSourceType() {
		return sourceType;
	}

	public void setSourceType(BattleNext_LoadSource sourceType) {
		this.sourceType = sourceType;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public void resetProgressForReplay() {
		rounds.clear();
		status = MatchStatus.PENDING;
	}
}
