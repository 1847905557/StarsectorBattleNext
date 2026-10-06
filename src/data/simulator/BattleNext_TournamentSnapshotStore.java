package data.simulator;

import data.model.BattleNext_BaseStore;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseSaver;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import data.scripts.tools.BattleNext_Logger;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentSnapshotStore extends BattleNext_BaseStore<BattleNext_TournamentSnapshot> {

	private static final String DIRECTORY = "tournament/_simulations";
	private final BattleNext_CatalogLoader<BattleNext_TournamentSnapshot> loader = new BattleNext_CatalogLoader<>() {
		@Override
		protected List<String> catalogPaths() throws IOException {
			return BattleNext_ModFileManager.listJson(DIRECTORY);
		}

		@Override
		protected BattleNext_TournamentSnapshot parse(String path) throws IOException, JSONException {
			BattleNext_TournamentSnapshot snapshot = new BattleNext_TournamentSnapshot();
			snapshot.fromJson(BattleNext_ModFileManager.readJson(BattleNext_TournamentSnapshotStore.class, path, "AI_Battles_Next"));
			return snapshot;
		}

		@Override
		protected BattleNext_LoadSource source() {
			return BattleNext_LoadSource.STANDARD;
		}

		@Override
		protected String kind() {
			return "tournament simulation snapshot";
		}
	};
	private final BattleNext_BaseSaver<BattleNext_TournamentSnapshot> writer = new BattleNext_BaseSaver<>() {
		@Override
		public String save(BattleNext_TournamentSnapshot snapshot) throws IOException, JSONException {
			String path = path(snapshot);
			writeResourceJson(path, snapshot.toJson());
			return path;
		}

		@Override
		public void delete(BattleNext_TournamentSnapshot snapshot) throws IOException {
			BattleNext_ModFileManager.delete(path(snapshot));
		}
	};

	public BattleNext_TournamentSnapshotStore() {
		super("Tournament simulation catalog has not been loaded");
	}

	public void restoreCheckpoints(List<BattleNext_TournamentSnapshot> snapshots) throws IOException, JSONException {
		for (BattleNext_TournamentSnapshot snapshot : snapshots) {
			writer.save(snapshot);
			BattleNext_Logger.info(getClass(), "simulator.tournament.checkpoint.restored",
					BattleNext_Logger.fields().put("id", snapshot.getId()).put("version", snapshot.getVersion()).build());
		}
		reload();
	}

	private static String path(BattleNext_TournamentSnapshot snapshot) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(snapshot.getId())) throw new IOException("Invalid tournament simulation ID: " + snapshot.getId());
		return DIRECTORY + "/" + snapshot.getId() + ".json";
	}

	@Override
	protected BattleNext_BaseSaver<BattleNext_TournamentSnapshot> saver() {
		return writer;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_TournamentSnapshot>> sources() {
		return List.of(loader);
	}

	@Override
	protected boolean isManaged(BattleNext_TournamentSnapshot snapshot) {
		return snapshot != null;
	}
}
