package data.model.report;

import data.model.BattleNext_JsonModel;
import data.scripts.report.BattleNext_MemberReport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleReportModel extends BattleNext_JsonModel {

	public static final int FORMAT_VERSION = 1;

	private String id = "";
	private String winner = "draw";
	private long durationMillis = 0L;
	private long createdAt = 0L;
	private List<BattleNext_MemberReport> playerMembers = new ArrayList<>();
	private List<BattleNext_MemberReport> enemyMembers = new ArrayList<>();

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported report formatVersion: " + version);
		id = json.optString("id", "");
		winner = json.optString("winner", "draw");
		durationMillis = json.optLong("durationMillis", 0L);
		createdAt = json.optLong("createdAt", 0L);
		parseMembers(json.optJSONArray("playerMembers"), playerMembers);
		parseMembers(json.optJSONArray("enemyMembers"), enemyMembers);
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("id", id);
		json.put("winner", winner);
		json.put("durationMillis", durationMillis);
		json.put("createdAt", createdAt);
		json.put("playerMembers", membersToJson(playerMembers));
		json.put("enemyMembers", membersToJson(enemyMembers));
		return json;
	}

	@Override
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
