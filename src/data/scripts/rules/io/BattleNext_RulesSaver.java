package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.scripts.rules.BattleNext_RulesModel;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_RulesSaver {

	private static final String COMMON_ROOT = "AI_Battles_Next/rules/";

	public void save(BattleNext_RulesModel model) throws IOException, JSONException {
		Global.getSettings().writeJSONToCommon(COMMON_ROOT + model.getId() + ".json", model.toJson(), false);
	}
}
