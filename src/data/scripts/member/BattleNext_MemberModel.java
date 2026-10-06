package data.scripts.member;

import data.model.BattleNext_JsonModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_MemberModel {

	public static final int FORMAT_VERSION = 1;

	private String variantId;
	private String name;
	private String personality;
	private List<String> skills;
	private String portrait;
	private String orders;
	private List<String> tags;
	private final List<String> loadErrors = new ArrayList<>();
	private JSONObject incompleteJson;

	public BattleNext_MemberModel() {
		this("", "", "", List.of(), "", "", List.of());
	}

	public BattleNext_MemberModel(String variantId, String name, String personality, List<String> skills,
			String portrait, String orders, List<String> tags) {
		this.variantId = normalize(variantId);
		this.name = normalize(name);
		this.personality = normalize(personality);
		this.skills = copy(skills);
		this.portrait = normalize(portrait);
		this.orders = normalize(orders);
		this.tags = copy(tags);
	}

	public static BattleNext_MemberModel fromJson(JSONObject json) throws JSONException {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported member formatVersion: " + version);
		List<String> errors = new ArrayList<>();
		List<String> skills = BattleNext_JsonModel.parseStrings("skills", json.optJSONArray("skills"), errors);
		List<String> tags = BattleNext_JsonModel.parseStrings("tags", json.optJSONArray("tags"), errors);
		BattleNext_MemberModel member = new BattleNext_MemberModel(json.optString("variant_id", ""), json.optString("name", ""),
				json.optString("personality", ""), skills, json.optString("portrait", ""),
				json.optString("orders", ""), tags);
		member.loadErrors.addAll(errors);
		if (!errors.isEmpty()) {
			member.incompleteJson = new JSONObject(json.toString());
		}
		return member;
	}

	public JSONObject toJson() throws JSONException {
		if (incompleteJson != null) return new JSONObject(incompleteJson.toString());
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("variant_id", variantId);
		json.put("name", name);
		json.put("personality", personality);
		json.put("skills", new JSONArray(skills));
		json.put("portrait", portrait);
		json.put("orders", orders);
		json.put("tags", new JSONArray(tags));
		return json;
	}

	public List<String> getLoadErrors() {
		return List.copyOf(loadErrors);
	}

	public String getVariantId() {
		return variantId;
	}

	public void setVariantId(String variantId) {
		this.variantId = normalize(variantId);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = normalize(name);
	}

	public String getPersonality() {
		return personality;
	}

	public void setPersonality(String personality) {
		this.personality = normalize(personality);
	}

	public List<String> getSkills() {
		return Collections.unmodifiableList(skills);
	}

	public void setSkills(List<String> skills) {
		this.skills = copy(skills);
	}

	public String getPortrait() {
		return portrait;
	}

	public void setPortrait(String portrait) {
		this.portrait = normalize(portrait);
	}

	public String getOrders() {
		return orders;
	}

	public void setOrders(String orders) {
		this.orders = normalize(orders);
	}

	public List<String> getTags() {
		return Collections.unmodifiableList(tags);
	}

	public void setTags(List<String> tags) {
		this.tags = copy(tags);
	}

	private static List<String> copy(List<String> values) {
		if (values == null || values.isEmpty()) {
			return new ArrayList<>();
		}
		List<String> result = new ArrayList<>();
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				result.add(value.trim());
			}
		}
		return result;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
