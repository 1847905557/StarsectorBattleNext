package data.model.tournament.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentCommonLoader extends BattleNext_CatalogLoader<BattleNext_TournamentModel> {

	private static final String COMMON_ROOT = "AI_Battles_Next/tournaments/";
	private static final String META_PATH = "AI_Battles_Next/meta.json";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_JsonCatalog.commonFiles(COMMON_ROOT);
	}

	@Override
	protected BattleNext_TournamentModel parse(String path) throws IOException, JSONException {
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.fromJson(Global.getSettings().readJSONFromCommon(path, false));
		return tournament;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.COMMON;
	}

	@Override
	protected String kind() {
		return "common tournament";
	}

	public String loadActiveId() throws IOException, JSONException {
		if (!Global.getSettings().fileExistsInCommon(META_PATH)) return "";
		JSONObject meta = Global.getSettings().readJSONFromCommon(META_PATH, false);
		int version = meta.optInt("formatVersion", BattleNext_TournamentModel.META_FORMAT_VERSION);
		if (version != BattleNext_TournamentModel.META_FORMAT_VERSION) return "";
		return meta.optString("activeTournamentId", "");
	}
}
