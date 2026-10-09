package data.restriction;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader.LoadError;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.tools.BattleNext_Settings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class BattleNext_RestrictionRegistry {

	public static final String PATH = "data/config/BattleNext_restrictions.csv";
	public static final String CN_PATH = "data/config/BattleNext_restrictions_CN.csv";
	private static final BattleNext_RestrictionRegistry INSTANCE = new BattleNext_RestrictionRegistry();

	private static final List<String> TYPES = List.of("number", "list");

	private Map<String, BattleNext_RestrictionSpec> entries = Map.of();
	private Function<String, Object> scriptFactory;
	private List<LoadError> loadErrors = List.of();

	@LifecycleEntry
	public static BattleNext_RestrictionRegistry getInstance() {
		return INSTANCE;
	}

	public static void loadFromSettings() {
		String path = BattleNext_Settings.isCnTextEnabled() ? CN_PATH : PATH;
		try {
			JSONArray rows = BattleNext_ModFileManager.readMergedCsv(BattleNext_RestrictionRegistry.class, "id", path);
			INSTANCE.load(rows, name -> Global.getSettings().getInstanceOfScript(name));
		} catch (IOException | JSONException exception) {
			BattleNext_Logger.warn(BattleNext_RestrictionRegistry.class, "registry.source.unavailable", BattleNext_Logger.fields().put("path", path).build(), exception);
			INSTANCE.load(new JSONArray(), name -> Global.getSettings().getInstanceOfScript(name));
			INSTANCE.loadErrors = List.of(new LoadError(path, -1, "", "", exception.toString()));
		}
	}

	public void load(JSONArray rows, Function<String, Object> scriptFactory) {

		Map<String, Integer> counts = new LinkedHashMap<>();
		for (int index = 0; index < rows.length(); index++) {
			JSONObject row = rows.optJSONObject(index);
			if (row == null) continue;
			String id = row.optString("id", "").trim();
			if (!id.isEmpty()) {
				counts.merge(id, 1, Integer::sum);
			}
		}
		Map<String, BattleNext_RestrictionSpec> parsed = new LinkedHashMap<>();
		List<LoadError> errors = new ArrayList<>();
		for (int index = 0; index < rows.length(); index++) {
			String id = "";
			try {
				JSONObject row = rows.getJSONObject(index);
				id = row.getString("id").trim();
				String name = row.getString("name").trim();
				String type = row.getString("type").trim();
				String script = row.getString("script").trim();
				String description = row.getString("desc").trim();
				if (id.isEmpty() || script.isEmpty() || description.isEmpty() || !TYPES.contains(type)) throw new IllegalArgumentException("Invalid id,type,script,desc");
				if (counts.get(id) > 1) throw new IllegalArgumentException("Duplicate restriction ID: " + id);
				try {
					Object instance = scriptFactory.apply(script);
					if (!(instance instanceof BattleNext_RestrictionScript)) throw new IllegalArgumentException("Script does not implement BattleNext_RestrictionScript");
				} catch (RuntimeException exception) {
					errors.add(new LoadError(PATH, index, id, rows.opt(index).toString(), exception.toString()));
					continue;
				}
				parsed.put(id, new BattleNext_RestrictionSpec(id, name, type, script, description));
			} catch (JSONException | IllegalArgumentException exception) {
				errors.add(new LoadError(PATH, index, id, rows.opt(index).toString(), exception.toString()));
			}
		}
		entries = parsed;
		loadErrors = List.copyOf(errors);
		this.scriptFactory = scriptFactory;
		for (LoadError error : loadErrors) {
			BattleNext_Logger.warn(BattleNext_RestrictionRegistry.class, "registry.item.rejected",
					BattleNext_Logger.fields().put("path", PATH).put("position", error.position()).put("id", error.id()).put("raw", error.raw()).put("diagnostic", error.diagnostic()).build());
		}
		BattleNext_Logger.operationSucceeded(BattleNext_RestrictionRegistry.class, "registry.load",
				BattleNext_Logger.fields().put("path", PATH).put("registered_ids", entries.keySet()).build());
	}

	public List<LoadError> getLoadErrors() {
		return loadErrors;
	}

	public Object create(String id) {
		BattleNext_RestrictionSpec spec = entries.get(id);
		try {
			return scriptFactory.apply(spec.getScript());
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Unable to instantiate restriction " + id, exception);
		}
	}

	public BattleNext_RestrictionSpec get(String id) {
		return entries.get(id);
	}

	public List<BattleNext_RestrictionSpec> getAll() {
		return List.copyOf(entries.values());
	}

	public List<BattleNext_RestrictionSpec> getByType(String type) {
		return entries.values().stream().filter(entry -> entry.getType().equals(type)).toList();
	}
}
