package data.scripts.rules;

import data.scripts.rules.io.BattleNext_RulesLoader;
import data.scripts.rules.io.BattleNext_RulesSaver;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_RulesStore {

	private final BattleNext_RulesLoader loader = new BattleNext_RulesLoader();
	private final BattleNext_RulesSaver saver = new BattleNext_RulesSaver();
	private final Map<String, BattleNext_RulesModel> rules = new LinkedHashMap<>();

	public void reload() throws IOException, JSONException {
		rules.clear();
		rules.putAll(loader.loadBuiltIn());
		rules.putAll(loader.loadUser());
	}

	public void save(BattleNext_RulesModel model) throws IOException, JSONException {
		if (model == null) {
			throw new IllegalArgumentException("Rules model is required");
		}
		saver.save(model);
		rules.put(model.getId(), model);
	}

	public BattleNext_RulesModel get(String id) {
		return rules.get(id);
	}

	public List<BattleNext_RulesModel> getAll() {
		return List.copyOf(rules.values());
	}
}
