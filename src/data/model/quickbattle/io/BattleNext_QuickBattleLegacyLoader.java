package data.model.quickbattle.io;

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
	private static final String LEGACY_FLEET_ID_PREFIX = "ab_player";

	@Override
	public List<BattleNext_QuickBattleModel> load() throws IOException, JSONException {
		List<BattleNext_QuickBattleModel> result = new ArrayList<>();
		String text;
		try {
			text = Global.getSettings().loadText(LEGACY_PATH, MOD_ID);
		} catch (IOException | JSONException exception) {
			Global.getLogger(BattleNext_QuickBattleLegacyLoader.class).warn("Legacy pvp matches are unavailable: " + LEGACY_PATH);
			return result;
		}
		int index = 0;
		for (String line : text.split("\\R")) {
			BattleNext_QuickBattleModel model = BattleNext_QuickBattleModel.fromCsvRow(line);
			if (model == null) continue;
			model.setPlayer(LEGACY_FLEET_ID_PREFIX + model.getPlayer());
			model.setEnemy(LEGACY_FLEET_ID_PREFIX + model.getEnemy());
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
