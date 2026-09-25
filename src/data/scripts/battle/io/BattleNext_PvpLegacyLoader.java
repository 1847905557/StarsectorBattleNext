package data.scripts.battle.io;

import com.fs.starfarer.api.Global;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_CsvLine;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_PvpLegacyLoader {

	private static final String LEGACY_MATCHES_PATH = "tournament/PvP_matches.csv";
	private static final String MOD_ID = "AI_Battles_Next";
	private static final String LEGACY_FLEET_ID_PREFIX = "ab_player";

	public List<BattleNext_Match> load() {
		List<BattleNext_Match> result = new ArrayList<>();
		String text;
		try {
			text = Global.getSettings().loadText(LEGACY_MATCHES_PATH, MOD_ID);
		} catch (IOException | JSONException exception) {
			Global.getLogger(BattleNext_PvpLegacyLoader.class).warn("Legacy pvp matches are unavailable: " + LEGACY_MATCHES_PATH);
			return result;
		}
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("player,enemy,map,size")) continue;
			List<String> fields = BattleNext_CsvLine.parse(line);
			if (fields.size() != 4) continue;
			try {
				int map = Integer.parseInt(fields.get(2));
				float size = Float.parseFloat(fields.get(3));
				if (!Float.isFinite(size) || size <= 0f) continue;
				result.add(new BattleNext_Match("pvp-legacy-" + result.size(), legacyFleetId(fields.get(0)), legacyFleetId(fields.get(1)), map, size));
			} catch (NumberFormatException exception) {
				continue;
			}
		}
		return result;
	}

	private String legacyFleetId(String number) {
		return LEGACY_FLEET_ID_PREFIX + number;
	}
}
