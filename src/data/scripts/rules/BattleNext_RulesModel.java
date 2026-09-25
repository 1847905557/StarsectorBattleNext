package data.scripts.rules;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_RulesModel {

	public static final int FORMAT_VERSION = 1;

	private int slot;
	private String id;
	private String name;
	private final List<BattleNext_RulesModel> children = new ArrayList<>();
	private boolean enabled;
	private double value;

	public BattleNext_RulesModel(int slot, String id, String name) {
		if (slot < 0 || slot > 31) {
			throw new IllegalArgumentException("Rules slot must be between 0 and 31");
		}
		this.slot = slot;
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
	}

	public static BattleNext_RulesModel fromJson(JSONObject json) {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported rules formatVersion: " + version);
		BattleNext_RulesModel model = new BattleNext_RulesModel(json.optInt("slot", 0), json.optString("id", ""), json.optString("name", ""));
		model.enabled = json.optBoolean("enabled", false);
		model.value = json.optDouble("value", 0d);
		JSONArray children = json.optJSONArray("children");
		if (children != null) {
			for (int index = 0; index < children.length(); index++) {
				if (children.optJSONObject(index) != null) {
					model.children.add(fromJson(children.optJSONObject(index)));
				}
			}
		}
		return model;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("slot", slot);
		json.put("id", id);
		json.put("name", name);
		json.put("enabled", enabled);
		json.put("value", value);
		JSONArray values = new JSONArray();
		for (BattleNext_RulesModel child : children) {
			values.put(child.toJson());
		}
		json.put("children", values);
		return json;
	}

	public int getSlot() {
		return slot;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public List<BattleNext_RulesModel> getChildren() {
		return Collections.unmodifiableList(children);
	}

	public void addChild(BattleNext_RulesModel child) {
		if (child != null) {
			children.add(child);
		}
	}
}
