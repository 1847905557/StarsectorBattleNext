package data.model.quickbattle;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.quickbattle.io.BattleNext_QuickBattleCommonLoader;
import data.model.quickbattle.io.BattleNext_QuickBattleLegacyLoader;
import data.model.quickbattle.io.BattleNext_QuickBattleSaver;
import data.model.quickbattle.io.BattleNext_QuickBattleStandardLoader;

import java.io.IOException;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleStore extends BattleNext_BaseStore<BattleNext_QuickBattleModel> {

	private final BattleNext_QuickBattleStandardLoader standardLoader = new BattleNext_QuickBattleStandardLoader();
	private final BattleNext_QuickBattleCommonLoader commonLoader = new BattleNext_QuickBattleCommonLoader();
	private final BattleNext_QuickBattleLegacyLoader legacyLoader = new BattleNext_QuickBattleLegacyLoader();
	private final BattleNext_QuickBattleSaver saver = new BattleNext_QuickBattleSaver(this::getAll, this::getComments);
	private List<String> comments = new ArrayList<>();

	public BattleNext_QuickBattleStore() {
		super("Quick battle catalog has not been loaded");
	}

	public List<String> getComments() {
		return List.copyOf(comments);
	}

	@Override
	protected void loadState() throws IOException, JSONException {
		comments = commonLoader.exists() ? commonLoader.loadComments() : standardLoader.loadComments();
	}

	@Override
	protected BattleNext_QuickBattleSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_QuickBattleModel>> sources() {
		return List.of(standardLoader, commonLoader, legacyLoader);
	}
}
