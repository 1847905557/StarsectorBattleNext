package data.scripts.tools;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_CsvLine {

	private BattleNext_CsvLine() {

	}

	public static List<String> parse(String line) {
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

	public static String escape(String value) {
		String normalized = value == null ? "" : value;
		return normalized.contains(",") ? "\"" + normalized.replace("\"", "\"\"") + "\"" : normalized;
	}
}
