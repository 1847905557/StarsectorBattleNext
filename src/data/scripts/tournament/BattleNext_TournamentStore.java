package data.scripts.tournament;

import data.scripts.tournament.io.BattleNext_TournamentLoader;
import data.scripts.tournament.io.BattleNext_TournamentSaver;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_TournamentStore {

	private final BattleNext_TournamentLoader loader = new BattleNext_TournamentLoader();
	private final BattleNext_TournamentSaver saver = new BattleNext_TournamentSaver();
	private final Map<Integer, BattleNext_Tournament> tournaments = new LinkedHashMap<>();
	private int activeSlot = -1;

	public void reload() throws IOException, JSONException {
		tournaments.clear();
		tournaments.putAll(loader.loadBuiltIn());
		tournaments.putAll(loader.loadUser());
		activeSlot = loader.loadActiveSlot();
	}

	public void save(BattleNext_Tournament tournament) throws IOException, JSONException {
		if (tournament == null) {
			throw new IllegalArgumentException("Tournament is required");
		}
		saver.save(tournament);
		tournaments.put(tournament.getSlot(), tournament);
	}

	public void setActiveSlot(int slot) throws IOException, JSONException {
		if (slot < -1 || slot > 31) {
			throw new IllegalArgumentException("Active tournament slot must be between -1 and 31");
		}
		saver.saveActiveSlot(slot);
		activeSlot = slot;
	}

	public BattleNext_Tournament get(int slot) {
		return tournaments.get(slot);
	}

	public List<BattleNext_Tournament> getAll() {
		return List.copyOf(tournaments.values());
	}

	public int getActiveSlot() {
		return activeSlot;
	}
}
