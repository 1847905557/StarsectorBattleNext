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
	private int map;
	private float size;
	private final List<BattleNext_Round> rounds = new ArrayList<>();

	public BattleNext_Match(String id, String player, String enemy, int map, float size) {
		this.id = id == null ? "" : id.trim();
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map;
		this.size = size;
	}

	public static BattleNext_Match fromJson(JSONObject json) {
		BattleNext_Match match = new BattleNext_Match(json.optString("id", ""), json.optString("player", ""), json.optString("enemy", ""), json.optInt("map", -1), (float)json.optDouble("size", 1f));
		org.json.JSONArray values = json.optJSONArray("rounds");
		if (values != null) {
			for (int index = 0; index < values.length(); index++) {
				if (values.optJSONObject(index) != null) {
					match.rounds.add(BattleNext_Round.fromJson(values.optJSONObject(index)));
				}
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
		json.put("size", size);
		org.json.JSONArray values = new org.json.JSONArray();
		for (BattleNext_Round round : rounds) {
			values.put(round.toJson());
		}
		json.put("rounds", values);
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
}
