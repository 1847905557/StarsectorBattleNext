package data.scripts.fleet.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantModel;
import data.scripts.tools.BattleNext_FileCatalog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetStandardLoader extends BattleNext_BaseLoader<BattleNext_FleetModel> {

	@Override
	public List<BattleNext_FleetModel> load() throws IOException, JSONException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_FileCatalog.resources("tournament", true);
		List<String> variantPaths = paths.stream().filter(path -> path.endsWith(".variant")).toList();
		List<String> dataPaths = paths.stream().filter(path -> path.endsWith("/fleet_data.json")
				|| (path.startsWith("tournament/fleets/") && path.endsWith(".json")
				&& path.indexOf('/', "tournament/fleets/".length()) < 0)).toList();
		for (String path : dataPaths) {
			try {
				result.add(read(path, variantPaths));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load standard fleet: " + path, exception);
			}
		}
		return result;
	}

	private BattleNext_FleetModel read(String path, List<String> variantPaths) throws IOException, JSONException {
		JSONObject json = new JSONObject(Global.getSettings().loadText(path, MOD_ID));
		String normalizedPath = path.replace("\\", "/");
		boolean folderFleet = normalizedPath.endsWith("/fleet_data.json");
		String fallbackId = folderFleet
				? normalizedPath.substring(0, normalizedPath.lastIndexOf("/fleet_data.json"))
				: normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1, normalizedPath.length() - 5);
		fallbackId = fallbackId.substring(fallbackId.lastIndexOf('/') + 1);
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.fromJson(json);
		if (folderFleet || fleet.getId().isEmpty()) fleet.setId(fallbackId);
		markSource(fleet, path);
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

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}
}
