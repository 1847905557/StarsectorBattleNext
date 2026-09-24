package data.scripts.fleet;

import data.scripts.member.BattleNext_MemberModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BattleNext_LegacyLoader {

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
		List<String[]> dataRows = rows(dataFile);
		if (dataRows.isEmpty()) throw new IOException("Missing legacy fleet metadata: " + dataFile);
		String[] data = dataRows.get(0);
		List<String> skills = splitValues(column(data, 2));
		List<String> tags = splitValues(column(data, 3));
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (String[] row : rows(fleetFile)) {
			if (row.length < 2) continue;
			members.add(new BattleNext_MemberModel(column(row, 1), column(row, 4), column(row, 2),
					splitValues(column(row, 3)), column(row, 5), column(row, 6), splitValues(column(row, 7))));
		}
		BattleNext_FleetModel result = new BattleNext_FleetModel("ab_player" + number, column(data, 0), column(data, 1), skills, tags, members);
		result.setSource("legacy");
		result.setSourcePath(dataFile.toString());
		return result;
	}

	private List<String[]> rows(Path path) throws IOException {
		List<String[]> result = new ArrayList<>();
		boolean header = true;
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
			if (header) {
				header = false;
				continue;
			}
			result.add(parse(line));
		}
		return result;
	}

	private String[] parse(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char current = line.charAt(index);
			if (current == '"') quoted = !quoted;
			else if (current == ',' && !quoted) { values.add(value.toString().trim()); value.setLength(0); }
			else value.append(current);
		}
		values.add(value.toString().trim());
		return values.toArray(String[]::new);
	}

	private String column(String[] row, int index) { return index < row.length ? row[index].trim() : ""; }

	private List<String> splitValues(String value) {
		if (value.isEmpty()) return List.of();
		return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
	}
}
