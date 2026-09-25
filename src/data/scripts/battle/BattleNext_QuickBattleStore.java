package data.scripts.battle;

import data.scripts.battle.io.BattleNext_QuickBattleSnapshot;
import data.scripts.battle.io.BattleNext_PvpLegacyLoader;
import data.scripts.battle.io.BattleNext_QuickBattleCommonLoader;
import data.scripts.battle.io.BattleNext_QuickBattleSaver;
import data.scripts.battle.io.BattleNext_QuickBattleStandardLoader;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleStore {

	private final BattleNext_QuickBattleStandardLoader standardLoader = new BattleNext_QuickBattleStandardLoader();
	private final BattleNext_QuickBattleCommonLoader commonLoader = new BattleNext_QuickBattleCommonLoader();
	private final BattleNext_PvpLegacyLoader pvpLegacyLoader = new BattleNext_PvpLegacyLoader();
	private final BattleNext_QuickBattleSaver saver = new BattleNext_QuickBattleSaver();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private final List<String> comments = new ArrayList<>();

	public boolean hasUserFile() {
		return commonLoader.hasFile();
	}

	public void load() throws IOException {
		apply(commonLoader.load());
		appendPvpLegacy();
	}

	public void loadBuiltIn() throws IOException, JSONException {
		apply(standardLoader.load());
		appendPvpLegacy();
	}

	public void save() throws IOException {
		saver.save(matches, comments);
		for (BattleNext_Match match : matches) {
			match.setSourceType(BattleNext_LoadSource.COMMON);
			match.setSourcePath("AI_Battles_Next/quick_battle.csv");
		}
	}

	public List<BattleNext_Match> getMatches() {
		return List.copyOf(matches);
	}

	public void add(BattleNext_Match match) {
		if (match != null) {
			matches.add(match);
		}
	}

	public void replaceAll(List<BattleNext_Match> values) {
		matches.clear();
		if (values != null) {
			for (BattleNext_Match value : values) {
				if (value != null) {
					matches.add(value);
				}
			}
		}
	}

	private void apply(BattleNext_QuickBattleSnapshot snapshot) {
		matches.clear();
		comments.clear();
		matches.addAll(snapshot.matches());
		comments.addAll(snapshot.comments());
	}

	private void appendPvpLegacy() {
		for (BattleNext_Match match : pvpLegacyLoader.load()) {
			if (containsMatch(match)) continue;
			matches.add(match);
		}
	}

	private boolean containsMatch(BattleNext_Match candidate) {
		for (BattleNext_Match match : matches) {
			if (match.getPlayer().equals(candidate.getPlayer()) && match.getEnemy().equals(candidate.getEnemy())
					&& match.getMap() == candidate.getMap() && match.getSize() == candidate.getSize()) return true;
		}
		return false;
	}
}
