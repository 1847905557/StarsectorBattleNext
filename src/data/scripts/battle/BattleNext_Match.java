package data.scripts.battle;

import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_Match {

	public enum Status { PENDING, COMPLETE }
	public enum Stage { MAIN, WINNERS, LOSERS, GRAND_FINAL, GRAND_FINAL_RESET, ROUND_ROBIN, KING_OF_THE_HILL, BYE }

	private String id;
	private String player;
	private String enemy;
	private int map;
	private float mapScale;
	private final List<BattleNext_Round> rounds = new ArrayList<>();
	private Status status = Status.PENDING;
	private Stage stage = Stage.MAIN;
	private final List<String> dependencies = new ArrayList<>();
	private BattleNext_LoadSource sourceType = BattleNext_LoadSource.CREATED;
	private String sourcePath = "";

	public BattleNext_Match(String id, String player, String enemy, int map, float mapScale) {
		this.id = id == null ? "" : id.trim();
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map;
		this.mapScale = mapScale;
	}

	public static BattleNext_Match fromQuickBattle(BattleNext_QuickBattleModel model) {
		return new BattleNext_Match(model.getId(), model.getPlayer(), model.getEnemy(), model.getMap(), model.getMapScale());
	}

	public static BattleNext_Match fromJson(JSONObject json) throws JSONException {
		BattleNext_Match match = new BattleNext_Match(json.getString("id"), json.getString("player"), json.getString("enemy"), json.getInt("map"), (float)json.getDouble("mapScale"));
		if (!Float.isFinite(match.mapScale) || match.mapScale <= 0f) throw new IllegalArgumentException("Invalid match mapScale");
		org.json.JSONArray values = json.optJSONArray("rounds");
		if (values != null) {
			for (int index = 0; index < values.length(); index++) {
				if (values.optJSONObject(index) != null) {
					match.rounds.add(BattleNext_Round.fromJson(values.optJSONObject(index)));
				}
			}
		}
		try {
			match.status = Status.valueOf(json.getString("status"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid match.status: " + json.getString("status"), exception);
		}
		try {
			match.stage = Stage.valueOf(json.getString("stage"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid match.stage: " + json.getString("stage"), exception);
		}
		org.json.JSONArray sources = json.optJSONArray("dependencies");
		if (sources != null) {
			for (int index = 0; index < sources.length(); index++) {
				match.dependencies.add(sources.getString(index));
			}
		}
		return match;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("map", map);
		json.put("mapScale", mapScale);
		org.json.JSONArray values = new org.json.JSONArray();
		for (BattleNext_Round round : rounds) {
			values.put(round.toJson());
		}
		json.put("rounds", values);
		json.put("status", status.name());
		json.put("stage", stage.name());
		json.put("dependencies", new org.json.JSONArray(dependencies));
		return json;
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

	public Status getStatus() {
		return status;
	}

	public Stage getStage() {
		return stage;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public void setStage(Stage stage) {
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
		if (status != Status.COMPLETE) return "";
		if (enemy.isEmpty()) return player;
		return getPlayerWins() > getEnemyWins() ? player : enemy;
	}

	public String getLoserFleetId() {
		if (status != Status.COMPLETE || enemy.isEmpty()) return "";
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
		status = Status.PENDING;
	}
}
