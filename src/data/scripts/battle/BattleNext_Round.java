package data.scripts.battle;

import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_Round {

	public enum RoundStatus { PLAYER_WIN, ENEMY_WIN, DRAW }

	private String player;
	private String enemy;
	private RoundStatus status = RoundStatus.DRAW;
	private String reportId = "";

	public BattleNext_Round(String player, String enemy) {
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
	}

	public static BattleNext_Round fromJson(JSONObject json) throws JSONException {
		BattleNext_Round round = new BattleNext_Round(json.getString("player"), json.getString("enemy"));
		try {
			round.status = RoundStatus.valueOf(json.getString("status"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid round.status: " + json.getString("status"), exception);
		}
		round.reportId = json.getString("reportId");
		return round;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("player", player);
		json.put("enemy", enemy);
		json.put("status", status.name());
		json.put("reportId", reportId);
		return json;
	}

	public String getPlayer() {
		return player;
	}

	public String getEnemy() {
		return enemy;
	}

	public RoundStatus getStatus() {
		return status;
	}

	public void setStatus(RoundStatus status) {
		this.status = status;
	}

	public String getWinnerFleetId() {
		if (status == RoundStatus.PLAYER_WIN) return player;
		if (status == RoundStatus.ENEMY_WIN) return enemy;
		return "";
	}

	public String getReportId() {
		return reportId;
	}

	public void setReportId(String reportId) {
		this.reportId = reportId;
	}
}
