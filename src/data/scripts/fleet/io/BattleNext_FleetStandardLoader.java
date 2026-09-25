package data.scripts.fleet.io;

import com.fs.starfarer.api.Global;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantModel;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetStandardLoader {

	private static final String MOD_ID = "AI_Battles_Next";

	public List<BattleNext_FleetModel> load() {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> dataPaths = invokeResourceList("tournament", "json");
		List<String> variantPaths = invokeResourceList("tournament", "variant");
		for (String path : dataPaths) {
			if (!path.endsWith("/fleet_data.json")) continue;
			try {
				JSONObject json = new JSONObject(Global.getSettings().loadText(path, MOD_ID));
				result.add(read(json, path, variantPaths));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load standard fleet: " + path, exception);
			}
		}
		return result;
	}

	private BattleNext_FleetModel read(JSONObject json, String path, List<String> variantPaths) {
		String normalizedPath = path.replace("\\", "/");
		String folderId = normalizedPath.substring(0, normalizedPath.lastIndexOf("/fleet_data.json"));
		folderId = folderId.substring(folderId.lastIndexOf('/') + 1);
		BattleNext_FleetModel fleet = BattleNext_FleetModel.fromJson(json, folderId);
		fleet.setId(folderId);
		fleet.setSourceType(BattleNext_LoadSource.STANDARD);
		fleet.setSourcePath(path);
		String folderPrefix = normalizedPath.substring(0, normalizedPath.lastIndexOf('/') + 1);
		for (String variantPath : variantPaths) {
			String normalizedVariant = variantPath.replace("\\", "/");
			if (!normalizedVariant.startsWith(folderPrefix) || !normalizedVariant.endsWith(".variant")) continue;
			String variantId = normalizedVariant.substring(folderPrefix.length(), normalizedVariant.length() - ".variant".length());
			try {
				String text = Global.getSettings().loadText(variantPath, MOD_ID);
				fleet.putVariant(BattleNext_VariantModel.fromJson(new JSONObject(text), variantId));
			} catch (IOException | JSONException exception) {
				fleet.addLoadError("Unable to load variant " + variantId + ": " + exception.getMessage());
			}
		}
		for (String variantId : fleet.getMembers().stream().map(member -> member.getVariantId()).toList()) {
			if (fleet.getVariant(variantId) == null) fleet.addLoadError("Missing variant: " + variantId);
		}
		return fleet;
	}

	private List<String> invokeResourceList(String root, String extension) {
		try {
			Class<?> type = Class.forName("com.fs.starfarer.loading.LoadingUtils");
			Method method = type.getMethod("listFilesOfType", String.class, String.class);
			Object value = method.invoke(null, root, extension);
			return normalizePaths(value);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private List<String> normalizePaths(Object value) {
		if (!(value instanceof List<?> paths)) return List.of();
		Map<String, String> unique = new LinkedHashMap<>();
		for (Object path : paths) {
			String normalized = String.valueOf(path).replace("\\", "/");
			unique.put(normalized, normalized);
		}
		List<String> result = new ArrayList<>(unique.values());
		result.sort(String::compareTo);
		return result;
	}
}
