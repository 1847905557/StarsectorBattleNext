package data.scripts.fleet.io;

import com.fs.starfarer.api.Global;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantModel;
import data.scripts.tools.BattleNext_LoadSource;
import data.scripts.tools.BattleNext_FileCatalog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetStandardLoader {

	private static final String MOD_ID = "AI_Battles_Next";

	public List<BattleNext_FleetModel> load() {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_FileCatalog.resources("tournament", true);
		List<String> dataPaths = paths.stream().filter(path -> path.endsWith("/fleet_data.json")
				|| (path.startsWith("tournament/fleets/") && path.endsWith(".json")
				&& path.indexOf('/', "tournament/fleets/".length()) < 0)).toList();
		List<String> variantPaths = paths.stream().filter(path -> path.endsWith(".variant")).toList();
		for (String path : dataPaths) {
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
		boolean folderFleet = normalizedPath.endsWith("/fleet_data.json");
		String fallbackId = folderFleet
				? normalizedPath.substring(0, normalizedPath.lastIndexOf("/fleet_data.json"))
				: normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1, normalizedPath.length() - 5);
		fallbackId = fallbackId.substring(fallbackId.lastIndexOf('/') + 1);
		BattleNext_FleetModel fleet = BattleNext_FleetModel.fromJson(json, fallbackId);
		if (folderFleet) fleet.setId(fallbackId);
		fleet.setSourceType(BattleNext_LoadSource.STANDARD);
		fleet.setSourcePath(path);
		String folderPrefix = normalizedPath.substring(0, normalizedPath.lastIndexOf('/') + 1);
		for (String variantPath : variantPaths) {
			if (!folderFleet) break;
			String normalizedVariant = variantPath.replace("\\", "/");
			if (!normalizedVariant.startsWith(folderPrefix) || !normalizedVariant.endsWith(".variant")) continue;
			String variantId = normalizedVariant.substring(folderPrefix.length(), normalizedVariant.length() - ".variant".length());
			try {
				String text = Global.getSettings().loadText(variantPath, MOD_ID);
				fleet.putVariant(BattleNext_VariantModel.fromJson(new JSONObject(text), variantId));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load variant " + variantPath + ": " + exception.getMessage(), exception);
			}
		}
		if (fleet.hasLocalVariants()) {
			for (String variantId : fleet.getMembers().stream().map(member -> member.getVariantId()).toList()) {
				if (fleet.getVariant(variantId) == null) throw new IllegalStateException("Missing variant " + variantId + " in " + path);
			}
		}
		return fleet;
	}

}
