package data.scripts.tournament;

import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_TournamentStore {
	private static final String COMMON_ROOT = "AI_Battles_Next/tournament/";
	private final Map<Integer, BattleNext_Tournament> tournaments = new LinkedHashMap<>();
	private int activeSlot = -1;

	public void reload() throws IOException, JSONException {
		tournaments.clear();
		for (int slot = 0; slot < 32; slot++) {
			String path = "tournament/tournament_" + slot + ".json";
			try { tournaments.put(slot, BattleNext_Tournament.fromJson(Global.getSettings().loadJSON(path, "AI_Battles_Next"))); } catch (Exception ignored) { }
			String userPath = COMMON_ROOT + "tournament_" + slot + ".json";
			if (Global.getSettings().fileExistsInCommon(userPath)) tournaments.put(slot, BattleNext_Tournament.fromJson(Global.getSettings().readJSONFromCommon(userPath, false)));
		}
		JSONObject meta = Global.getSettings().readJSONFromCommon("AI_Battles_Next/meta.json", false);
		activeSlot = meta.optInt("activeTournamentSlot", -1);
	}

	public void save(BattleNext_Tournament tournament) throws IOException, JSONException {
		if (tournament == null) throw new IllegalArgumentException("Tournament is required");
		Global.getSettings().writeJSONToCommon(COMMON_ROOT + "tournament_" + tournament.getSlot() + ".json", tournament.toJson(), false);
		tournaments.put(tournament.getSlot(), tournament);
	}

	public void setActiveSlot(int slot) throws IOException, JSONException {
		if (slot < -1 || slot > 31) throw new IllegalArgumentException("Active tournament slot must be between -1 and 31");
		activeSlot = slot;
		JSONObject meta = new JSONObject();
		meta.put("formatVersion", 1);
		meta.put("activeTournamentSlot", activeSlot);
		Global.getSettings().writeJSONToCommon("AI_Battles_Next/meta.json", meta, false);
	}

	public BattleNext_Tournament get(int slot) { return tournaments.get(slot); }
	public List<BattleNext_Tournament> getAll() { return List.copyOf(tournaments.values()); }
	public int getActiveSlot() { return activeSlot; }
}
