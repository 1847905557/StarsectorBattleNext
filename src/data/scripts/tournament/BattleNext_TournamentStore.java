package data.scripts.tournament;

import data.scripts.tournament.io.BattleNext_TournamentCommonLoader;
import data.scripts.tournament.io.BattleNext_TournamentSaver;
import data.scripts.tournament.io.BattleNext_TournamentStandardLoader;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class BattleNext_TournamentStore {

	private final BattleNext_TournamentStandardLoader standardLoader = new BattleNext_TournamentStandardLoader();
	private final BattleNext_TournamentCommonLoader commonLoader = new BattleNext_TournamentCommonLoader();
	private final BattleNext_TournamentSaver saver = new BattleNext_TournamentSaver();
	private final Map<String, BattleNext_TournamentModel> tournaments = new LinkedHashMap<>();
	private final Set<String> managedIds = new HashSet<>();
	private String activeId = "";
	private String loadError = "Tournament catalog has not been loaded";

	public void reload() throws IOException, JSONException {
		try {
			Map<String, BattleNext_TournamentModel> loaded = new LinkedHashMap<>(standardLoader.load());
			Map<String, BattleNext_TournamentModel> users = commonLoader.load();
			loaded.putAll(users);
			String loadedActiveId = commonLoader.loadActiveId();
			tournaments.clear();
			tournaments.putAll(loaded);
			managedIds.clear();
			managedIds.addAll(users.keySet());
			activeId = loadedActiveId;
			loadError = "";
		} catch (IOException | JSONException | RuntimeException exception) {
			loadError = exception.getMessage();
			throw exception;
		}
	}

	public String getLoadError() {
		return loadError;
	}
	public boolean isLoaded() {
		return loadError.isEmpty();
	}
	private void requireLoaded() {
		if (!isLoaded()) throw new IllegalStateException(loadError);
	}

	public void save(BattleNext_TournamentModel tournament) throws IOException, JSONException {
		requireLoaded();
		if (tournament == null || !BattleNext_EntryId.isValid(tournament.getId())) {
			throw new IllegalArgumentException("Tournament is required");
		}
		saver.save(tournament);
		tournament.setSourceType(BattleNext_LoadSource.COMMON);
		tournament.setSourcePath("AI_Battles_Next/tournaments/" + tournament.getId() + ".json");
		for (BattleNext_Match match : tournament.getMatches()) {
			match.setSourceType(BattleNext_LoadSource.COMMON);
			match.setSourcePath(tournament.getSourcePath());
		}
		tournaments.put(tournament.getId(), tournament);
		managedIds.add(tournament.getId());
	}

	public void setActiveId(String id) throws IOException, JSONException {
		requireLoaded();
		if (!id.isEmpty() && !tournaments.containsKey(id)) throw new IllegalArgumentException("Unknown tournament: " + id);
		saver.saveActiveId(id);
		activeId = id;
	}

	public BattleNext_TournamentModel get(String id) {
		return tournaments.get(id);
	}

	public List<BattleNext_TournamentModel> getAll() {
		return List.copyOf(tournaments.values());
	}

	public String getActiveId() {
		return activeId;
	}

	public boolean isManaged(String id) {
		return managedIds.contains(id);
	}

	public void delete(String id) throws IOException, JSONException {
		requireLoaded();
		if (!managedIds.contains(id)) throw new IllegalStateException("Tournament source is unavailable");
		saver.delete(id);
		managedIds.remove(id);
		tournaments.remove(id);
		if (id.equals(activeId)) setActiveId("");
	}
}
