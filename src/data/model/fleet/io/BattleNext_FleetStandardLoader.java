package data.model.fleet.io;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

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
		List<String> paths = BattleNext_ModFileManager.list(BASE_FOLDER, true);
		List<String> dataPaths = paths.stream().filter(BattleNext_FleetStandardLoader::isFleetDataPath)
				.filter(path -> !BattleNext_EntryId.isReservedFolder(fleetFolder(path))).toList();
		for (String path : dataPaths) {
			try {
				result.add(read(path, paths));
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load standard fleet: " + path + ": " + exception.getMessage(), exception);
			}
		}
		return result;
	}

	private BattleNext_FleetModel read(String path, List<String> paths) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetStandardLoader.class, "fleet.resource.load", input);
		try {
			String normalizedPath = path.replace("\\", "/");
			String folderName = fleetFolder(normalizedPath);
			if (!BattleNext_EntryId.isValidPathSegment(folderName)) {
				throw new IOException("Invalid fleet folder name: " + folderName);
			}
			BattleNext_FleetModel fleet = new BattleNext_FleetModel();
			fleet.fromJson(BattleNext_ModFileManager.readJson(BattleNext_FleetStandardLoader.class, path, MOD_ID));
			fleet.setFolderName(folderName);
			markSource(fleet, path);
			BattleNext_FleetVariantLoader.load(fleet, paths);
			BattleNext_Logger.operationSucceeded(BattleNext_FleetStandardLoader.class, "fleet.resource.load",
					BattleNext_Logger.fields().put("path", path).put("fleet", fleet).build());
			return fleet;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_FleetStandardLoader.class, "fleet.resource.load", input, exception);
			throw exception;
		}
	}

	private static boolean isFleetDataPath(String path) {
		String normalized = path.replace("\\", "/");
		if (!normalized.startsWith(BASE_ROOT)) return false;
		String relative = normalized.substring(BASE_ROOT.length());
		int separator = relative.indexOf('/');
		return separator > 0 && !BattleNext_EntryId.isReservedFolder(relative.substring(0, separator))
				&& relative.substring(separator).equals("/" + DATA_FILE);
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
