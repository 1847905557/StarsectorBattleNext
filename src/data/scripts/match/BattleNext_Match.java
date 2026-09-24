package data.scripts.match;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_Match {

	private String id;
	private String player;
	private String enemy;
	private String map;
	private int timeLimit = -1;
	private final List<BattleNext_Round> rounds = new ArrayList<>();

	public BattleNext_Match(String id, String player, String enemy, String map, int timeLimit) {
		this.id = id == null ? "" : id.trim();
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map == null ? "" : map.trim();
		this.timeLimit = timeLimit;
	}

	public static BattleNext_Match fromJson(JSONObject json) {
		BattleNext_Match match = new BattleNext_Match(json.optString("id", ""), json.optString("player", ""), json.optString("enemy", ""), json.optString("map", ""), json.optInt("timeLimit", -1));
		org.json.JSONArray values = json.optJSONArray("rounds");
		if (values != null) for (int index = 0; index < values.length(); index++) if (values.optJSONObject(index) != null) match.rounds.add(BattleNext_Round.fromJson(values.optJSONObject(index)));
		return match;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("map", map);
		json.put("timeLimit", timeLimit);
		org.json.JSONArray values = new org.json.JSONArray();
		for (BattleNext_Round round : rounds) values.put(round.toJson());
		json.put("rounds", values);
		return json;
	}

	public void addRound(BattleNext_Round round) { if (round != null) rounds.add(round); }
	public String getId() { return id; }
	public String getPlayer() { return player; }
	public String getEnemy() { return enemy; }
	public String getMap() { return map; }
	public int getTimeLimit() { return timeLimit; }
	public List<BattleNext_Round> getRounds() { return Collections.unmodifiableList(rounds); }
}
