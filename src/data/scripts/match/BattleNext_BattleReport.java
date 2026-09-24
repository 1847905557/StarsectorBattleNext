package data.scripts.match;

import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_BattleReport {

	private String id = "";
	private String winner = "draw";
	private long durationMillis = 0L;
	private int playerShips = 0;
	private int enemyShips = 0;

	public static BattleNext_BattleReport fromJson(JSONObject json) {
		BattleNext_BattleReport report = new BattleNext_BattleReport();
		report.id = json.optString("id", "");
		report.winner = json.optString("winner", "draw");
		report.durationMillis = json.optLong("durationMillis", 0L);
		report.playerShips = json.optInt("playerShips", 0);
		report.enemyShips = json.optInt("enemyShips", 0);
		return report;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("winner", winner);
		json.put("durationMillis", durationMillis);
		json.put("playerShips", playerShips);
		json.put("enemyShips", enemyShips);
		return json;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id == null ? "" : id.trim(); }
	public String getWinner() { return winner; }
	public void setWinner(String winner) { this.winner = winner == null || winner.trim().isEmpty() ? "draw" : winner.trim(); }
	public long getDurationMillis() { return durationMillis; }
	public void setDurationMillis(long durationMillis) { this.durationMillis = Math.max(0L, durationMillis); }
	public int getPlayerShips() { return playerShips; }
	public void setPlayerShips(int playerShips) { this.playerShips = Math.max(0, playerShips); }
	public int getEnemyShips() { return enemyShips; }
	public void setEnemyShips(int enemyShips) { this.enemyShips = Math.max(0, enemyShips); }
}
