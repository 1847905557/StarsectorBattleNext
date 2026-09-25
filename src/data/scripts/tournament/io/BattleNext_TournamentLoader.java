package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.scripts.battlesnext.util.BattleNext_JsonCatalog;
import data.scripts.tournament.BattleNext_Tournament;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TournamentLoader {

	private static final String COMMON_ROOT = "AI_Battles_Next/tournament/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";
	private static final String MOD_ID = "AI_Battles_Next";

	public Map<String, BattleNext_Tournament> loadBuiltIn() {
		Map<String, BattleNext_Tournament> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.resourceFiles("tournament")) {
			try {
				BattleNext_Tournament value = BattleNext_Tournament.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
				if (!value.getId().isEmpty()) result.put(value.getId(), value);
			} catch (Exception ignored) {
			}
		}
		return result;
	}

	public Map<String, BattleNext_Tournament> loadUser() throws IOException, JSONException {
		Map<String, BattleNext_Tournament> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.commonFiles("AI_Battles_Next/tournament")) {
			try {
				BattleNext_Tournament value = BattleNext_Tournament.fromJson(Global.getSettings().readJSONFromCommon(path, false));
				if (!value.getId().isEmpty()) result.put(value.getId(), value);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return result;
	}

	public String loadActiveId() throws IOException, JSONException {
		if (!Global.getSettings().fileExistsInCommon(META_PATH)) return "";
		JSONObject meta = Global.getSettings().readJSONFromCommon(META_PATH, false);
		int version = meta.optInt("formatVersion", BattleNext_Tournament.META_FORMAT_VERSION);
		if (version != BattleNext_Tournament.META_FORMAT_VERSION) return "";
		return meta.optString("activeTournamentId", "");
	}
}
