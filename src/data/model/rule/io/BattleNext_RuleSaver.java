package data.model.rule.io;

import data.model.BattleNext_BaseSaver;
import data.model.rule.BattleNext_RuleModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_RuleSaver extends BattleNext_BaseSaver<BattleNext_RuleModel> {

	private static final String RESOURCE_ROOT = "tournament/_rules/";

	@Override
	public String save(BattleNext_RuleModel model) throws IOException, JSONException {
		if (!BattleNext_EntryId.isValidPathSegment(model.getId())) throw new IOException("Invalid rules ID: " + model.getId());
		String path = RESOURCE_ROOT + model.getId() + ".json";
		writeResourceJson(path, model.toJson());
		return path;
	}

	@Override
	public void delete(BattleNext_RuleModel model) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(model.getId())) throw new IOException("Invalid rules ID: " + model.getId());
		String path = RESOURCE_ROOT + model.getId() + ".json";
		BattleNext_ModFileManager.delete(path);
		if (BattleNext_ModFileManager.exists(path)) throw new IOException("Rules file remains after deletion: " + model.getId());
	}
}
