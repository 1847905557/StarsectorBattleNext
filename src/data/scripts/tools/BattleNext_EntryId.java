package data.scripts.tools;

import java.util.Locale;

public class BattleNext_EntryId {

	private BattleNext_EntryId() {
	}

	public static boolean isValid(String value) {
		return problem(value) == null;
	}

	public enum Reason { REQUIRED, NUMERIC, CONTROL, WHITESPACE, CHARACTER, TRAILING_DOT, RESERVED }

	public record Problem(Reason reason, int position, int codePoint) { }

	public static Problem problem(String value) {
		if (value == null || value.isEmpty()) return new Problem(Reason.REQUIRED, 0, 0);
		if (value.matches("[0-9]+")) return new Problem(Reason.NUMERIC, 0, 0);
		for (int index = 0; index < value.length(); index++) {
			int letter = value.codePointAt(index);
			int position = value.codePointCount(0, index) + 1;
			if (letter < 32) return new Problem(Reason.CONTROL, position, letter);
			if (isWhitespace(letter)) return new Problem(Reason.WHITESPACE, position, letter);
			if ("<>:\"/\\|?*".indexOf(letter) >= 0) return new Problem(Reason.CHARACTER, position, letter);
			index += Character.charCount(letter) - 1;
		}
		if (value.endsWith(".")) return new Problem(Reason.TRAILING_DOT, 0, 0);
		String stem = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
		if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return new Problem(Reason.RESERVED, 0, 0);
		// Reserves fixed resource directory names.
		if (isReservedFolder(value)) return new Problem(Reason.RESERVED, 0, 0);
		return null;
	}

	public static String trimWhitespace(String value) {
		int start = 0;
		int end = value.length();
		while (start < end) {
			int codePoint = value.codePointAt(start);
			if (!isWhitespace(codePoint)) break;
			start += Character.charCount(codePoint);
		}
		while (end > start) {
			int codePoint = value.codePointBefore(end);
			if (!isWhitespace(codePoint)) break;
			end -= Character.charCount(codePoint);
		}
		return value.substring(start, end);
	}

	public static boolean isValidPathSegment(String value) {
		if (!isValid(value)) return false;
		return !value.equals(".") && !value.equals("..")
				&& value.indexOf('/') < 0 && value.indexOf('\\') < 0;
	}

	public static boolean isReservedFolder(String folderName) {
		return folderName.matches("(?i)_reports|_rules|_tournaments|_pinnedVariants|_simulations");
	}

	public static boolean sameFileName(String first, String second) {
		return first.equals(second);
	}

	private static boolean isWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
	}
}
