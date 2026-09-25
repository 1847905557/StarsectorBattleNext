package data.scripts.battle;

import data.scripts.battle.io.BattleNext_PvpLegacyLoader;
import data.scripts.battle.io.BattleNext_QuickBattleLoader;
import data.scripts.battle.io.BattleNext_QuickBattleSaver;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleStore {

	private final BattleNext_QuickBattleLoader loader = new BattleNext_QuickBattleLoader();
	private final BattleNext_PvpLegacyLoader pvpLegacyLoader = new BattleNext_PvpLegacyLoader();
	private final BattleNext_QuickBattleSaver saver = new BattleNext_QuickBattleSaver();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private final List<String> comments = new ArrayList<>();

	public boolean hasUserFile() {
		return loader.hasUserFile();
	}

	public void load() throws IOException {
		apply(loader.loadUser());
		appendPvpLegacy();
	}

	public void loadBuiltIn() throws IOException, JSONException {
		apply(loader.loadBuiltIn());
		appendPvpLegacy();
	}

	public void save() throws IOException {
		saver.save(matches, comments);
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

	private void apply(BattleNext_QuickBattleLoader.Snapshot snapshot) {
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
