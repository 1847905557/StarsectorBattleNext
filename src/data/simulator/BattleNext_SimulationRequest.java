package data.simulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BattleNext_SimulationRequest(String id, List<String> player, List<String> enemy,
		BattleNext_SimulationConfig config) {

	public BattleNext_SimulationRequest {
		UUID.fromString(id);
		player = List.copyOf(player);
		enemy = List.copyOf(enemy);
		if (player.isEmpty() || enemy.isEmpty()) throw new IllegalArgumentException("Both simulation sides require at least one variant");
		for (String variant : player) {
			if (variant.isBlank()) throw new IllegalArgumentException("Player variant ID must not be blank");
		}
		for (String variant : enemy) {
			if (variant.isBlank()) throw new IllegalArgumentException("Enemy variant ID must not be blank");
		}
		if (config == null) throw new IllegalArgumentException("Simulation configuration is required");
	}

	public JSONObject toJson() throws JSONException {
		return new JSONObject().put("id", id).put("player", new JSONArray(player)).put("enemy", new JSONArray(enemy)).put("config", config.toJson());
	}

	public static BattleNext_SimulationRequest fromJson(JSONObject json) throws JSONException {
		List<String> player = new ArrayList<>();
		List<String> enemy = new ArrayList<>();
		JSONArray players = json.getJSONArray("player");
		JSONArray enemies = json.getJSONArray("enemy");
		for (int index = 0; index < players.length(); index++) {
			player.add(players.getString(index));
		}
		for (int index = 0; index < enemies.length(); index++) {
			enemy.add(enemies.getString(index));
		}
		return new BattleNext_SimulationRequest(json.getString("id"), player, enemy, BattleNext_SimulationConfig.fromJson(json.getJSONObject("config")));
	}
}
