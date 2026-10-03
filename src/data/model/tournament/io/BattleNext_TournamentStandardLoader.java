package data.model.tournament.io;

import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentStandardLoader extends BattleNext_CatalogLoader<BattleNext_TournamentModel> {

	private static final String RESOURCE_FOLDER = "tournament/_tournaments";
	private static final String META_FILE = "meta.json";
	private static final String META_PATH = RESOURCE_FOLDER + "/" + META_FILE;

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_ModFileManager.listJson(RESOURCE_FOLDER).stream()
				.filter(path -> !path.endsWith("/meta.json") && !path.equals(META_FILE))
				.toList();
	}

	@Override
	protected BattleNext_TournamentModel parse(String path) throws IOException, JSONException {
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.fromJson(BattleNext_ModFileManager.readJson(BattleNext_TournamentStandardLoader.class, path, MOD_ID));
		return tournament;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}

	@Override
	protected String kind() {
		return "tournament";
	}

	public String loadActiveId() throws IOException, JSONException {
		if (!BattleNext_ModFileManager.exists(META_PATH)) return "";
		JSONObject meta = BattleNext_ModFileManager.readJson(BattleNext_TournamentStandardLoader.class, META_PATH, MOD_ID);
		int version = meta.optInt("formatVersion", BattleNext_TournamentModel.META_FORMAT_VERSION);
		if (version != BattleNext_TournamentModel.META_FORMAT_VERSION) return "";
		return meta.optString("activeTournamentId", "");
	}
}
