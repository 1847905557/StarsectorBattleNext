package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetLoader {

	public static final String MOD_ID = "AI_Battles_Next";
	private static final String COMMON_ROOT = "saves/common/AI_Battles_Next";

	public List<BattleNext_FleetModel> loadBuiltIn() {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		for (String path : listBuiltInPaths()) {
			try {
				JSONObject json = Global.getSettings().loadJSON(path, MOD_ID);
				if (isFleet(json)) result.add(read(json, path, "built_in"));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load built-in fleet: " + path, exception);
			}
		}
		return result;
	}

	public List<BattleNext_FleetModel> loadUser() {
		List<String> paths = listUserPaths();
		if (paths.isEmpty()) paths = readIndexPaths();
		List<BattleNext_FleetModel> result = new ArrayList<>();
		for (String path : paths) {
			try {
				JSONObject json = Global.getSettings().readJSONFromCommon(stripDataSuffix(path), false);
				if (isFleet(json)) result.add(read(json, path, "user"));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load user fleet: " + path, exception);
			}
		}
		return result;
	}

	private BattleNext_FleetModel read(JSONObject json, String path, String source) {
		String fallbackId = new File(path).getName().replaceFirst("\\.json(?:\\.data)?$", "");
		BattleNext_FleetModel fleet = BattleNext_FleetModel.fromJson(json, fallbackId);
		fleet.setSource(source);
		fleet.setSourcePath(path);
		return fleet;
	}

	private List<String> listBuiltInPaths() {
		List<String> result = new ArrayList<>();
		for (String root : List.of("tournament", "tournament/fleets")) {
			result.addAll(invokeResourceList(root));
		}
		result.sort(String::compareTo);
		return unique(result);
	}

	private List<String> listUserPaths() {
		String savesPath = invokeSavesPath();
		if (savesPath.isEmpty()) return List.of();
		List<String> result = new ArrayList<>();
		String commonPath = new File(savesPath, "common").getAbsolutePath();
		for (String folder : List.of(commonPath + "/AI_Battles_Next", commonPath + "/AI_Battles_Next/fleets")) {
			List<String> reflected = invokeAbsoluteResourceList(folder, commonPath);
			result.addAll(reflected.isEmpty() ? listAbsoluteJson(folder, commonPath) : reflected);
		}
		result.sort(String::compareTo);
		return unique(result);
	}

	private List<String> readIndexPaths() {
		try {
			JSONObject index = Global.getSettings().readJSONFromCommon("fleet_index", false);
			List<String> result = new ArrayList<>();
			JSONArray paths = index.optJSONArray("paths");
			if (paths == null) return List.of();
			for (int indexValue = 0; indexValue < paths.length(); indexValue++) {
				result.add(paths.optString(indexValue, ""));
			}
			return unique(result);
		} catch (Exception exception) {
			return List.of();
		}
	}

	private List<String> invokeResourceList(String root) {
		try {
			Class<?> type = Class.forName("com.fs.starfarer.loading.LoadingUtils");
			Method method = type.getMethod("listFilesOfType", String.class, String.class);
			Object value = method.invoke(null, root, "json");
			if (value instanceof List<?> paths) {
				List<String> result = new ArrayList<>();
				for (Object path : paths) result.add(String.valueOf(path));
				return result;
			}
		} catch (Exception ignored) {
			return List.of();
		}
		return List.of();
	}

	private List<String> invokeAbsoluteResourceList(String folder, String commonPath) {
		try {
			Class<?> type = Class.forName("com.fs.starfarer.loading.LoadingUtils");
			Method method = type.getMethod("listFilesOfTypeAbsolute", String.class, String.class);
			Object value = method.invoke(null, folder, "json");
			if (value instanceof List<?> paths) {
				List<String> result = new ArrayList<>();
				for (Object path : paths) {
					String absolute = String.valueOf(path);
					String relative = absolute.substring(commonPath.length()).replace("\\", "/");
					if (relative.startsWith("/")) relative = relative.substring(1);
					if (relative.endsWith(".json") || relative.endsWith(".json.data")) result.add(relative);
				}
				return result;
			}
		} catch (Exception ignored) {
			return List.of();
		}
		return List.of();
	}

	private List<String> listAbsoluteJson(String folder, String commonPath) {
		List<String> result = new ArrayList<>();
		File directory = new File(folder);
		File[] files = directory.listFiles();
		if (files == null) return result;
		for (File file : files) {
			if (file.isFile() && (file.getName().endsWith(".json.data") || file.getName().endsWith(".json"))) {
				String absolute = file.getAbsolutePath();
				String relative = absolute.substring(commonPath.length()).replace("\\", "/");
				if (relative.startsWith("/")) relative = relative.substring(1);
				result.add(relative);
			}
		}
		return result;
	}

	private String invokeSavesPath() {
		try {
			Class<?> type = Class.forName("com.fs.starfarer.settings.StarfarerSettings");
			return String.valueOf(type.getMethod("getSavesPath").invoke(null));
		} catch (Exception ignored) {
			return "";
		}
	}

	private boolean isFleet(JSONObject json) { return json != null && json.optJSONArray("members") != null; }
	private String stripDataSuffix(String path) { return path.replace("\\", "/").replaceFirst("\\.data$", ""); }
	private List<String> unique(List<String> values) {
		Map<String, String> ordered = new LinkedHashMap<>();
		for (String value : values) ordered.put(value.replace("\\", "/"), value);
		return new ArrayList<>(ordered.values());
	}
}
