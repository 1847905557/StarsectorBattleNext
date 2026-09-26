package data.model.quickbattle.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleStandardLoader extends BattleNext_BaseLoader<BattleNext_QuickBattleModel> {

	private static final String BUILT_IN_PATH = "tournament/quick_battle.csv";

	@Override
	public List<BattleNext_QuickBattleModel> load() throws IOException, JSONException {
		List<BattleNext_QuickBattleModel> result = new ArrayList<>();
		int index = 0;
		String text = Global.getSettings().loadText(BUILT_IN_PATH, MOD_ID);
		String[] lines = text.split("\\R", -1);
		int limit = text.endsWith("\n") || text.endsWith("\r") ? lines.length - 1 : lines.length;
		for (int lineIndex = 0; lineIndex < limit; lineIndex++) {
			String line = lines[lineIndex];
			BattleNext_QuickBattleModel model = BattleNext_QuickBattleModel.parseLine(line, false);
			if (model == null) continue;
			model.setId("quick-standard-" + index);
			index++;
			markSource(model, BUILT_IN_PATH);
			result.add(model);
		}
		return result;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}
}
