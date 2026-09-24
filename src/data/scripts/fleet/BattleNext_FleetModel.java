package data.scripts.fleet;

import data.scripts.member.BattleNext_MemberModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_FleetModel {

	public static final int FORMAT_VERSION = 1;

	private String id;
	private String name;
	private String player;
	private List<String> skills;
	private List<String> tags;
	private List<BattleNext_MemberModel> members;
	private String author;
	private String description;
	private String behavior;
	private String color;
	private String vsImage;
	private int rulesSlot;
	private String source;
	private String sourcePath;

	public BattleNext_FleetModel() {
		this("", "", "", List.of(), List.of(), List.of());
	}

	public BattleNext_FleetModel(String id, String name, String player, List<String> skills, List<String> tags,
			List<BattleNext_MemberModel> members) {
		this.id = normalize(id);
		this.name = normalize(name);
		this.player = normalize(player);
		this.skills = copyStrings(skills);
		this.tags = copyStrings(tags);
		this.members = copyMembers(members);
		this.author = "";
		this.description = "";
		this.behavior = "";
		this.color = "";
		this.vsImage = "";
		this.rulesSlot = -1;
		this.source = "native";
		this.sourcePath = "";
	}

	public static BattleNext_FleetModel fromJson(JSONObject json, String fallbackId) {
		List<BattleNext_MemberModel> members = new ArrayList<>();
		JSONArray memberArray = json.optJSONArray("members");
		if (memberArray != null) {
			for (int index = 0; index < memberArray.length(); index++) {
				JSONObject member = memberArray.optJSONObject(index);
				if (member != null) {
					members.add(BattleNext_MemberModel.fromJson(member));
				}
			}
		}

		String id = json.optString("id", fallbackId);
		BattleNext_FleetModel result = new BattleNext_FleetModel(id, json.optString("name", ""),
				json.optString("player", ""), readStrings(json.optJSONArray("skills")),
				readStrings(json.optJSONArray("tags")), members);
		result.author = json.optString("author", "");
		result.description = json.optString("description", "");
		result.behavior = json.optString("behavior", "");
		result.color = json.optString("color", "");
		result.vsImage = json.optString("vsImage", "");
		result.rulesSlot = json.optInt("rulesSlot", -1);
		return result;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("id", id);
		json.put("name", name);
		json.put("player", player);
		json.put("skills", new JSONArray(skills));
		json.put("tags", new JSONArray(tags));
		JSONArray memberArray = new JSONArray();
		for (BattleNext_MemberModel member : members) {
			memberArray.put(member.toJson());
		}
		json.put("members", memberArray);
		json.put("author", author);
		json.put("description", description);
		json.put("behavior", behavior);
		json.put("color", color);
		json.put("vsImage", vsImage);
		json.put("rulesSlot", rulesSlot);
		return json;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = normalize(id); }
	public String getName() { return name; }
	public void setName(String name) { this.name = normalize(name); }
	public String getPlayer() { return player; }
	public void setPlayer(String player) { this.player = normalize(player); }
	public List<String> getSkills() { return Collections.unmodifiableList(skills); }
	public void setSkills(List<String> skills) { this.skills = copyStrings(skills); }
	public List<String> getTags() { return Collections.unmodifiableList(tags); }
	public void setTags(List<String> tags) { this.tags = copyStrings(tags); }
	public List<BattleNext_MemberModel> getMembers() { return Collections.unmodifiableList(members); }
	public void setMembers(List<BattleNext_MemberModel> members) { this.members = copyMembers(members); }
	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = normalize(author); }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = normalize(description); }
	public String getBehavior() { return behavior; }
	public void setBehavior(String behavior) { this.behavior = normalize(behavior); }
	public String getColor() { return color; }
	public void setColor(String color) { this.color = normalize(color); }
	public String getVsImage() { return vsImage; }
	public void setVsImage(String vsImage) { this.vsImage = normalize(vsImage); }
	public int getRulesSlot() { return rulesSlot; }
	public void setRulesSlot(int rulesSlot) { this.rulesSlot = rulesSlot; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = normalize(source); }
	public String getSourcePath() { return sourcePath; }
	public void setSourcePath(String sourcePath) { this.sourcePath = normalize(sourcePath); }

	private static List<String> readStrings(JSONArray values) {
		List<String> result = new ArrayList<>();
		if (values == null) return result;
		for (int index = 0; index < values.length(); index++) {
			String value = values.optString(index, "").trim();
			if (!value.isEmpty()) result.add(value);
		}
		return result;
	}

	private static List<String> copyStrings(List<String> values) {
		List<String> result = new ArrayList<>();
		if (values == null) return result;
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) result.add(value.trim());
		}
		return result;
	}

	private static List<BattleNext_MemberModel> copyMembers(List<BattleNext_MemberModel> values) {
		List<BattleNext_MemberModel> result = new ArrayList<>();
		if (values != null) {
			for (BattleNext_MemberModel value : values) if (value != null) result.add(value);
		}
		return result;
	}

	private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
