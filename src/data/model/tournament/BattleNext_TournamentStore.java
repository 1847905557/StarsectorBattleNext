package data.model.tournament;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.BattleNext_LoadSource;
import data.model.tournament.io.BattleNext_TournamentSaver;
import data.model.tournament.io.BattleNext_TournamentStandardLoader;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentStore extends BattleNext_BaseStore<BattleNext_TournamentModel> {

	private final BattleNext_TournamentStandardLoader standardLoader = new BattleNext_TournamentStandardLoader();
	private final BattleNext_TournamentSaver saver = new BattleNext_TournamentSaver();

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
	public void save(BattleNext_TournamentModel configuration) throws IOException, JSONException {
		requireEditable(configuration.getId());
		super.save(configuration);
	}

	@Override
	public void saveEdited(BattleNext_TournamentModel configuration, String sourceId) throws IOException, JSONException {
		requireEditable(sourceId);
		super.saveEdited(configuration, sourceId);
	}

	@Override
	public void delete(String id) throws IOException, JSONException {
		requireEditable(id);
		super.delete(id);
	}

	public boolean isLocked(String id) throws IOException, JSONException {
		BattleNext_StartedTournamentStore started = new BattleNext_StartedTournamentStore();
		started.reload();
		return started.hasInstances(id);
	}

	private void requireEditable(String id) throws IOException, JSONException {
		if (BattleNext_StartedTournamentStore.FILE_NAME_WITHOUT_FORMAT.equalsIgnoreCase(id)) throw new IllegalArgumentException("Reserved tournament ID: " + id);
		if (isLocked(id)) throw new IllegalStateException("Tournament configuration has started instances: " + id);
	}

	public boolean isManaged(String id) {
		return isManaged(get(id));
	}

	@Override
	protected boolean isManaged(BattleNext_TournamentModel tournament) {
		return tournament != null && tournament.getSourceType() != BattleNext_LoadSource.CREATED;
	}
}
