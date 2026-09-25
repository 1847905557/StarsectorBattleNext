package data.scripts.rules.io;

import com.fs.starfarer.api.Global;
import data.scripts.rules.BattleNext_RulesModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_RulesCommonLoader {

	private static final String COMMON_ROOT = "AI_Battles_Next/rules/";

	public Map<String, BattleNext_RulesModel> load() throws IOException, JSONException {
		Map<String, BattleNext_RulesModel> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.commonFiles(COMMON_ROOT)) {
			try {
				BattleNext_RulesModel value = BattleNext_RulesModel.fromJson(Global.getSettings().readJSONFromCommon(path, false));
				if (value.getId().isEmpty()) continue;
				value.setSourceType(BattleNext_LoadSource.COMMON);
				value.setSourcePath(path);
				result.put(value.getId(), value);
			} catch (IllegalArgumentException exception) {
				throw new IllegalStateException("Unable to load common rules: " + path, exception);
			}
		}
		return result;
	}
}
