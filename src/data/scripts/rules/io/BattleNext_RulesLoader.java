package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.scripts.rules.BattleNext_RulesModel;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_RulesLoader {

	private static final int SLOT_COUNT = 32;
	private static final String MOD_ID = "AI_Battles_Next";

	public Map<Integer, BattleNext_RulesModel> loadBuiltIn() {
		Map<Integer, BattleNext_RulesModel> result = new LinkedHashMap<>();
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			try {
				result.put(slot, BattleNext_RulesModel.fromJson(Global.getSettings().loadJSON("rules/rules_" + slot + ".json", MOD_ID)));
			} catch (Exception ignored) {
			}
		}
		return result;
	}

	public Map<Integer, BattleNext_RulesModel> loadUser() throws IOException, JSONException {
		Map<Integer, BattleNext_RulesModel> result = new LinkedHashMap<>();
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			String userPath = "AI_Battles_Next/rules/rules_" + slot + ".json";
			if (Global.getSettings().fileExistsInCommon(userPath)) {
				result.put(slot, BattleNext_RulesModel.fromJson(Global.getSettings().readJSONFromCommon(userPath, false)));
			}
		}
		return result;
	}
}
