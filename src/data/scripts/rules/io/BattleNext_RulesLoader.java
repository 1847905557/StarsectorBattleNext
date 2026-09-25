package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.scripts.battlesnext.util.BattleNext_JsonCatalog;
import data.scripts.rules.BattleNext_RulesModel;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_RulesLoader {

	private static final String MOD_ID = "AI_Battles_Next";

	public Map<String, BattleNext_RulesModel> loadBuiltIn() {
		Map<String, BattleNext_RulesModel> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.resourceFiles("tournament/rules")) {
			try {
				BattleNext_RulesModel value = BattleNext_RulesModel.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
				if (!value.getId().isEmpty()) result.put(value.getId(), value);
			} catch (Exception ignored) {
			}
		}
		return result;
	}

	public Map<String, BattleNext_RulesModel> loadUser() throws IOException, JSONException {
		Map<String, BattleNext_RulesModel> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.commonFiles("AI_Battles_Next/rules")) {
			try {
				BattleNext_RulesModel value = BattleNext_RulesModel.fromJson(Global.getSettings().readJSONFromCommon(path, false));
				if (!value.getId().isEmpty()) result.put(value.getId(), value);
			} catch (IllegalArgumentException ignored) {
			}
		}
		return result;
	}
}
