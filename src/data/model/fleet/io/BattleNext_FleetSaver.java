package data.model.fleet.io;

import data.model.BattleNext_BaseSaver;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantAdapter;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class BattleNext_FleetSaver extends BattleNext_BaseSaver<BattleNext_FleetModel> {

	private static final String BASE_FOLDER = "tournament";
	private static final String BASE_ROOT = BASE_FOLDER + "/";
	private static final String DATA_FILE = "fleet_data.json";

	@Override
	public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {
		if (!BattleNext_EntryId.isValidPathSegment(fleet.getFolderName())) {
			throw new IOException("Invalid fleet folder name: " + fleet.getFolderName());
		}
		String directory = BASE_ROOT + fleet.getFolderName() + "/";
		String path = directory + DATA_FILE;
		if (BattleNext_ModFileManager.exists(path)) {
			JSONObject previous = new JSONObject(BattleNext_ModFileManager.readText(path));
			writeResourceJson(directory + DATA_FILE + ".bak", previous);
		}
		writeResourceJson(path, fleet.toJson());
		for (BattleNext_VariantAdapter variant : fleet.getVariants()) {
			if (!BattleNext_EntryId.isValidPathSegment(variant.getId())) {
				throw new IOException("Invalid fleet variant ID: " + variant.getId());
			}
			BattleNext_ModFileManager.writeText(directory + variant.getId() + ".variant", variant.toJson().toString(2));
		}
		String oldFolder = previousFolder(fleet.getSourcePath());
		if (oldFolder != null && !BattleNext_EntryId.sameFileName(oldFolder, fleet.getFolderName())) {
			BattleNext_ModFileManager.deleteRecursively(BASE_ROOT + oldFolder);
		}
		return path;
	}

	@Override
	public void delete(BattleNext_FleetModel fleet) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(fleet.getFolderName())) {
			throw new IOException("Invalid fleet folder name: " + fleet.getFolderName());
		}
		BattleNext_ModFileManager.deleteRecursively(BASE_ROOT + fleet.getFolderName());
	}

	private String previousFolder(String sourcePath) {
		if (sourcePath == null) return null;
		String normalized = sourcePath.replace('\\', '/');
		if (!normalized.startsWith(BASE_ROOT) || !normalized.endsWith("/" + DATA_FILE)) return null;
		String folder = normalized.substring(BASE_ROOT.length(), normalized.length() - ("/" + DATA_FILE).length());
		return folder.indexOf('/') < 0 ? folder : null;
	}
}
