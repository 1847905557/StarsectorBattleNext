package data.scripts.rules;

import data.model.BattleNext_BaseModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_RulesModel extends BattleNext_BaseModel {

	public static final int FORMAT_VERSION = 1;

	private String id;
	private String name;
	private final List<BattleNext_RulesModel> children = new ArrayList<>();
	private boolean enabled;
	private double value;

	public BattleNext_RulesModel() {
		this("", "");
	}

	public BattleNext_RulesModel(String id, String name) {
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported rules formatVersion: " + version);
		id = json.optString("id", "").trim();
		name = json.optString("name", "").trim();
		enabled = json.optBoolean("enabled", false);
		value = json.optDouble("value", 0d);
		JSONArray childValues = json.optJSONArray("children");
		if (childValues != null) {
			for (int index = 0; index < childValues.length(); index++) {
				JSONObject childJson = childValues.optJSONObject(index);
				if (childJson != null) {
					BattleNext_RulesModel child = new BattleNext_RulesModel();
					child.fromJson(childJson);
					children.add(child);
				}
			}
		}
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
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

	@Override
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
