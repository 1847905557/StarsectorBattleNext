package data.model.fleet;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.BattleNext_LoadSource;
import data.model.fleet.io.BattleNext_FleetLegacyLoader;
import data.model.fleet.io.BattleNext_FleetSaver;
import data.model.fleet.io.BattleNext_FleetStandardLoader;

import java.util.List;

public class BattleNext_FleetStore extends BattleNext_BaseStore<BattleNext_FleetModel> {

	private final BattleNext_FleetStandardLoader standardLoader = new BattleNext_FleetStandardLoader();
	private final BattleNext_FleetLegacyLoader legacyLoader = new BattleNext_FleetLegacyLoader();
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
		return List.of(legacyLoader, standardLoader);
	}

	@Override
	protected boolean isManaged(BattleNext_FleetModel model) {
		return model != null && model.getSourceType() != BattleNext_LoadSource.LEGACY;
	}

	public boolean isManaged(String id) {
		return isManaged(get(id));
	}
}
