package data.model.rules.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.rules.BattleNext_RulesModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_RulesStandardLoader extends BattleNext_CatalogLoader<BattleNext_RulesModel> {

	private static final String RESOURCE_ROOT = "tournament/_rules";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_ModFileManager.listJson(RESOURCE_ROOT);
	}

	@Override
	protected BattleNext_RulesModel parse(String path) throws IOException, JSONException {
		BattleNext_RulesModel model = new BattleNext_RulesModel();
		model.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
		return model;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}

	@Override
	protected String kind() {
		return "rules";
	}
}
