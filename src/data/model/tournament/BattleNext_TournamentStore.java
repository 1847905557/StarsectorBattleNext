package data.model.tournament;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.BattleNext_LoadSource;
import data.model.tournament.io.BattleNext_TournamentSaver;
import data.model.tournament.io.BattleNext_TournamentStandardLoader;
import data.scripts.tools.BattleNext_Logger;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentStore extends BattleNext_BaseStore<BattleNext_TournamentModel> {

	private final BattleNext_TournamentStandardLoader standardLoader = new BattleNext_TournamentStandardLoader();
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
		return List.of(standardLoader);
	}

	@Override
	protected void loadState() throws IOException, JSONException {
		activeId = standardLoader.loadActiveId();
	}

	@Override
	protected void onEntryRemoved(String id) throws IOException, JSONException {
		if (id.equals(activeId)) setActiveId("");
	}

	public void setActiveId(String id) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("previous_id", activeId).put("requested_id", id).build();
		BattleNext_Logger.operationStarted(BattleNext_TournamentStore.class, "tournament.activation.commit", input);
		try {
			requireLoaded();
			if (!id.isEmpty() && get(id) == null) throw new IllegalArgumentException("Unknown tournament: " + id);
			saver.saveActiveId(id);
			activeId = id;
			BattleNext_Logger.operationSucceeded(BattleNext_TournamentStore.class, "tournament.activation.commit",
					BattleNext_Logger.fields().put("active_id", activeId).put("tournament", get(id)).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_TournamentStore.class, "tournament.activation.commit.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_TournamentStore.class, "tournament.activation.commit", input, exception);
			}
			throw exception;
		}
	}

	public String getActiveId() {
		return activeId;
	}

	public boolean isManaged(String id) {
		return isManaged(get(id));
	}

	@Override
	protected boolean isManaged(BattleNext_TournamentModel tournament) {
		return tournament != null && tournament.getSourceType() != BattleNext_LoadSource.CREATED;
	}
}
