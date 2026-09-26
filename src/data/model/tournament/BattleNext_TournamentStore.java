package data.model.tournament;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.BattleNext_LoadSource;
import data.model.tournament.io.BattleNext_TournamentCommonLoader;
import data.model.tournament.io.BattleNext_TournamentSaver;
import data.model.tournament.io.BattleNext_TournamentStandardLoader;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentStore extends BattleNext_BaseStore<BattleNext_TournamentModel> {

	private final BattleNext_TournamentStandardLoader standardLoader = new BattleNext_TournamentStandardLoader();
	private final BattleNext_TournamentCommonLoader commonLoader = new BattleNext_TournamentCommonLoader();
	private final BattleNext_TournamentSaver saver = new BattleNext_TournamentSaver();
	private String activeId = "";

	public BattleNext_TournamentStore() {
		super("Tournament catalog has not been loaded");
	}

	@Override
	protected BattleNext_TournamentSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_TournamentModel>> sources() {
		return List.of(standardLoader, commonLoader);
	}

	@Override
	protected void loadState() throws IOException, JSONException {
		activeId = commonLoader.loadActiveId();
	}

	@Override
	protected void onEntryRemoved(String id) throws IOException, JSONException {
		if (id.equals(activeId)) setActiveId("");
	}

	public void setActiveId(String id) throws IOException, JSONException {
		requireLoaded();
		if (!id.isEmpty() && get(id) == null) throw new IllegalArgumentException("Unknown tournament: " + id);
		saver.saveActiveId(id);
		activeId = id;
	}

	public String getActiveId() {
		return activeId;
	}

	public boolean isManaged(String id) {
		BattleNext_TournamentModel tournament = get(id);
		return tournament != null && tournament.getSourceType() == BattleNext_LoadSource.COMMON;
	}
}
