package data.scripts.battle.io;

import com.fs.starfarer.api.Global;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_CsvLine;

import java.io.IOException;
import java.util.List;

public class BattleNext_QuickBattleSaver {

	private static final String PATH = "AI_Battles_Next/quick_battle.csv";

	public void save(List<BattleNext_Match> matches, List<String> comments) throws IOException {
		StringBuilder text = new StringBuilder();
		for (String comment : comments) {
			text.append(comment).append('\n');
		}
		text.append("player,enemy,map,size\n");
		for (BattleNext_Match match : matches) {
			text.append(BattleNext_CsvLine.escape(match.getPlayer())).append(',').append(BattleNext_CsvLine.escape(match.getEnemy())).append(',').append(match.getMap()).append(',').append(match.getSize()).append('\n');
		}
		Global.getSettings().writeTextFileToCommon(PATH, text.toString());
	}
}
