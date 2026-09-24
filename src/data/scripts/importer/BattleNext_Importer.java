package data.scripts.importer;

import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_LegacyLoader;
import data.scripts.fleet.BattleNext_VariantModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_Importer {

	private final BattleNext_LegacyLoader legacyLoader = new BattleNext_LegacyLoader();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();

	public BattleNext_Importer() {
		fleetStore.reload();
	}

	public ImportResult importNumber(Path directory, String number) {
		ImportResult result = new ImportResult();
		Path dataFile = directory.resolve("player" + number + "_data.csv");
		Path fleetFile = directory.resolve("player" + number + "_fleet.csv");
		if (!Files.isRegularFile(dataFile) || !Files.isRegularFile(fleetFile)) {
			result.warnings.add("Missing pair for player" + number);
			return result;
		}
		try {
			BattleNext_FleetModel fleet = legacyLoader.loadFleet(number, dataFile, fleetFile);
			persist(fleet, result);
		} catch (Exception exception) {
			result.warnings.add("Unable to import player" + number + ": " + exception.getMessage());
		}
		return result;
	}

	public ImportResult importNumbers(Path directory, List<String> numbers) {
		ImportResult result = new ImportResult();
		for (String number : numbers == null ? List.<String>of() : numbers) {
			result.merge(importNumber(directory, number));
		}
		return result;
	}

	public ImportResult importDirectory(Path directory) {
		ImportResult result = new ImportResult();
		try {
			List<String> numbers = Files.list(directory)
					.map(path -> path.getFileName().toString())
					.filter(name -> name.matches("player\\d+_data\\.csv"))
					.map(name -> name.substring("player".length(), name.length() - "_data.csv".length()))
					.sorted(Comparator.comparingInt(Integer::parseInt))
					.toList();
			return importNumbers(directory, numbers);
		} catch (IOException exception) {
			result.warnings.add("Unable to enumerate import directory: " + exception.getMessage());
			return result;
		}
	}

	public List<MatchPreview> previewMatches(Path file, Map<String, String> idMapping) {
		List<MatchPreview> result = new ArrayList<>();
		try {
			int lineNumber = 0;
			for (String line : Files.readAllLines(file)) {
				lineNumber++;
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("player,enemy,map,size")) continue;
				String[] fields = trimmed.split(",", -1);
				if (fields.length < 4) {
					result.add(new MatchPreview(lineNumber, "", "", false, "Expected player,enemy,map,size"));
					continue;
				}
				String player = resolveId(fields[0].trim(), idMapping);
				String enemy = resolveId(fields[1].trim(), idMapping);
				try {
					int map = Integer.parseInt(fields[2].trim());
					float size = Float.parseFloat(fields[3].trim());
					boolean valid = map >= -1 && map <= 11 && Float.isFinite(size) && size > 0f;
					result.add(new MatchPreview(lineNumber, player, enemy, valid, valid ? "" : "Invalid map or size"));
				} catch (NumberFormatException exception) {
					result.add(new MatchPreview(lineNumber, player, enemy, false, "Invalid numeric field"));
				}
			}
		} catch (IOException exception) {
			result.add(new MatchPreview(-1, "", "", false, exception.getMessage()));
		}
		return result;
	}

	public ImportResult importMatchesAsTournament(Path file, int requestedSlot, String name,
			Map<String, String> idMapping) {
		ImportResult result = new ImportResult();
		try {
			tournamentStore.reload();
			int slot = resolveTournamentSlot(requestedSlot, result);
			BattleNext_Tournament tournament = new BattleNext_Tournament(slot,
					name == null ? "Imported tournament" : name, "round_robin", "BO1", 2);
			List<String> teams = new ArrayList<>();
			for (MatchPreview preview : previewMatches(file, idMapping)) {
				if (!preview.isValid()) {
					if (!preview.getWarning().isEmpty()) {
						result.warnings.add("matches.csv line " + preview.getLineNumber() + ": " + preview.getWarning());
					}
					continue;
				}
				if (fleetStore.get(preview.getPlayer()) == null || fleetStore.get(preview.getEnemy()) == null) {
					result.warnings.add("matches.csv line " + preview.getLineNumber() + ": missing fleet reference");
					continue;
				}
				addTeam(teams, preview.getPlayer());
				addTeam(teams, preview.getEnemy());
			}
			if (teams.size() < 2) {
				result.warnings.add("No valid tournament matches found");
				return result;
			}
			for (String team : teams) {
				tournament.addTeam(team);
			}
			tournamentStore.save(tournament);
			result.tournamentSlot = slot;
		} catch (Exception exception) {
			result.warnings.add("Unable to import tournament matches: " + exception.getMessage());
		}
		return result;
	}

	private int resolveTournamentSlot(int requestedSlot, ImportResult result) {
		int first = Math.max(0, Math.min(31, requestedSlot));
		if (tournamentStore.get(first) == null) {
			return first;
		}
		for (int slot = 0; slot < 32; slot++) {
			if (tournamentStore.get(slot) == null) {
				result.warnings.add("Tournament slot " + first + " is occupied; using slot " + slot);
				return slot;
			}
		}
		throw new IllegalStateException("No tournament slot is available");
	}

	private void addTeam(List<String> teams, String team) {
		if (!teams.contains(team) && teams.size() < 32) {
			teams.add(team);
		}
	}

	private void persist(BattleNext_FleetModel fleet, ImportResult result) throws IOException, JSONException {
		String originalId = fleet.getId();
		String resolvedId = resolveConflict(originalId);
		fleet.setId(resolvedId);
		for (BattleNext_MemberModel member : fleet.getMembers()) {
			try {
				fleet.putVariant(BattleNext_VariantModel.fromRegistered(member.getVariantId()));
			} catch (Exception exception) {
				fleet.addLoadError("Missing variant " + member.getVariantId());
				result.warnings.add(resolvedId + ": missing variant " + member.getVariantId());
			}
		}
		fleetStore.save(fleet);
		result.idMapping.put(originalId, resolvedId);
		result.importedIds.add(resolvedId);
	}

	private String resolveConflict(String originalId) {
		String candidate = originalId;
		int suffix = 1;
		while (fleetStore.get(candidate) != null) {
			candidate = originalId + "_" + suffix++;
		}
		return candidate;
	}

	private String resolveId(String value, Map<String, String> idMapping) {
		if (idMapping == null) return value;
		return idMapping.getOrDefault(value, value);
	}

	public static final class ImportResult {

		private final Map<String, String> idMapping = new LinkedHashMap<>();
		private final List<String> importedIds = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private int tournamentSlot = -1;

		public Map<String, String> getIdMapping() {
			return Map.copyOf(idMapping);
		}

		public List<String> getImportedIds() {
			return List.copyOf(importedIds);
		}

		public List<String> getWarnings() {
			return List.copyOf(warnings);
		}

		public int getTournamentSlot() {
			return tournamentSlot;
		}

		private void merge(ImportResult other) {
			idMapping.putAll(other.idMapping);
			importedIds.addAll(other.importedIds);
			warnings.addAll(other.warnings);
		}
	}

	public static final class MatchPreview {

		private final int lineNumber;
		private final String player;
		private final String enemy;
		private final boolean valid;
		private final String warning;

		private MatchPreview(int lineNumber, String player, String enemy, boolean valid, String warning) {
			this.lineNumber = lineNumber;
			this.player = player;
			this.enemy = enemy;
			this.valid = valid;
			this.warning = warning;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		public String getPlayer() {
			return player;
		}

		public String getEnemy() {
			return enemy;
		}

		public boolean isValid() {
			return valid;
		}

		public String getWarning() {
			return warning;
		}
	}
}
