package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.scripts.tournament.BattleNext_Tournament;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TournamentLoader {

	private static final int SLOT_COUNT = 32;
	private static final String COMMON_ROOT = "AI_Battles_Next/tournament/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";
	private static final String MOD_ID = "AI_Battles_Next";

	public Map<Integer, BattleNext_Tournament> loadBuiltIn() {
		Map<Integer, BattleNext_Tournament> result = new LinkedHashMap<>();
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			try {
				result.put(slot, BattleNext_Tournament.fromJson(Global.getSettings().loadJSON("tournament/tournament_" + slot + ".json", MOD_ID)));
			} catch (Exception ignored) {
			}
		}
		return result;
	}

	public Map<Integer, BattleNext_Tournament> loadUser() throws IOException, JSONException {
		Map<Integer, BattleNext_Tournament> result = new LinkedHashMap<>();
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			String userPath = COMMON_ROOT + "tournament_" + slot + ".json";
			if (Global.getSettings().fileExistsInCommon(userPath)) {
				result.put(slot, BattleNext_Tournament.fromJson(Global.getSettings().readJSONFromCommon(userPath, false)));
			}
		}
		return result;
	}

	public int loadActiveSlot() throws IOException, JSONException {
		if (!Global.getSettings().fileExistsInCommon(META_PATH)) return -1;
		return Global.getSettings().readJSONFromCommon(META_PATH, false).optInt("activeTournamentSlot", -1);
	}
}
