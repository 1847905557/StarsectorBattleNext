package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_JsonCatalog;
import data.scripts.tools.BattleNext_LoadSource;
import data.scripts.tournament.BattleNext_Tournament;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TournamentCommonLoader {

	private static final String COMMON_ROOT = "AI_Battles_Next/tournament/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";

	public Map<String, BattleNext_Tournament> load() throws IOException, JSONException {
		Map<String, BattleNext_Tournament> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.commonFiles("AI_Battles_Next/tournament")) {
			try {
				BattleNext_Tournament value = BattleNext_Tournament.fromJson(Global.getSettings().readJSONFromCommon(path, false));
				if (value.getId().isEmpty()) continue;
				value.setSourceType(BattleNext_LoadSource.COMMON);
				value.setSourcePath(path);
				propagateToMatches(value);
				result.put(value.getId(), value);
			} catch (IllegalArgumentException exception) {
				throw new IllegalStateException("Unable to load common tournament: " + path, exception);
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

	private void propagateToMatches(BattleNext_Tournament tournament) {
		for (BattleNext_Match match : tournament.getMatches()) {
			match.setSourceType(tournament.getSourceType());
			match.setSourcePath(tournament.getSourcePath());
		}
	}
}
