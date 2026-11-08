package data.model.tournament.io;

import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class BattleNext_TournamentStandardLoader extends BattleNext_CatalogLoader<BattleNext_TournamentModel> {

	private static final String RESOURCE_FOLDER = "tournament/_tournaments";
	private static final String STARTED_FILE = "started_tournament.json";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		List<String> result = new ArrayList<>();
		for (String path : paths(RESOURCE_FOLDER, false)) {
			if (!path.endsWith(".json")) continue;
			if (path.substring(path.lastIndexOf('/') + 1).equalsIgnoreCase(STARTED_FILE)) continue;
			result.add(path);
		}
		return List.copyOf(result);
	}

	@Override
	protected BattleNext_TournamentModel parse(JSONObject json) throws IOException, JSONException {
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.fromJson(json);
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
