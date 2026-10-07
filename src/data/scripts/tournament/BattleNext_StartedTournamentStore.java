package data.scripts.tournament;

import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BattleNext_StartedTournamentStore {

	public static final int FORMAT_VERSION = 1;
	public static final String FILE_NAME_WITHOUT_FORMAT = "started_tournament";
	public static final String FILE_NAME = FILE_NAME_WITHOUT_FORMAT + ".json";
	public static final String PATH = "tournament/_tournaments/" + FILE_NAME;

	public static String pendingRoundPath(String id) {
		return "tournament/_tournaments/_pending/" + id + ".json";
	}
	private final Map<String, BattleNext_StartedTournament> entries = new LinkedHashMap<>();
	private List<CatalogEntry> catalog = List.of();
	private final List<String> loadErrors = new ArrayList<>();
	private String activeId = "";
	private String loadError = "Started tournament catalog has not been loaded";
	private long revision;

	public record CatalogEntry(int index, String id, String sourceTournamentId, String raw,
			BattleNext_StartedTournament model, String error) { }

	public void reload() throws IOException, JSONException {
		loadErrors.clear();
		try {
			publish(read());
		} catch (IOException | JSONException | IllegalArgumentException exception) {
			loadErrors.add(PATH + ": " + exception);
			BattleNext_Logger.warn(getClass(), "tournament.instances.source.unavailable", BattleNext_Logger.fields().put("path", PATH).build(), exception);
			publish(new JSONObject().put("formatVersion", FORMAT_VERSION).put("activeTournamentId", "").put("instances", new JSONArray()));
		}
	}

	public void save(BattleNext_StartedTournament started) throws IOException, JSONException {
		if (started.getExecutionKind() != BattleNext_StartedTournament.ExecutionKind.MANUAL) throw new IllegalArgumentException("Persistent tournament instances must use manual execution: " + started.getId());
		if (!started.getLoadErrors().isEmpty()) throw new IllegalArgumentException("Started tournament is incomplete: " + started.getLoadErrors());
		commit(started, null);
	}

	public void activate(String id) throws IOException, JSONException {
		commit(null, id);
	}

	public void delete(String id) throws IOException, JSONException {
		JSONObject current = read();
		List<CatalogEntry> selected = parse(current).stream().filter(entry -> entry.id().equals(id)).toList();
		if (selected.size() != 1) throw new IllegalArgumentException("Started tournament ID is unavailable or ambiguous: " + id);
		delete(selected.get(0));
	}

	public void delete(CatalogEntry selected) throws IOException, JSONException {
		JSONObject current = read();
		JSONArray values = current.getJSONArray("instances");
		if (selected.index() >= values.length() || !values.get(selected.index()).toString().equals(selected.raw())) {
			throw new IllegalStateException("Started tournament entry changed: " + selected.index());
		}
		JSONArray retained = new JSONArray();
		boolean sharedIdentity = false;
		for (int index = 0; index < values.length(); index++) {
			if (index == selected.index()) continue;
			retained.put(values.get(index));
			JSONObject other = values.optJSONObject(index);
			if (other != null && other.optString("id", "").equals(selected.id())) {
				sharedIdentity = true;
			}
		}
		current.put("instances", retained);
		if (!sharedIdentity && current.getString("activeTournamentId").equals(selected.id())) {
			current.put("activeTournamentId", "");
		}
		write(current);
		if (!sharedIdentity && BattleNext_EntryId.isValidPathSegment(selected.id())) {
			BattleNext_ModFileManager.delete(pendingRoundPath(selected.id()));
		}
		BattleNext_Logger.info(getClass(), "tournament.instance.deleted", () -> BattleNext_Logger.fields().put("instance_id", selected.id()).put("index", selected.index()).build());
	}

	private void commit(BattleNext_StartedTournament updated, String selection) throws IOException, JSONException {
		JSONObject current = read();
		List<CatalogEntry> loaded = parse(current);
		String nextActive = selection == null ? current.getString("activeTournamentId") : selection;
		if (updated != null) {
			List<CatalogEntry> matching = loaded.stream().filter(entry -> entry.id().equals(updated.getId())).toList();
			if (matching.size() > 1) throw new IllegalArgumentException("Ambiguous started tournament ID: " + updated.getId());
			JSONArray values = current.getJSONArray("instances");
			if (matching.isEmpty()) {
				values.put(updated.toJson());
			} else {
				values.put(matching.get(0).index(), updated.toJson());
			}
		}
		if (selection != null && !selection.isEmpty()) {
			CatalogEntry selected = loaded.stream().filter(entry -> entry.id().equals(selection) && entry.error().isEmpty()).findFirst().orElse(null);
			if (selected == null || selected.model().getExecutionKind() != BattleNext_StartedTournament.ExecutionKind.MANUAL) {
				throw new IllegalArgumentException("Active tournament must reference a complete manual instance: " + selection);
			}
		}
		current.put("activeTournamentId", nextActive);
		write(current);
		BattleNext_Logger.info(getClass(), "tournament.instances.committed",
				() -> BattleNext_Logger.fields().put("instance_id", updated == null ? "" : updated.getId()).put("active_id", nextActive).build());
	}

	private void write(JSONObject next) throws IOException, JSONException {
		BattleNext_ModFileManager.writeTextAtomic(PATH, next.toString());
		publish(next);
	}

	private JSONObject read() throws IOException, JSONException {
		if (BattleNext_ModFileManager.exists(PATH)) {
			JSONObject json = new JSONObject(new String(BattleNext_ModFileManager.readBytes(PATH), StandardCharsets.UTF_8));
			if (json.getInt("formatVersion") != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported started tournament formatVersion");
			json.getJSONArray("instances");
			json.getString("activeTournamentId");
			return json;
		}
		return new JSONObject().put("formatVersion", FORMAT_VERSION).put("activeTournamentId", "").put("instances", new JSONArray());
	}

	private List<CatalogEntry> parse(JSONObject json) throws JSONException {
		List<CatalogEntry> result = new ArrayList<>();
		JSONArray values = json.getJSONArray("instances");
		Map<String, Integer> counts = new HashMap<>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject raw = values.optJSONObject(index);
			if (raw == null) continue;
			String id = raw.optString("id", "");
			if (!id.isEmpty()) {
				counts.merge(id, 1, Integer::sum);
			}
		}
		for (int index = 0; index < values.length(); index++) {
			JSONObject raw = values.optJSONObject(index);
			String id = raw == null ? "" : raw.optString("id", "");
			String sourceId = raw == null ? "" : raw.optString("sourceTournamentId", "");
			try {
				BattleNext_StartedTournament started = new BattleNext_StartedTournament();
				started.fromJson(values.getJSONObject(index));
				if (!started.getLoadErrors().isEmpty()) {
					result.add(new CatalogEntry(index, id, sourceId, values.get(index).toString(), started, String.join("; ", started.getLoadErrors())));
					continue;
				}
				if (counts.get(id) > 1) throw new IllegalArgumentException("Duplicate started tournament ID: " + id);
				result.add(new CatalogEntry(index, id, sourceId, values.get(index).toString(), started, ""));
			} catch (JSONException | IllegalArgumentException exception) {
				result.add(new CatalogEntry(index, id, sourceId, values.get(index).toString(), null, exception.toString()));
			}
		}
		return List.copyOf(result);
	}

	private void publish(JSONObject json) throws JSONException {
		catalog = parse(json);
		entries.clear();
		for (CatalogEntry entry : catalog) {
			if (!entry.error().isEmpty()) {
				BattleNext_Logger.warn(getClass(), "tournament.instance.rejected", BattleNext_Logger.fields().put("path", PATH)
						.put("position", entry.index()).put("id", entry.id()).put("raw", entry.raw()).put("diagnostic", entry.error()).build());
				continue;
			}
			entries.put(entry.id(), entry.model());
		}
		activeId = json.getString("activeTournamentId");
		BattleNext_StartedTournament selected = entries.get(activeId);
		if (selected == null || selected.getExecutionKind() != BattleNext_StartedTournament.ExecutionKind.MANUAL) {
			activeId = "";
		}
		loadError = "";
		revision++;
	}

	public boolean isLoaded() {
		return loadError.isEmpty();
	}

	public String getLoadError() {
		return loadError;
	}

	public long getRevision() {
		return revision;
	}

	public String getActiveId() {
		return activeId;
	}

	public BattleNext_StartedTournament get(String id) {
		BattleNext_StartedTournament started = entries.get(id);
		return started == null ? null : started.copy();
	}

	public List<BattleNext_StartedTournament> getAll() {
		return entries.values().stream().map(BattleNext_StartedTournament::copy).toList();
	}

	public List<BattleNext_StartedTournament> getForSource(String sourceId, BattleNext_StartedTournament.ExecutionKind kind) {
		return entries.values().stream().filter(started -> started.getSourceTournamentId().equals(sourceId) && started.getExecutionKind() == kind)
				.sorted(Comparator.comparingLong(BattleNext_StartedTournament::getStartedAt).reversed())
				.map(BattleNext_StartedTournament::copy).toList();
	}

	public boolean hasInstances(String sourceId) {
		return !sourceId.isEmpty() && catalog.stream().anyMatch(entry -> entry.sourceTournamentId().equals(sourceId));
	}

	public List<CatalogEntry> getCatalog() {
		return catalog;
	}

	public List<String> getLoadErrors() {
		List<String> errors = new ArrayList<>(loadErrors);
		for (CatalogEntry entry : catalog) {
			if (entry.error().isEmpty()) continue;
			errors.add(PATH + " / " + entry.index() + ": " + entry.error());
		}
		return List.copyOf(errors);
	}
}
