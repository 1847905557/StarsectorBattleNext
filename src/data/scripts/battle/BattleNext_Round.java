package data.scripts.battle;

import data.model.report.BattleNext_BattleReportModel;
import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_Round {

	public enum Status { PLAYER_WIN, ENEMY_WIN, DRAW }

	private String player;
	private String enemy;
	private Status status = Status.DRAW;
	private BattleNext_BattleReportModel report;

	public BattleNext_Round(String player, String enemy) {
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.report = new BattleNext_BattleReportModel();
	}

	public static BattleNext_Round fromJson(JSONObject json) throws JSONException {
		BattleNext_Round round = new BattleNext_Round(json.getString("player"), json.getString("enemy"));
		try {
			round.status = Status.valueOf(json.getString("status"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid round.status: " + json.getString("status"), exception);
		}
		JSONObject report = json.optJSONObject("report");
		if (report != null) {
			BattleNext_BattleReportModel reportModel = new BattleNext_BattleReportModel();
			reportModel.fromJson(report);
			round.report = reportModel;
		}
		return round;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("status", status.name());
		json.put("report", report.toJson());
		return json;
	}

	public String getPlayer() {
		return player;
	}

	public String getEnemy() {
		return enemy;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getWinnerFleetId() {
		if (status == Status.PLAYER_WIN) return player;
		if (status == Status.ENEMY_WIN) return enemy;
		return "";
	}

	public BattleNext_BattleReportModel getReport() {
		return report;
	}

	public void setReport(BattleNext_BattleReportModel report) {
		this.report = report == null ? new BattleNext_BattleReportModel() : report;
	}
}
