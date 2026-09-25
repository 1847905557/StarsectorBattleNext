package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.scripts.rules.BattleNext_RulesModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import data.scripts.tools.BattleNext_LoadSource;

import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_RulesStandardLoader {

	private static final String MOD_ID = "AI_Battles_Next";

	public Map<String, BattleNext_RulesModel> load() {
		Map<String, BattleNext_RulesModel> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.resourceFiles("tournament/rules")) {
			try {
				BattleNext_RulesModel value = BattleNext_RulesModel.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
				if (value.getId().isEmpty()) continue;
				value.setSourceType(BattleNext_LoadSource.STANDARD);
				value.setSourcePath(path);
				result.put(value.getId(), value);
			} catch (Exception exception) {
				throw new IllegalStateException("Unable to load rules: " + path, exception);
			}
		}
		return result;
	}
}
