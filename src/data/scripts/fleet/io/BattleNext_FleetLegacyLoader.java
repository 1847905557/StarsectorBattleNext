package data.scripts.fleet.io;

import com.fs.starfarer.api.Global;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_CsvLine;
import data.scripts.tools.BattleNext_LoadSource;
import data.scripts.tools.BattleNext_FileCatalog;
import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_FleetLegacyLoader {

	public List<BattleNext_FleetModel> loadDirectory(Path directory) throws IOException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		if (!Files.isDirectory(directory)) return result;
		try (var stream = Files.list(directory)) {
			List<Path> dataFiles = stream.filter(path -> path.getFileName().toString().matches("player\\d+_data\\.csv"))
					.sorted().toList();
			for (Path dataFile : dataFiles) {
				String number = dataFile.getFileName().toString().replaceAll("^player(\\d+)_data\\.csv$", "$1");
				Path fleetFile = directory.resolve("player" + number + "_fleet.csv");
				if (Files.isRegularFile(fleetFile)) result.add(loadFleet(number, dataFile, fleetFile));
			}
		}
		return result;
	}

	public BattleNext_FleetModel loadFleet(String number, Path dataFile, Path fleetFile) throws IOException {
		return loadFleet(number, rows(dataFile), rows(fleetFile), dataFile.toString());
	}

	public List<BattleNext_FleetModel> loadResourceDirectory(String root, String modId) throws IOException, JSONException {
		List<BattleNext_FleetModel> result = new ArrayList<>();
		List<String> paths = BattleNext_FileCatalog.resources(root, false);
		for (String dataPath : paths) {
			String fileName = dataPath.substring(dataPath.lastIndexOf('/') + 1);
			if (!fileName.matches("player\\d+_data\\.csv")) continue;
			String number = fileName.replaceAll("^player(\\d+)_data\\.csv$", "$1");
			String fleetPath = root + "/player" + number + "_fleet.csv";
			if (!paths.contains(fleetPath)) continue;
			try {
				String dataText = Global.getSettings().loadText(dataPath, modId);
				String fleetText = Global.getSettings().loadText(fleetPath, modId);
				result.add(loadFleet(number, rows(dataText, dataPath), rows(fleetText, fleetPath), dataPath));
			} catch (IOException | JSONException exception) {
				throw new IOException("Unable to load legacy fleet " + number + " from " + root, exception);
			}
		}
		return result;
	}

	public BattleNext_FleetModel loadResourceFleet(String number, String root, String modId) throws IOException, JSONException {
		String dataPath = root + "/player" + number + "_data.csv";
		String fleetPath = root + "/player" + number + "_fleet.csv";
		return loadFleet(number,
				rows(Global.getSettings().loadText(dataPath, modId), dataPath),
				rows(Global.getSettings().loadText(fleetPath, modId), fleetPath), dataPath);
	}

	private BattleNext_FleetModel loadFleet(String number, List<String[]> dataRows, List<String[]> fleetRows, String sourcePath) throws IOException {
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
		BattleNext_FleetModel result = new BattleNext_FleetModel("ab_player" + number, column(data, 0), column(data, 1), skills, tags, members);
		result.setSourceType(BattleNext_LoadSource.LEGACY);
		result.setSourcePath(sourcePath);
		return result;
	}

	private List<String[]> rows(Path path) throws IOException {
		return rows(Files.readAllLines(path, StandardCharsets.UTF_8), path.toString());
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
