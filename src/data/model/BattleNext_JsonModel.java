package data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BattleNext_JsonModel extends BattleNext_BaseModel {

	private final List<String> loadErrors = new ArrayList<>();
	private final Map<String, JSONArray> incompleteCollections = new LinkedHashMap<>();

	@FunctionalInterface
	public interface ItemParser<T> {
		T parse(JSONObject json) throws JSONException;
	}

	protected void beginParse() {
		loadErrors.clear();
		incompleteCollections.clear();
	}

	protected <T> List<T> readCollection(String key, JSONArray values, ItemParser<T> parser) {
		int previousErrors = loadErrors.size();
		List<T> parsed = parseCollection(key, values, parser, loadErrors);
		if (loadErrors.size() != previousErrors) {
			retainCollection(key, values);
		}
		return parsed;
	}

	public static <T> List<T> parseCollection(String key, JSONArray values, ItemParser<T> parser, List<String> errors) {
		List<T> parsed = new ArrayList<>();
		for (int index = 0; index < values.length(); index++) {
			try {
				parsed.add(parser.parse(values.getJSONObject(index)));
			} catch (JSONException | IllegalArgumentException exception) {
				errors.add(key + "[" + index + "] " + values.opt(index) + ": " + exception);
			}
		}
		return parsed;
	}

	protected <T> List<T> readIdentifiedCollection(String key, JSONArray values, ItemParser<T> parser) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject item = values.optJSONObject(index);
			if (item == null) continue;
			String id = item.optString("id", "");
			if (id.isEmpty()) continue;
			counts.merge(id, 1, Integer::sum);
		}
		return readCollection(key, values, json -> {
			String id = json.getString("id");
			if (counts.getOrDefault(id, 0) > 1) throw new IllegalArgumentException("Duplicate ID: " + id);
			return parser.parse(json);
		});
	}

	protected JSONArray writeCollection(String key, JSONArray parsed) throws JSONException {
		JSONArray raw = incompleteCollections.get(key);
		return raw == null ? parsed : new JSONArray(raw.toString());
	}

	protected void retainCollection(String key, JSONArray raw) {
		try {
			incompleteCollections.put(key, new JSONArray(raw.toString()));
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to retain collection: " + key, exception);
		}
	}

	protected List<String> readStringCollection(String key, JSONArray values) {
		int previousErrors = loadErrors.size();
		List<String> parsed = parseStrings(key, values, loadErrors);
		if (loadErrors.size() != previousErrors) {
			retainCollection(key, values);
		}
		return parsed;
	}

	public static List<String> parseStrings(String key, JSONArray values, List<String> errors) {
		List<String> parsed = new ArrayList<>();
		if (values == null) return parsed;
		for (int index = 0; index < values.length(); index++) {
			try {
				if (!(values.get(index) instanceof String value)) throw new JSONException("Expected a string");
				parsed.add(value);
			} catch (JSONException exception) {
				errors.add(key + "[" + index + "] " + values.opt(index) + ": " + exception);
			}
		}
		return parsed;
	}

	public void addLoadError(String error) {
		loadErrors.add(error);
	}

	public List<String> getLoadErrors() {
		return List.copyOf(loadErrors);
	}

	public abstract void fromJson(JSONObject json) throws JSONException;

	public abstract JSONObject toJson() throws JSONException;
}
