package data.scripts.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BattleNext_OrderSpec {

	private static final Pattern SEGMENT = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\(([^)]*)\\))?$");

	private final String id;
	private final String type;
	private final String script;
	private final String description;

	public BattleNext_OrderSpec(String id, String type, String script, String description) {
		this.id = id;
		this.type = type;
		this.script = script;
		this.description = description;
	}
	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getScript() {
		return script;
	}

	public String getDescription() {
		return description;
	}

	public static ParseResult parse(String expression) {
		if (expression == null || expression.trim().isEmpty()) return ParseResult.error("empty instruction");
		String[] segments = expression.trim().split("\\.");
		if (segments.length < 2) return ParseResult.error("missing picker");
		Command command = new Command();
		for (int index = 0; index < segments.length; index++) {
			Matcher matcher = SEGMENT.matcher(segments[index].trim());
			if (!matcher.matches()) return ParseResult.error("invalid segment: " + segments[index].trim());
			String id = matcher.group(1).toLowerCase(Locale.ROOT);
			List<String> params = splitParams(matcher.group(2));
			if (index == 0) {
				command.starter = id;
			} else if ("picker".equals(id) || isKnownPickerName(id)) {
				command.picker = id;
			} else if (command.picker == null) {
				command.filters.add(new Part(id, params));
			} else {
				command.suffixes.add(new Part(id, params));
			}
		}
		if (command.picker == null) return ParseResult.error("missing picker");
		return ParseResult.success(command);
	}

	public static List<ParseResult> parseScript(String script) {
		if (script == null) return List.of();
		List<ParseResult> results = new ArrayList<>();
		for (String line : script.toLowerCase(Locale.ROOT).split("\\R")) {
			for (String expression : line.split(";")) {
				if (!expression.trim().isEmpty()) results.add(parse(expression));
			}
		}
		return results;
	}

	private static boolean isKnownPickerName(String id) {
		return id.endsWith("picker") || id.equals("nearest") || id.equals("random") || id.equals("spread") || id.equals("concentrate");
	}

	private static List<String> splitParams(String value) {
		if (value == null || value.trim().isEmpty()) return List.of();
		List<String> result = new ArrayList<>();
		for (String param : value.split(",", -1)) {
			result.add(param.trim());
		}
		return Collections.unmodifiableList(result);
	}

	public static class Part {

		private final String id;
		private final List<String> params;
		private Part(String id, List<String> params) {
			this.id = id;
			this.params = params;
		}

		public String getId() {
			return id;
		}

		public List<String> getParams() {
			return params;
		}
	}

	public static class Command {

		private String starter;
		private String picker;
		private final List<Part> filters = new ArrayList<>();
		private final List<Part> suffixes = new ArrayList<>();
		public String getStarter() {
			return starter;
		}

		public String getPicker() {
			return picker;
		}

		public List<Part> getFilters() {
			return Collections.unmodifiableList(filters);
		}

		public List<Part> getSuffixes() {
			return Collections.unmodifiableList(suffixes);
		}
	}

	public static class ParseResult {

		private final Command command;
		private final String error;
		private ParseResult(Command command, String error) {
			this.command = command;
			this.error = error;
		}

		public static ParseResult success(Command command) {
			return new ParseResult(command, null);
		}

		public static ParseResult error(String error) {
			return new ParseResult(null, error);
		}

		public boolean isValid() {
			return command != null;
		}

		public Command getCommand() {
			return command;
		}

		public String getError() {
			return error;
		}
	}
}
