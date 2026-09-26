package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseSaver;
import data.scripts.rules.BattleNext_RulesModel;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_RulesSaver extends BattleNext_BaseSaver<BattleNext_RulesModel> {

	private static final String COMMON_ROOT = "AI_Battles_Next/rules/";

	@Override
	public String save(BattleNext_RulesModel model) throws IOException, JSONException {
		String path = COMMON_ROOT + model.getId() + ".json";
		writeCommonJson(path, model.toJson());
		return path;
	}

	@Override
	public void delete(BattleNext_RulesModel model) throws IOException {
		String path = COMMON_ROOT + model.getId() + ".json";
		Global.getSettings().deleteTextFileFromCommon(path);
		if (Global.getSettings().fileExistsInCommon(path)) {
			throw new IOException("Rules file remains after deletion: " + model.getId());
		}
	}
}
