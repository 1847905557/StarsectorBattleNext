package data.scripts.match;

import com.fs.starfarer.api.Global;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_MatchBook {

	private static final String PATH = "AI_Battles_Next/quick_battle.csv";
	private final List<BattleNext_Match> matches = new ArrayList<>();
	private final List<String> comments = new ArrayList<>();

	public boolean hasUserFile() {
		return Global.getSettings().fileExistsInCommon(PATH);
	}

	public void load() throws IOException {
		loadText(Global.getSettings().readTextFileFromCommon(PATH));
	}

	public void loadBuiltIn() throws IOException, JSONException {
		loadText(Global.getSettings().loadText("tournament/quick_battle.csv", "AI_Battles_Next"));
	}

	private void loadText(String text) {
		matches.clear();
		comments.clear();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.equals("player,enemy,map,size")) continue;
			if (trimmed.startsWith("#")) {
				comments.add(trimmed);
				continue;
			}
			List<String> fields = parse(line);
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
	}

	public void save() throws IOException {
		StringBuilder text = new StringBuilder();
		for (String comment : comments) {
			text.append(comment).append('\n');
		}
		text.append("player,enemy,map,size\n");
		for (BattleNext_Match match : matches) {
			text.append(csv(match.getPlayer())).append(',').append(csv(match.getEnemy())).append(',').append(match.getMap()).append(',').append(match.getSize()).append('\n');
		}
		Global.getSettings().writeTextFileToCommon(PATH, text.toString());
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

	private String csv(String value) {
		String normalized = value == null ? "" : value;
		return normalized.contains(",") ? "\"" + normalized.replace("\"", "\"\"") + "\"" : normalized;
	}

	private List<String> parse(String line) {
		List<String> result = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char value = line.charAt(index);
			if (value == '\"') {
				quoted = !quoted;
			} else if (value == ',' && !quoted) {
				result.add(field.toString().trim());
				field.setLength(0);
			} else {
				field.append(value);
			}
		}
		result.add(field.toString().trim());
		return result;
	}
}
