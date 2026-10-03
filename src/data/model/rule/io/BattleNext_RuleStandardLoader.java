package data.model.rule.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.rule.BattleNext_RuleModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_RuleStandardLoader extends BattleNext_CatalogLoader<BattleNext_RuleModel> {

	private static final String RESOURCE_ROOT = "tournament/_rules";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_ModFileManager.listJson(RESOURCE_ROOT);
	}

	@Override
	protected BattleNext_RuleModel parse(String path) throws IOException, JSONException {
		BattleNext_RuleModel model = new BattleNext_RuleModel();
		model.fromJson(BattleNext_ModFileManager.readJson(BattleNext_RuleStandardLoader.class, path, MOD_ID));
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
