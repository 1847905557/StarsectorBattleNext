package data.model.rule;

import data.model.BattleNext_JsonModel;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class BattleNext_RuleRestriction {

	private final String id;
	private final String type;
	private boolean enabled;
	private double value;
	private List<String> items = new ArrayList<>();
	private JSONObject unregisteredJson;
	private final List<String> loadErrors = new ArrayList<>();

	public BattleNext_RuleRestriction(String id, String type) {
		if (id == null || id.isBlank() || id.contains(".")) throw new IllegalArgumentException("Invalid restriction ID: " + id);
		if (!"number".equals(type) && !"list".equals(type)) throw new IllegalArgumentException("Invalid restriction type: " + type);
		this.id = id;
		this.type = type;
	}

	public static BattleNext_RuleRestriction fromJson(JSONObject json) throws JSONException {
		String id = json.getString("id");
		if (json.has("value") == json.has("items")) {
			throw new IllegalArgumentException("Restriction requires exactly one value or items: " + id);
		}
		String type = json.has("value") ? "number" : "list";
		BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id, type);
		BattleNext_RestrictionSpec entry = BattleNext_RestrictionRegistry.getInstance().get(id);
		if (entry != null && !entry.getType().equals(type)) {
			throw new IllegalArgumentException("Restriction type differs from registry: " + id);
		}
		restriction.enabled = json.getBoolean("enabled");
		if ("number".equals(type)) {
			restriction.setValue(json.getDouble("value"));
		} else {
			JSONArray values = json.getJSONArray("items");
			List<String> parsed = BattleNext_JsonModel.parseStrings("items", values, restriction.loadErrors);
			Map<String, Integer> counts = new HashMap<>();
			for (String item : parsed) {
				counts.merge(item.trim(), 1, Integer::sum);
			}
			for (int index = 0; index < values.length(); index++) {
				if (!(values.opt(index) instanceof String item)) continue;
				String value = item.trim();
				if (value.isEmpty() || counts.get(value) > 1) {
					restriction.loadErrors.add("items[" + index + "] " + item + ": Invalid or conflicting restriction item: " + id);
					continue;
				}
				restriction.items.add(value);
			}
		}
		if (entry == null || !restriction.loadErrors.isEmpty()) restriction.unregisteredJson = new JSONObject(json.toString());
		return restriction;
	}

	public JSONObject toJson() throws JSONException {
		if (unregisteredJson != null) return new JSONObject(unregisteredJson.toString());
		JSONObject json = new JSONObject().put("id", id).put("enabled", enabled);
		if ("number".equals(type)) json.put("value", value);
		else json.put("items", new JSONArray(items));
		return json;
	}

	public String getId() {
		return id;
	}

	public List<String> getLoadErrors() {
		return List.copyOf(loadErrors);
	}

	public String getType() {
		return type;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		unregisteredJson = null;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		if (!Double.isFinite(value) || value < 0d) {
			throw new IllegalArgumentException("Invalid restriction value: " + id);
		}
		this.value = value;
		unregisteredJson = null;
	}

	public List<String> getItems() {
		return Collections.unmodifiableList(items);
	}

	public void setItems(List<String> values) {
		List<String> normalized = new ArrayList<>();
		for (String item : values) {
			if (item == null || item.isBlank()) throw new IllegalArgumentException("Empty restriction item: " + id);
			normalized.add(item.trim());
		}
		if (Set.copyOf(normalized).size() != normalized.size()) {
			throw new IllegalArgumentException("Duplicate restriction item: " + id);
		}
		items = normalized;
		unregisteredJson = null;
	}
}
