package data.order;

import com.fs.starfarer.api.Global;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class BattleNext_OrderRegistry {

	public static final String PATH = "data/config/BattleNext_orders.csv";
	private static final BattleNext_OrderRegistry INSTANCE = new BattleNext_OrderRegistry();

	private static final List<String> TYPES = List.of("starter", "filter", "picker", "suffix");

	private Map<String, BattleNext_OrderSpec> entries = Map.of();
	private Function<String, Object> scriptFactory;

	@LifecycleEntry
	public static BattleNext_OrderRegistry getInstance() {
		return INSTANCE;
	}

	public static void loadFromSettings() {
		try {
			JSONArray rows = Global.getSettings().getMergedSpreadsheetData("id", PATH);
			INSTANCE.load(rows, name -> Global.getSettings().getInstanceOfScript(name));
		} catch (IOException | JSONException exception) {
			throw new IllegalArgumentException("Unable to read " + PATH, exception);
		}
	}

	public void load(JSONArray rows, Function<String, Object> scriptFactory) throws JSONException {
		Map<String, BattleNext_OrderSpec> parsed = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		for (int index = 0; index < rows.length(); index++) {
			JSONObject row = rows.getJSONObject(index);
			String id = row.optString("id", "").trim();
			String type = row.optString("type", "").trim();
			String script = row.optString("script", "").trim();
			String description = row.optString("desc", "").trim();
			if (id.isEmpty() || script.isEmpty() || description.isEmpty() || !TYPES.contains(type)) {
				errors.add("row " + (index + 1) + ": invalid id,type,script,desc");
				continue;
			}
			if (parsed.containsKey(id)) {
				errors.add("row " + (index + 1) + ": duplicate order " + id);
				continue;
			}
			try {
				Object instance = scriptFactory.apply(script);
				Class<?> contract = contractFor(type);
				if (!contract.isInstance(instance)) {
					errors.add(id + ": script does not implement " + contract.getSimpleName());
					continue;
				}
				parsed.put(id, new BattleNext_OrderSpec(id, type, script, description));
			} catch (RuntimeException exception) {
				errors.add(id + ": cannot instantiate " + script + ": " + exception.getMessage());
			}
		}
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Invalid " + PATH + ": " + String.join("; ", errors));
		}
		entries = parsed;
		this.scriptFactory = scriptFactory;
	}

	private Class<?> contractFor(String type) {
		if ("starter".equals(type)) return BattleNext_OrderStarter.class;
		if ("filter".equals(type)) return BattleNext_OrderFilter.class;
		if ("picker".equals(type)) return BattleNext_OrderPicker.class;
		if ("suffix".equals(type)) return BattleNext_OrderSuffix.class;
		throw new IllegalArgumentException("Unknown order type: " + type);
	}

	public Object create(String id) {
		BattleNext_OrderSpec spec = entries.get(id);
		try {
			return scriptFactory.apply(spec.getScript());
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Unable to instantiate order " + id, exception);
		}
	}

	public BattleNext_OrderSpec get(String id) {
		return entries.get(id);
	}

	public List<BattleNext_OrderSpec> getAll() {
		return List.copyOf(entries.values());
	}

	public List<BattleNext_OrderSpec> getByType(String type) {
		return entries.values().stream().filter(entry -> entry.getType().equals(type)).toList();
	}
}
