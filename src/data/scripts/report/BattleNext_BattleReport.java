package data.scripts.match;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleReport {

	private String id = "";
	private String winner = "draw";
	private long durationMillis = 0L;
	private List<BattleNext_MemberReport> members = new ArrayList<>();

	public static BattleNext_BattleReport fromJson(JSONObject json) {
		BattleNext_BattleReport report = new BattleNext_BattleReport();
		report.id = json.optString("id", "");
		report.winner = json.optString("winner", "draw");
		report.durationMillis = json.optLong("durationMillis", 0L);
		JSONArray members = json.optJSONArray("members");
		if (members != null) {
			for (int index = 0; index < members.length(); index++) {
				JSONObject member = members.optJSONObject(index);
				if (member != null) report.members.add(BattleNext_MemberReport.fromJson(member));
			}
		}
		return report;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("id", id);
		json.put("winner", winner);
		json.put("durationMillis", durationMillis);
		JSONArray members = new JSONArray();
		for (BattleNext_MemberReport member : this.members) members.put(member.toJson());
		json.put("members", members);
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

	public List<BattleNext_MemberReport> getMembers() {
		return Collections.unmodifiableList(members);
	}

	public void addMember(BattleNext_MemberReport member) {
		if (member != null) members.add(member);
	}
}
