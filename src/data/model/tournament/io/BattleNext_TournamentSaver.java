package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseSaver;
import data.scripts.tournament.BattleNext_TournamentModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class BattleNext_TournamentSaver extends BattleNext_BaseSaver<BattleNext_TournamentModel> {

	private static final String COMMON_ROOT = "AI_Battles_Next/tournaments/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";

	@Override
	public String save(BattleNext_TournamentModel tournament) throws IOException, JSONException {
		String path = COMMON_ROOT + tournament.getId() + ".json";
		writeCommonJson(path, tournament.toJson());
		return path;
	}

	public void saveActiveId(String id) throws IOException, JSONException {
		JSONObject meta = new JSONObject();
		meta.put("formatVersion", BattleNext_TournamentModel.META_FORMAT_VERSION);
		meta.put("activeTournamentId", id);
		Global.getSettings().writeJSONToCommon(META_PATH, meta, false);
	}

	@Override
	public void delete(BattleNext_TournamentModel tournament) throws IOException {
		String path = COMMON_ROOT + tournament.getId() + ".json";
		Global.getSettings().deleteTextFileFromCommon(path);
		if (Global.getSettings().fileExistsInCommon(path)) {
			throw new IOException("Tournament file remains after deletion: " + tournament.getId());
		}
	}
}
