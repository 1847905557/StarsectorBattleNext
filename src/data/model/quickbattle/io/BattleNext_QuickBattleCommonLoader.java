package data.model.quickbattle.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleCommonLoader extends BattleNext_BaseLoader<BattleNext_QuickBattleModel> {

	private static final String USER_PATH = "AI_Battles_Next/quick_battle.csv";

	@Override
	public List<BattleNext_QuickBattleModel> load() throws IOException, JSONException {
		List<BattleNext_QuickBattleModel> result = new ArrayList<>();
		if (!exists()) return result;
		int index = 0;
		for (String line : Global.getSettings().readTextFileFromCommon(USER_PATH).split("\\R")) {
			BattleNext_QuickBattleModel model = BattleNext_QuickBattleModel.fromCsvRow(line);
			if (model == null) continue;
			model.setId("quick-" + index);
			index++;
			markSource(model, USER_PATH);
			result.add(model);
		}
		return result;
	}

	public boolean exists() {
		return Global.getSettings().fileExistsInCommon(USER_PATH);
	}

	public List<String> loadComments() throws IOException, JSONException {
		List<String> result = new ArrayList<>();
		if (!exists()) return result;
		for (String line : Global.getSettings().readTextFileFromCommon(USER_PATH).split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("#")) result.add(trimmed);
		}
		return result;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.COMMON;
	}
}
