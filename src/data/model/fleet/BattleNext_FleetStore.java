package data.model.fleet;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.BattleNext_LoadSource;
import data.model.fleet.io.BattleNext_FleetLegacyLoader;
import data.model.fleet.io.BattleNext_FleetLegacyFolderLoader;
import data.model.fleet.io.BattleNext_FleetSaver;
import data.model.fleet.io.BattleNext_FleetStandardLoader;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetStore extends BattleNext_BaseStore<BattleNext_FleetModel> {

	private final BattleNext_FleetStandardLoader standardLoader = new BattleNext_FleetStandardLoader();
	private final BattleNext_FleetLegacyLoader legacyLoader = new BattleNext_FleetLegacyLoader();
	private final BattleNext_FleetLegacyFolderLoader folderLoader = new BattleNext_FleetLegacyFolderLoader();
	private final BattleNext_FleetSaver saver = new BattleNext_FleetSaver();

	public BattleNext_FleetStore() {
		super("Fleet catalog has not been loaded");
	}

	@Override
	protected BattleNext_FleetSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_FleetModel>> sources() {
		return List.of(legacyLoader, folderLoader, standardLoader);
	}

	@Override
	protected void mergeLoaded(Map<String, BattleNext_FleetModel> loaded, BattleNext_FleetModel model) throws IOException {
		BattleNext_FleetModel existing = loaded.get(model.getId());
		if (existing != null) {
			throw new IOException("Duplicate fleet ID " + model.getId() + ": "
					+ existing.getSourcePath() + " and " + model.getSourcePath());
		}
		super.mergeLoaded(loaded, model);
	}

	@Override
	public void save(BattleNext_FleetModel model) throws IOException, JSONException {

		requireLoaded();
		String target = "tournament/" + model.getFolderName();
		for (BattleNext_FleetModel fleet : getAll()) {
			if (fleet.getSourceType() != BattleNext_LoadSource.LEGACY) continue;
			String source = fleet.getSourcePath().replace('\\', '/');
			if (model.getId().equals(fleet.getId()) || target.equalsIgnoreCase(source.substring(0, source.lastIndexOf('/')))) {
				throw new IOException("Legacy fleet requires a new ID and target directory: " + source);
			}
		}
		super.save(model);
	}

	@Override
	protected boolean isManaged(BattleNext_FleetModel model) {
		return model != null && model.getSourceType() != BattleNext_LoadSource.LEGACY;
	}

	public boolean isManaged(String id) {
		return isManaged(get(id));
	}
}
