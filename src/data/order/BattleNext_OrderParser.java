package data.order;

import data.order.BattleNext_OrderRegistry.BattleNext_OrderSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BattleNext_OrderParser {

	private static final Pattern SEGMENT = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\(([^()]*)\\))?$");

	private BattleNext_OrderParser() {

	}

	public static OrderParseResult parse(String expression) {
		return parse(expression, BattleNext_OrderRegistry.getInstance());
	}

	public static OrderParseResult parse(String expression, BattleNext_OrderRegistry registry) {
		if (expression == null || expression.trim().isEmpty()) return OrderParseResult.error("empty instruction");

		List<String> segments = splitOutsideParentheses(expression.trim(), '.');
		if (segments.size() < 2) return OrderParseResult.error("missing picker");
		String starter = null;
		String picker = null;
		List<OrderScriptInvocation> filters = new ArrayList<>();
		List<OrderScriptInvocation> suffixes = new ArrayList<>();
		for (int index = 0; index < segments.size(); index++) {
			Matcher matcher = SEGMENT.matcher(segments.get(index).trim());
			if (!matcher.matches()) return OrderParseResult.error("invalid segment: " + segments.get(index).trim());
			String id = matcher.group(1);
			List<String> params = splitParams(matcher.group(2));
			BattleNext_OrderSpec spec = registry.get(id);
			if (spec == null) return OrderParseResult.error("unknown order ID: " + id);
			String type = spec.type();
			if (index == 0) {
				if (!"starter".equals(type) || matcher.group(2) != null) return OrderParseResult.error("expected starter without parameters: " + id);
				starter = id;
			} else if ("filter".equals(type) && picker == null) {
				filters.add(new OrderScriptInvocation(id, params));
			} else if ("picker".equals(type) && picker == null && matcher.group(2) == null) {
				picker = id;
			} else if ("suffix".equals(type) && picker != null) {
				suffixes.add(new OrderScriptInvocation(id, params));
			} else {
				return OrderParseResult.error("invalid order position or parameters: " + id);
			}
		}
		if (picker == null) return OrderParseResult.error("missing picker");

		return OrderParseResult.success(new ParsedOrderCommand(starter, picker, filters, suffixes));
	}

	public static List<OrderParseResult> parseScript(String script) {
		return parseScript(script, BattleNext_OrderRegistry.getInstance());
	}

	public static List<OrderParseResult> parseScript(String script, BattleNext_OrderRegistry registry) {
		if (script == null) return List.of();
		List<OrderParseResult> results = new ArrayList<>();
		int lineNumber = 0;
		for (String line : script.split("\\R")) {
			lineNumber++;
			for (String expression : line.split(";", -1)) {
				if (expression.trim().isEmpty()) continue;
				OrderParseResult result = parse(expression, registry);
				result.instructionNumber = results.size() + 1;
				result.lineNumber = lineNumber;
				results.add(result);
			}
		}
		return results;
	}

	private static List<String> splitOutsideParentheses(String value, char delimiter) {
		List<String> result = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (current == '(') depth++;
			if (current == ')') depth--;
			if (current == delimiter && depth == 0) {
				result.add(value.substring(start, index));
				start = index + 1;
			}
		}
		result.add(value.substring(start));
		return result;
	}

	private static List<String> splitParams(String value) {
		if (value == null || value.trim().isEmpty()) return List.of();
		List<String> result = new ArrayList<>();
		for (String param : value.split(",", -1)) result.add(param.trim());
		return Collections.unmodifiableList(result);
	}

	public record OrderScriptInvocation(String id, List<String> params) {

	}

	public record ParsedOrderCommand(String starter, String picker, List<OrderScriptInvocation> filters, List<OrderScriptInvocation> suffixes) {
		public ParsedOrderCommand {
			filters = Collections.unmodifiableList(filters);
			suffixes = Collections.unmodifiableList(suffixes);
		}
	}

	public static class OrderParseResult {

		private final ParsedOrderCommand command;
		private final String error;
		private int instructionNumber = 1;
		private int lineNumber = 1;

		private OrderParseResult(ParsedOrderCommand command, String error) {
			this.command = command;
			this.error = error;
		}

		public static OrderParseResult success(ParsedOrderCommand command) {
			return new OrderParseResult(command, null);
		}

		public static OrderParseResult error(String error) {
			return new OrderParseResult(null, error);
		}

		public boolean isValid() {
			return command != null;
		}

		public int getInstructionNumber() {
			return instructionNumber;
		}

		public int getLineNumber() {
			return lineNumber;
		}

		public ParsedOrderCommand getCommand() {
			return command;
		}

		public String getError() {
			return error;
		}
	}
}
