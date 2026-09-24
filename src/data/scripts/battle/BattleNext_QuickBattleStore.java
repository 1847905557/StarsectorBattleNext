package data.scripts.match;

import data.scripts.match.io.BattleNext_QuickBattleLoader;
import data.scripts.match.io.BattleNext_QuickBattleSaver;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleStore {

	private final BattleNext_QuickBattleLoader loader = new BattleNext_QuickBattleLoader();
	private final BattleNext_QuickBattleSaver saver = new BattleNext_QuickBattleSaver();
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private final List<String> comments = new ArrayList<>();

	public boolean hasUserFile() {
		return loader.hasUserFile();
	}

	public void load() throws IOException {
		apply(loader.loadUser());
	}

	public void loadBuiltIn() throws IOException, JSONException {
		apply(loader.loadBuiltIn());
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
}
