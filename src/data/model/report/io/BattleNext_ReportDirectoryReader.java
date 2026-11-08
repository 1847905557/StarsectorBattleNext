package data.model.report.io;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

final class BattleNext_ReportDirectoryReader {

	record Header(JSONObject metadata, int members) {

	}

	private BattleNext_ReportDirectoryReader() {

	}

	static Header read(String text) throws JSONException {
		JSONTokener input = new JSONTokener(text);
		if (input.nextClean() != '{') throw input.syntaxError("Expected report object");

		JSONObject metadata = new JSONObject();
		int members = 0;
		while (true) {
			char first = input.nextClean();
			if (first == '}') break;
			input.back();
			String key = input.nextValue().toString();
			if (input.nextClean() != ':') throw input.syntaxError("Expected report field separator");
			if (key.equals("playerMembers") || key.equals("enemyMembers")) {
				if (input.nextClean() != '[') throw input.syntaxError("Expected report members array");
				int depth = 1;
				boolean item = false;
				while (depth > 0) {
					char next = input.nextClean();
					if (next == 0) throw input.syntaxError("Unterminated report members array");
					if (depth == 1 && next != ']' && next != ',' && !item) {
						members++;
						item = true;
					}
					if (next == '"' || next == '\'') input.nextString(next);
					else if (next == '{' || next == '[') depth++;
					else if (next == '}' || next == ']') depth--;
					else if (next == ',' && depth == 1) item = false;
				}
			} else {
				metadata.put(key, input.nextValue());
			}

			char separator = input.nextClean();
			if (separator == '}') break;
			if (separator != ',') throw input.syntaxError("Expected report field delimiter");
		}

		return new Header(metadata, members);
	}
}
