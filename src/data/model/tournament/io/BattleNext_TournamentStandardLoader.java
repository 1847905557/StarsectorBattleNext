package data.model.tournament.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentStandardLoader extends BattleNext_CatalogLoader<BattleNext_TournamentModel> {

	private static final String RESOURCE_ROOT = "tournament";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_JsonCatalog.resourceFiles(RESOURCE_ROOT);
	}

	@Override
	protected BattleNext_TournamentModel parse(String path) throws IOException, JSONException {
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
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
}
