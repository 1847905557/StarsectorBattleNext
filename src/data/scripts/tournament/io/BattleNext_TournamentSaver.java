package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.scripts.tournament.BattleNext_TournamentModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class BattleNext_TournamentSaver {

	private static final String COMMON_ROOT = "AI_Battles_Next/tournaments/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";

	public void save(BattleNext_TournamentModel tournament) throws IOException, JSONException {
		Global.getSettings().writeJSONToCommon(COMMON_ROOT + tournament.getId() + ".json", tournament.toJson(), false);
	}

	public void saveActiveId(String id) throws IOException, JSONException {
		JSONObject meta = new JSONObject();
		meta.put("formatVersion", BattleNext_TournamentModel.META_FORMAT_VERSION);
		meta.put("activeTournamentId", id);
		Global.getSettings().writeJSONToCommon(META_PATH, meta, false);
	}

	public void delete(String id) throws IOException {
		Global.getSettings().deleteTextFileFromCommon(COMMON_ROOT + id + ".json");
		if (Global.getSettings().fileExistsInCommon(COMMON_ROOT + id + ".json")) {
			throw new IOException("Tournament file remains after deletion: " + id);
		}
	}
}
