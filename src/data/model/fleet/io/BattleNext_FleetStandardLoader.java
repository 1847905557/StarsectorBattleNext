package data.model.fleet.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantAdapter;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileCatalog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetStandardLoader extends BattleNext_BaseLoader<BattleNext_FleetModel> {

	private static final String BASE_FOLDER = "tournament";
	private static final String BASE_ROOT = BASE_FOLDER + "/";
	private static final String DATA_FILE = "fleet_data.json";

	@Override
	public List<BattleNext_FleetModel> load() throws IOException, JSONException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_ModFileCatalog.resources(BASE_FOLDER, true);
		List<String> variantPaths = paths.stream().filter(path -> path.endsWith(".variant")).toList();
		List<String> dataPaths = paths.stream().filter(BattleNext_FleetStandardLoader::isFleetDataPath)
				.filter(path -> !BattleNext_EntryId.isReservedFolder(fleetFolder(path))).toList();
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
		String folderName = fleetFolder(normalizedPath);
		if (!BattleNext_EntryId.isValidPathSegment(folderName)) {
			throw new IOException("Invalid fleet folder name: " + folderName);
		}
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.fromJson(json);
		fleet.setFolderName(folderName);
		markSource(fleet, path);
		String folderPrefix = normalizedPath.substring(0, normalizedPath.lastIndexOf('/') + 1);
		for (String variantPath : variantPaths) {
			String normalizedVariant = variantPath.replace("\\", "/");
			if (!normalizedVariant.startsWith(folderPrefix) || !normalizedVariant.endsWith(".variant")) continue;
			if (normalizedVariant.indexOf('/', folderPrefix.length()) >= 0) continue;
			String variantId = normalizedVariant.substring(folderPrefix.length(), normalizedVariant.length() - ".variant".length());
			try {
				String text = Global.getSettings().loadText(variantPath, MOD_ID);
				fleet.putVariant(BattleNext_VariantAdapter.fromJson(new JSONObject(text), variantId));
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

	private static boolean isFleetDataPath(String path) {
		String normalized = path.replace("\\", "/");
		if (!normalized.startsWith(BASE_ROOT)) return false;
		String relative = normalized.substring(BASE_ROOT.length());
		int separator = relative.indexOf('/');
		return separator > 0 && relative.substring(separator).equals("/" + DATA_FILE);
	}

	private static String fleetFolder(String path) {
		String relative = path.substring(BASE_ROOT.length());
		return relative.substring(0, relative.indexOf('/'));
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}
}
