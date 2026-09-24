package data.scripts.match;

import com.fs.starfarer.api.Global;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_MatchBook {

	private static final String PATH = "AI_Battles_Next/quick_battle.csv";
	private final List<BattleNext_Match> matches = new ArrayList<>();

	public void load() throws IOException {
		matches.clear();
		String text = Global.getSettings().readTextFileFromCommon(PATH);
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("player,enemy,map,size")) continue;
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
		StringBuilder text = new StringBuilder("player,enemy,map,size\n");
		for (BattleNext_Match match : matches) text.append(csv(match.getPlayer())).append(',').append(csv(match.getEnemy())).append(',').append(match.getMap()).append(',').append(match.getSize()).append('\n');
		Global.getSettings().writeTextFileToCommon(PATH, text.toString());
	}

	public List<BattleNext_Match> getMatches() { return List.copyOf(matches); }
	public void add(BattleNext_Match match) { if (match != null) matches.add(match); }

	private String csv(String value) { String normalized = value == null ? "" : value; return normalized.contains(",") ? "\"" + normalized.replace("\"", "\"\"") + "\"" : normalized; }
	private List<String> parse(String line) {
		List<String> result = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char value = line.charAt(index);
			if (value == '\"') quoted = !quoted;
			else if (value == ',' && !quoted) { result.add(field.toString().trim()); field.setLength(0); }
			else field.append(value);
		}
		result.add(field.toString().trim());
		return result;
	}
}
