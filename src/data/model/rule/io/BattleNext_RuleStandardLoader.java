package data.model.rule.io;

import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.rule.BattleNext_RuleModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class BattleNext_RuleStandardLoader extends BattleNext_CatalogLoader<BattleNext_RuleModel> {

	private static final String RESOURCE_ROOT = "tournament/_rules";

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return paths(RESOURCE_ROOT, false).stream().filter(path -> path.endsWith(".json")).toList();
	}

	@Override
	protected BattleNext_RuleModel parse(JSONObject json) throws IOException, JSONException {
		BattleNext_RuleModel model = new BattleNext_RuleModel();
		model.fromJson(json);
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
