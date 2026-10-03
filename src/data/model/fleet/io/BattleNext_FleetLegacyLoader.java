package data.model.fleet.io;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_FleetLegacyLoader extends BattleNext_BaseLoader<BattleNext_FleetModel> {

	private static final String LEGACY_FOLDER = "tournament";

	@Override
	public List<BattleNext_FleetModel> load() throws IOException, JSONException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_ModFileManager.list(LEGACY_FOLDER, false);
		for (String dataPath : paths) {
			String fileName = dataPath.substring(dataPath.lastIndexOf('/') + 1);
			if (!fileName.matches("player\\d+_data\\.csv")) continue;
			String number = fileName.replaceAll("^player(\\d+)_data\\.csv$", "$1");
			String fleetPath = LEGACY_FOLDER + "/player" + number + "_fleet.csv";
			if (!paths.contains(fleetPath)) {
				BattleNext_Logger.warn(BattleNext_FleetLegacyLoader.class, "fleet.legacy.incomplete",
				BattleNext_Logger.fields().put("metadata_path", dataPath).put("missing_members_path", fleetPath).put("number", number).build());
				continue;
			}
			result.add(readFleet(number, dataPath, fleetPath));
		}
		return result;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.LEGACY;
	}

	protected BattleNext_FleetModel readFleet(String number, String dataPath, String fleetPath)
			throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("number", number).put("metadata_path", dataPath).put("members_path", fleetPath).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetLegacyLoader.class, "fleet.legacy.load", input);
		try {

			try {
				JSONArray dataRows = BattleNext_ModFileManager.readCsv(BattleNext_FleetLegacyLoader.class, dataPath, MOD_ID);
				JSONObject data = metadata(dataRows, dataPath);
				JSONArray fleetRows = BattleNext_ModFileManager.readCsv(BattleNext_FleetLegacyLoader.class, fleetPath, MOD_ID);
				List<BattleNext_MemberModel> members = new ArrayList<>();
				for (int index = 0; index < fleetRows.length(); index++) {
					JSONObject row = fleetRows.getJSONObject(index);
					if (isEmpty(row, "rowNumber", "variant", "personality", "skills", "name", "portrait", "orders", "tags")) continue;
					members.add(new BattleNext_MemberModel(row.optString("variant").trim(), row.optString("name").trim(),
							row.optString("personality").trim(), splitValues(row.optString("skills")),
							row.optString("portrait").trim(), row.optString("orders").trim(), splitValues(row.optString("tags"))));
				}
				BattleNext_FleetModel result = new BattleNext_FleetModel("ab_player" + number, data.optString("name").trim(),
						splitValues(data.optString("skills")), splitValues(data.optString("tags")), members);
				result.setFolderName("legacy_" + number);
				result.setAuthor(data.optString("author").trim());
				markSource(result, dataPath);
				BattleNext_Logger.operationSucceeded(BattleNext_FleetLegacyLoader.class, "fleet.legacy.load",
					BattleNext_Logger.fields().put("fleet", result).put("metadata_path", dataPath).put("members_path", fleetPath).build());
				return result;
			} catch (IOException | JSONException exception) {
				throw new IOException("Unable to load legacy fleet from " + dataPath + " and " + fleetPath
						+ ": " + exception.getMessage(), exception);
			}
		} catch (IOException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_FleetLegacyLoader.class, "fleet.legacy.load", input, exception);
			throw exception;
		}
	}

	private JSONObject metadata(JSONArray rows, String path) throws IOException, JSONException {
		for (int index = 0; index < rows.length(); index++) {
			JSONObject row = rows.getJSONObject(index);
			if (isEmpty(row, "name", "author", "skills", "tags")) continue;
			return row;
		}
		throw new IOException("Missing legacy fleet metadata: " + path);
	}

	private boolean isEmpty(JSONObject row, String... columns) {
		return Arrays.stream(columns).allMatch(column -> row.optString(column).isBlank());
	}

	private List<String> splitValues(String value) {
		if (value.isEmpty()) return List.of();
		return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
	}
}
