package data.scripts.report;

import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleReportModel {

	private String id = "";
	private String winner = "draw";
	private long durationMillis = 0L;
	private long createdAt = 0L;
	private List<BattleNext_MemberReport> playerMembers = new ArrayList<>();
	private List<BattleNext_MemberReport> enemyMembers = new ArrayList<>();
	private String sourceType = BattleNext_LoadSource.COMMON;
	private String sourcePath = "";

	public static BattleNext_BattleReportModel fromJson(JSONObject json) {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.id = json.optString("id", "");
		report.winner = json.optString("winner", "draw");
		report.durationMillis = json.optLong("durationMillis", 0L);
		report.createdAt = json.optLong("createdAt", 0L);
		parseMembers(json.optJSONArray("playerMembers"), report.playerMembers);
		parseMembers(json.optJSONArray("enemyMembers"), report.enemyMembers);
		return report;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("winner", winner);
		json.put("durationMillis", durationMillis);
		json.put("createdAt", createdAt);
		json.put("playerMembers", membersToJson(playerMembers));
		json.put("enemyMembers", membersToJson(enemyMembers));
		return json;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id == null ? "" : id.trim();
	}

	public String getWinner() {
		return winner;
	}

	public void setWinner(String winner) {
		this.winner = winner == null || winner.trim().isEmpty() ? "draw" : winner.trim();
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = Math.max(0L, durationMillis);
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = Math.max(0L, createdAt);
	}

	public List<BattleNext_MemberReport> getPlayerMembers() {
		return Collections.unmodifiableList(playerMembers);
	}

	public List<BattleNext_MemberReport> getEnemyMembers() {
		return Collections.unmodifiableList(enemyMembers);
	}

	public String getSourceType() {
		return sourceType;
	}

	public void setSourceType(String sourceType) {
		this.sourceType = sourceType;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public void addPlayerMember(BattleNext_MemberReport member) {
		if (member != null) playerMembers.add(member);
	}

	public void addEnemyMember(BattleNext_MemberReport member) {
		if (member != null) enemyMembers.add(member);
	}

	private static void parseMembers(JSONArray array, List<BattleNext_MemberReport> target) {
		if (array == null) return;
		for (int index = 0; index < array.length(); index++) {
			JSONObject member = array.optJSONObject(index);
			if (member != null) target.add(BattleNext_MemberReport.fromJson(member));
		}
	}

	private JSONArray membersToJson(List<BattleNext_MemberReport> members) throws JSONException {
		JSONArray array = new JSONArray();
		for (BattleNext_MemberReport member : members) {
			array.put(member.toJson());
		}
		return array;
	}
}
