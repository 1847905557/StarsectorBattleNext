package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.scripts.rules.BattleNext_RulesModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_RulesCommonLoader extends BattleNext_CatalogLoader<BattleNext_RulesModel> {

	private static final String COMMON_ROOT = "AI_Battles_Next/rules/";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_JsonCatalog.commonFiles(COMMON_ROOT);
	}

	@Override
	protected BattleNext_RulesModel parse(String path) throws IOException, JSONException {
		BattleNext_RulesModel model = new BattleNext_RulesModel();
		model.fromJson(Global.getSettings().readJSONFromCommon(path, false));
		return model;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.COMMON;
	}

	@Override
	protected String kind() {
		return "common rules";
	}
}
