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

public class BattleNext_FleetCommonLoader extends BattleNext_BaseLoader<BattleNext_FleetModel> {

	@Override
	public List<BattleNext_FleetModel> load() throws IOException, JSONException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_FileCatalog.common(MOD_ID, true);
		List<String> dataPaths = paths.stream().filter(path -> path.endsWith("/fleet_data.json.data"))
				.map(this::stripDataSuffix).toList();
		List<String> variantPaths = paths.stream().filter(path -> path.endsWith(".variant.data"))
				.map(this::stripDataSuffix).toList();
		for (String path : dataPaths) {
			if (!path.endsWith("/fleet_data.json")) continue;
			try {
				result.add(read(path, variantPaths));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load common fleet: " + path, exception);
			}
		}
		return result;
	}

	private BattleNext_FleetModel read(String path, List<String> variantPaths) throws IOException, JSONException {
		JSONObject json = Global.getSettings().readJSONFromCommon(path, false);
		String normalizedPath = path.replace("\\", "/");
		String folderId = normalizedPath.substring(0, normalizedPath.lastIndexOf("/fleet_data.json"));
		folderId = folderId.substring(folderId.lastIndexOf('/') + 1);
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.fromJson(json);
		fleet.setId(folderId);
		markSource(fleet, path);
		String folderPrefix = normalizedPath.substring(0, normalizedPath.lastIndexOf('/') + 1);
		for (String variantPath : variantPaths) {
			String normalizedVariant = variantPath.replace("\\", "/");
			if (!normalizedVariant.startsWith(folderPrefix) || !normalizedVariant.endsWith(".variant")) continue;
			String variantId = normalizedVariant.substring(folderPrefix.length(), normalizedVariant.length() - ".variant".length());
			try {
				String text = Global.getSettings().readTextFileFromCommon(variantPath);
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
		return BattleNext_LoadSource.COMMON;
	}

	private String stripDataSuffix(String path) {
		return path.replace("\\", "/").replaceFirst("\\.data$", "");
	}
}
