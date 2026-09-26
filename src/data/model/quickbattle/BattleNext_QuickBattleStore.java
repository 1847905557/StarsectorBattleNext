package data.model.quickbattle;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.quickbattle.io.BattleNext_QuickBattleLegacyLoader;
import data.model.quickbattle.io.BattleNext_QuickBattleSaver;
import data.model.quickbattle.io.BattleNext_QuickBattleStandardLoader;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_QuickBattleStore extends BattleNext_BaseStore<BattleNext_QuickBattleModel> {

	private final BattleNext_QuickBattleStandardLoader standardLoader = new BattleNext_QuickBattleStandardLoader();
	private final BattleNext_QuickBattleLegacyLoader legacyLoader = new BattleNext_QuickBattleLegacyLoader();
	private final BattleNext_QuickBattleSaver saver = new BattleNext_QuickBattleSaver(this::getAll);

	public BattleNext_QuickBattleStore() {
		super("Quick battle catalog has not been loaded");
	}

	@Override
	public void save(BattleNext_QuickBattleModel model) throws IOException, JSONException {
		data.model.BattleNext_LoadSource source = model.getSourceType();
		String path = model.getSourcePath();
		super.save(model);
		if (source != data.model.BattleNext_LoadSource.CREATED) {
			model.setSourceType(source);
			model.setSourcePath(path);
		}
	}

	@Override
	protected BattleNext_QuickBattleSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_QuickBattleModel>> sources() {
		return List.of(standardLoader, legacyLoader);
	}
}
