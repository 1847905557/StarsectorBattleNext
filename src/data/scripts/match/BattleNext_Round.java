package data.scripts.match;

import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_Round {

	private int number;
	private String player;
	private String enemy;
	private String winner = "draw";
	private int attempt;
	private BattleNext_BattleReport report;

	public BattleNext_Round(int number, String player, String enemy) {
		this.number = number;
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.report = new BattleNext_BattleReport();
	}

	public static BattleNext_Round fromJson(JSONObject json) {
		BattleNext_Round round = new BattleNext_Round(json.optInt("number", 0), json.optString("player", ""), json.optString("enemy", ""));
		round.winner = json.optString("winner", "draw");
		round.attempt = Math.max(0, json.optInt("attempt", 0));
		JSONObject report = json.optJSONObject("report");
		if (report != null) round.report = BattleNext_BattleReport.fromJson(report);
		return round;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("number", number);
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("winner", winner);
		json.put("attempt", attempt);
		json.put("report", report.toJson());
		return json;
	}

	public int getNumber() { return number; }
	public String getPlayer() { return player; }
	public String getEnemy() { return enemy; }
	public String getWinner() { return winner; }
	public void setWinner(String winner) { this.winner = winner == null || winner.trim().isEmpty() ? "draw" : winner.trim(); }
	public int getAttempt() { return attempt; }
	public void setAttempt(int attempt) { this.attempt = Math.max(0, attempt); }
	public BattleNext_BattleReport getReport() { return report; }
	public void setReport(BattleNext_BattleReport report) { this.report = report == null ? new BattleNext_BattleReport() : report; }
}
