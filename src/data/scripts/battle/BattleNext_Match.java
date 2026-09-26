package data.scripts.battle;

import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_Match {

	private String id;
	private String player;
	private String enemy;
	private int map;
	private float size;
	private final List<BattleNext_Round> rounds = new ArrayList<>();
	private int playerWins;
	private int enemyWins;
	private String status = "pending";
	private String stage = "main";
	private boolean replay;
	private BattleNext_LoadSource sourceType = BattleNext_LoadSource.CREATED;
	private String sourcePath = "";

	public BattleNext_Match(String id, String player, String enemy, int map, float size) {
		this.id = id == null ? "" : id.trim();
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map;
		this.size = size;
	}

	public static BattleNext_Match fromQuickBattle(BattleNext_QuickBattleModel model) {
		return new BattleNext_Match(model.getId(), model.getPlayer(), model.getEnemy(), model.getMap(), model.getSize());
	}

	public static BattleNext_Match fromJson(JSONObject json) throws JSONException {
		BattleNext_Match match = new BattleNext_Match(json.optString("id", ""), json.optString("player", ""), json.optString("enemy", ""), json.optInt("map", -1), (float)json.optDouble("size", 1f));
		org.json.JSONArray values = json.optJSONArray("rounds");
		if (values != null) {
			for (int index = 0; index < values.length(); index++) {
				if (values.optJSONObject(index) != null) {
					match.rounds.add(BattleNext_Round.fromJson(values.optJSONObject(index)));
				}
			}
		}
		match.playerWins = Math.max(0, json.optInt("playerWins", 0));
		match.enemyWins = Math.max(0, json.optInt("enemyWins", 0));
		match.status = json.optString("status", "pending");
		match.stage = json.optString("stage", "main");
		match.replay = json.optBoolean("replay", false);
		return match;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("map", map);
		json.put("size", size);
		org.json.JSONArray values = new org.json.JSONArray();
		for (BattleNext_Round round : rounds) {
			values.put(round.toJson());
		}
		json.put("rounds", values);
		json.put("playerWins", playerWins);
		json.put("enemyWins", enemyWins);
		json.put("status", status);
		json.put("stage", stage);
		json.put("replay", replay);
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

	public float getSize() {
		return size;
	}

	public List<BattleNext_Round> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	public int getPlayerWins() {
		return playerWins;
	}

	public int getEnemyWins() {
		return enemyWins;
	}

	public String getStatus() {
		return status;
	}

	public String getStage() {
		return stage;
	}

	public void addPlayerWin() {
		playerWins++;
	}

	public void addEnemyWin() {
		enemyWins++;
	}

	public void setStatus(String status) {
		this.status = status == null || status.trim().isEmpty() ? "pending" : status.trim();
	}

	public void setStage(String stage) {
		this.stage = stage == null || stage.trim().isEmpty() ? "main" : stage.trim();
	}

	public void setSides(String player, String enemy) {
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
	}

	public void clearRoundsAfter(int roundNumber) {
		while (!rounds.isEmpty() && rounds.get(rounds.size() - 1).getNumber() >= roundNumber) {
			rounds.remove(rounds.size() - 1);
		}
	}

	public boolean isReplay() {
		return replay;
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

	public void setReplay(boolean replay) {
		this.replay = replay;
	}

	public void resetProgressForReplay() {
		playerWins = 0;
		enemyWins = 0;
		status = "pending";
		replay = true;
	}
}
