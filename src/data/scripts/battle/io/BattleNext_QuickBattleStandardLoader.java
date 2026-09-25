package data.scripts.battle.io;

import com.fs.starfarer.api.Global;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_CsvLine;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleStandardLoader {

	private static final String BUILT_IN_PATH = "tournament/quick_battle.csv";
	private static final String MOD_ID = "AI_Battles_Next";

	public BattleNext_QuickBattleSnapshot load() throws IOException, JSONException {
		return read(Global.getSettings().loadText(BUILT_IN_PATH, MOD_ID));
	}

	private BattleNext_QuickBattleSnapshot read(String text) {
		List<BattleNext_Match> matches = new ArrayList<>();
		List<String> comments = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.equals("player,enemy,map,size")) continue;
			if (trimmed.startsWith("#")) {
				comments.add(trimmed);
				continue;
			}
			List<String> fields = BattleNext_CsvLine.parse(line);
			if (fields.size() != 4) continue;
			try {
				int map = Integer.parseInt(fields.get(2));
				float size = Float.parseFloat(fields.get(3));
				if (!Float.isFinite(size) || size <= 0f) continue;
				BattleNext_Match match = new BattleNext_Match("quick-" + matches.size(), fields.get(0), fields.get(1), map, size);
				match.setSourceType(BattleNext_LoadSource.STANDARD);
				match.setSourcePath(BUILT_IN_PATH);
				matches.add(match);
			} catch (NumberFormatException exception) {
				continue;
			}
		}
		return new BattleNext_QuickBattleSnapshot(List.copyOf(matches), List.copyOf(comments));
	}
}
