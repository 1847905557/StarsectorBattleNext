package data.model.tournament.io;

import data.model.BattleNext_BaseSaver;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_TournamentSaver extends BattleNext_BaseSaver<BattleNext_TournamentModel> {

	private static final String RESOURCE_ROOT = "tournament/_tournaments/";

	@Override
	public String save(BattleNext_TournamentModel tournament) throws IOException, JSONException {
		if (!BattleNext_EntryId.isValidPathSegment(tournament.getId()) || BattleNext_StartedTournamentStore.FILE_NAME_WITHOUT_FORMAT.equalsIgnoreCase(tournament.getId())) throw new IOException("Invalid tournament ID: " + tournament.getId());
		String path = RESOURCE_ROOT + tournament.getId() + ".json";
		writeResourceJson(path, tournament.toJson());
		return path;
	}

	@Override
	public void delete(BattleNext_TournamentModel tournament) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(tournament.getId()) || BattleNext_StartedTournamentStore.FILE_NAME_WITHOUT_FORMAT.equalsIgnoreCase(tournament.getId())) throw new IOException("Invalid tournament ID: " + tournament.getId());
		String path = RESOURCE_ROOT + tournament.getId() + ".json";
		BattleNext_ModFileManager.delete(path);
		if (BattleNext_ModFileManager.exists(path)) throw new IOException("Tournament file remains after deletion: " + tournament.getId());
	}
}
