package data.scripts.rules;

import data.scripts.rules.io.BattleNext_RulesCommonLoader;
import data.scripts.rules.io.BattleNext_RulesSaver;
import data.scripts.rules.io.BattleNext_RulesStandardLoader;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_RulesStore {

	private final BattleNext_RulesStandardLoader standardLoader = new BattleNext_RulesStandardLoader();
	private final BattleNext_RulesCommonLoader commonLoader = new BattleNext_RulesCommonLoader();
	private final BattleNext_RulesSaver saver = new BattleNext_RulesSaver();
	private final Map<String, BattleNext_RulesModel> rules = new LinkedHashMap<>();

	public void reload() throws IOException, JSONException {
		rules.clear();
		rules.putAll(standardLoader.load());
		rules.putAll(commonLoader.load());
	}

	public void save(BattleNext_RulesModel model) throws IOException, JSONException {
		if (model == null) {
			throw new IllegalArgumentException("Rules model is required");
		}
		saver.save(model);
		model.setSourceType(BattleNext_LoadSource.COMMON);
		model.setSourcePath("AI_Battles_Next/rules/" + model.getId() + ".json");
		rules.put(model.getId(), model);
	}

	public BattleNext_RulesModel get(String id) {
		return rules.get(id);
	}

	public List<BattleNext_RulesModel> getAll() {
		return List.copyOf(rules.values());
	}
}
