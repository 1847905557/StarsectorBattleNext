package data.model.rule;

import data.model.BattleNext_JsonModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_RuleModel extends BattleNext_JsonModel {

	public static final int FORMAT_VERSION = 1;

	private String id;
	private String name;
	private final List<BattleNext_RuleRestriction> restrictions = new ArrayList<>();

	public BattleNext_RuleModel() {
		this("", "");
	}

	public BattleNext_RuleModel(String id, String name) {
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		if (json.getInt("formatVersion") != FORMAT_VERSION) {
			throw new IllegalArgumentException("Unsupported rule formatVersion");
		}
		String parsedId = json.getString("id").trim();
		String parsedName = json.getString("name").trim();
		JSONArray values = json.getJSONArray("restrictions");
		List<BattleNext_RuleRestriction> parsed = new ArrayList<>();
		for (int index = 0; index < values.length(); index++) {
			parsed.add(BattleNext_RuleRestriction.fromJson(values.getJSONObject(index)));
		}
		setRestrictions(parsed);
		id = parsedId;
		name = parsedName;
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject().put("formatVersion", FORMAT_VERSION).put("id", id).put("name", name);
		JSONArray values = new JSONArray();
		for (BattleNext_RuleRestriction restriction : restrictions) {
			values.put(restriction.toJson());
		}
		return json.put("restrictions", values);
	}

	@Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id == null ? "" : id.trim();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name.trim();
	}

	public List<BattleNext_RuleRestriction> getRestrictions() {
		return Collections.unmodifiableList(restrictions);
	}

	public BattleNext_RuleRestriction getRestriction(String restrictionId) {
		for (BattleNext_RuleRestriction restriction : restrictions) {
			if (restriction.getId().equals(restrictionId)) return restriction;
		}
		return null;
	}

	public void setRestrictions(List<BattleNext_RuleRestriction> values) {
		Set<String> ids = new HashSet<>();
		for (BattleNext_RuleRestriction restriction : values) {
			if (!ids.add(restriction.getId())) {
				throw new IllegalArgumentException("Duplicate restriction ID: " + restriction.getId());
			}
		}
		List<BattleNext_RuleRestriction> parsed = new ArrayList<>(values);
		restrictions.clear();
		restrictions.addAll(parsed);
	}
}
