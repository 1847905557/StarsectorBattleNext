package data.scripts.rules;

import com.fs.starfarer.api.Global;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_RulesStore {

	private final Map<Integer, BattleNext_RulesModel> rules = new LinkedHashMap<>();

	public void reload() throws IOException, JSONException {
		rules.clear();
		for (int slot = 0; slot < 32; slot++) {
			String path = "rules/rules_" + slot + ".json";
			try { rules.put(slot, BattleNext_RulesModel.fromJson(Global.getSettings().loadJSON(path, "AI_Battles_Next"))); } catch (Exception ignored) { }
			String userPath = "AI_Battles_Next/rules/rules_" + slot + ".json";
			if (Global.getSettings().fileExistsInCommon(userPath)) rules.put(slot, BattleNext_RulesModel.fromJson(Global.getSettings().readJSONFromCommon(userPath, false)));
		}
	}

	public void save(BattleNext_RulesModel model) throws IOException, JSONException {
		if (model == null) throw new IllegalArgumentException("Rules model is required");
		Global.getSettings().writeJSONToCommon("AI_Battles_Next/rules/rules_" + model.getSlot() + ".json", model.toJson(), false);
		rules.put(model.getSlot(), model);
	}

	public BattleNext_RulesModel get(int slot) { return rules.get(slot); }
	public List<BattleNext_RulesModel> getAll() { return List.copyOf(rules.values()); }
}
