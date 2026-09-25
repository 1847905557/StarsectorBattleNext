package data.scripts.tournament.io;

import com.fs.starfarer.api.Global;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_JsonCatalog;
import data.scripts.tools.BattleNext_LoadSource;
import data.scripts.tournament.BattleNext_Tournament;

import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TournamentStandardLoader {

	private static final String MOD_ID = "AI_Battles_Next";

	public Map<String, BattleNext_Tournament> load() {
		Map<String, BattleNext_Tournament> result = new LinkedHashMap<>();
		for (String path : BattleNext_JsonCatalog.resourceFiles("tournament")) {
			try {
				BattleNext_Tournament value = BattleNext_Tournament.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
				if (value.getId().isEmpty()) continue;
				value.setSourceType(BattleNext_LoadSource.STANDARD);
				value.setSourcePath(path);
				propagateToMatches(value);
				result.put(value.getId(), value);
			} catch (Exception exception) {
				throw new IllegalStateException("Unable to load tournament: " + path, exception);
			}
		}
		return result;
	}

	private void propagateToMatches(BattleNext_Tournament tournament) {
		for (BattleNext_Match match : tournament.getMatches()) {
			match.setSourceType(tournament.getSourceType());
			match.setSourcePath(tournament.getSourcePath());
		}
	}
}
