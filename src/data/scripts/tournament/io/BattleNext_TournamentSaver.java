package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.scripts.tournament.BattleNext_Tournament;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class BattleNext_TournamentSaver {

	private static final String COMMON_ROOT = "AI_Battles_Next/tournament/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";

	public void save(BattleNext_Tournament tournament) throws IOException, JSONException {
		Global.getSettings().writeJSONToCommon(COMMON_ROOT + "tournament_" + tournament.getSlot() + ".json", tournament.toJson(), false);
	}

	public void saveActiveSlot(int slot) throws IOException, JSONException {
		JSONObject meta = new JSONObject();
		meta.put("formatVersion", BattleNext_Tournament.META_FORMAT_VERSION);
		meta.put("activeTournamentSlot", slot);
		Global.getSettings().writeJSONToCommon(META_PATH, meta, false);
	}
}
