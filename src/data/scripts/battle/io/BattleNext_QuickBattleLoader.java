package data.scripts.battle.io;

import com.fs.starfarer.api.Global;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_CsvLine;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleLoader {

	private static final String USER_PATH = "AI_Battles_Next/quick_battle.csv";
	private static final String BUILT_IN_PATH = "tournament/quick_battle.csv";
	private static final String MOD_ID = "AI_Battles_Next";

	public record Snapshot(List<BattleNext_Match> matches, List<String> comments) {
	}

	public boolean hasUserFile() {
		return Global.getSettings().fileExistsInCommon(USER_PATH);
	}

	public Snapshot loadUser() throws IOException {
		return read(Global.getSettings().readTextFileFromCommon(USER_PATH));
	}

	public Snapshot loadBuiltIn() throws IOException, JSONException {
		return read(Global.getSettings().loadText(BUILT_IN_PATH, MOD_ID));
	}

	private Snapshot read(String text) {
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
				matches.add(new BattleNext_Match("quick-" + matches.size(), fields.get(0), fields.get(1), map, size));
			} catch (NumberFormatException exception) {
				continue;
			}
		}
		return new Snapshot(List.copyOf(matches), List.copyOf(comments));
	}
}
