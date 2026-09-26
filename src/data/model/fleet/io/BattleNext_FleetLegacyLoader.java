package data.model.fleet.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_CsvLine;
import data.scripts.tools.BattleNext_FileCatalog;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_FleetLegacyLoader extends BattleNext_BaseLoader<BattleNext_FleetModel> {

	private static final String LEGACY_ROOT = "tournament";

	@Override
	public List<BattleNext_FleetModel> load() throws IOException, JSONException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_FileCatalog.resources(LEGACY_ROOT, false);
		for (String dataPath : paths) {
			String fileName = dataPath.substring(dataPath.lastIndexOf('/') + 1);
			if (!fileName.matches("player\\d+_data\\.csv")) continue;
			String number = fileName.replaceAll("^player(\\d+)_data\\.csv$", "$1");
			String fleetPath = LEGACY_ROOT + "/player" + number + "_fleet.csv";
			if (!paths.contains(fleetPath)) continue;
			try {
				String dataText = Global.getSettings().loadText(dataPath, MOD_ID);
				String fleetText = Global.getSettings().loadText(fleetPath, MOD_ID);
				result.add(readFleet(number, rows(dataText, dataPath), rows(fleetText, fleetPath), dataPath));
			} catch (IOException | JSONException exception) {
				throw new IOException("Unable to load legacy fleet " + number + " from " + LEGACY_ROOT, exception);
			}
		}
		return result;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.LEGACY;
	}

	private BattleNext_FleetModel readFleet(String number, List<String[]> dataRows, List<String[]> fleetRows,
			String sourcePath) throws IOException {
		if (dataRows.isEmpty()) throw new IOException("Missing legacy fleet metadata: " + sourcePath);
		String[] data = dataRows.get(0);
		List<String> skills = splitValues(column(data, 2));
		List<String> tags = splitValues(column(data, 3));
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (String[] row : fleetRows) {
			if (row.length < 2) {
				continue;
			}
			members.add(new BattleNext_MemberModel(column(row, 1), column(row, 4), column(row, 2),
					splitValues(column(row, 3)), column(row, 5), column(row, 6), splitValues(column(row, 7))));
		}
		BattleNext_FleetModel result = new BattleNext_FleetModel("ab_player" + number, column(data, 0), skills, tags, members);
		result.setAuthor(column(data, 1));
		markSource(result, sourcePath);
		return result;
	}

	private List<String[]> rows(String text, String source) {
		return rows(Arrays.asList(text.split("\\R", -1)), source);
	}

	private List<String[]> rows(List<String> lines, String source) {
		List<String[]> result = new ArrayList<>();
		boolean header = true;
		for (String line : lines) {
			if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
			if (header) {
				header = false;
				continue;
			}
			result.add(BattleNext_CsvLine.parse(line).toArray(new String[0]));
		}
		return result;
	}

	private String column(String[] row, int index) {
		return index < row.length ? row[index].trim() : "";
	}

	private List<String> splitValues(String value) {
		if (value.isEmpty()) return List.of();
		return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
	}
}
