package data.model.quickbattle.io;

import data.scripts.tools.BattleNext_Logger;

import data.scripts.tools.BattleNext_ModFileManager;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleLegacyLoader extends BattleNext_BaseLoader<BattleNext_QuickBattleModel> {

	private static final String LEGACY_PATH = "tournament/PvP_matches.csv";
	@Override
	public List<BattleNext_QuickBattleModel> load() throws IOException, JSONException {
		List<BattleNext_QuickBattleModel> result = new ArrayList<>();
		String text;
		try {
			text = BattleNext_ModFileManager.readText(BattleNext_QuickBattleLegacyLoader.class, LEGACY_PATH, MOD_ID);
		} catch (IOException | JSONException exception) {
			BattleNext_Logger.warn(BattleNext_QuickBattleLegacyLoader.class, "quick_battle.legacy.load.degraded",
				BattleNext_Logger.fields().put("path", LEGACY_PATH).put("fallback", result).build(), exception);
			return result;
		}
		int index = 0;
		String[] lines = text.split("\\R", -1);
		int limit = text.endsWith("\n") || text.endsWith("\r") ? lines.length - 1 : lines.length;
		for (int lineIndex = 0; lineIndex < limit; lineIndex++) {
			String line = lines[lineIndex];
			BattleNext_QuickBattleModel model = BattleNext_QuickBattleModel.parseLine(line, true);
			if (model == null) continue;
			if (model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW && !line.isBlank() && !line.trim().startsWith("#")) {
				BattleNext_Logger.warn(BattleNext_QuickBattleLegacyLoader.class, "quick_battle.row.degraded",
				BattleNext_Logger.fields().put("path", LEGACY_PATH).put("line", lineIndex + 1).put("input", line).put("model", model).put("criterion", "Four columns, fleet references, supported map and positive finite scale").build());
			}
			model.setId("pvp-legacy-" + index);
			index++;
			markSource(model, LEGACY_PATH);
			result.add(model);
		}
		return result;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.LEGACY;
	}
}
