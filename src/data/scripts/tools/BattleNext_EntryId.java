package data.scripts.tools;

import java.util.Locale;

public class BattleNext_EntryId {

	private BattleNext_EntryId() {
	}

	public static boolean isValid(String value) {
		if (value == null || value.isEmpty() || !value.equals(value.trim()) || value.matches("[0-9]+")) return false;
		if (value.endsWith(".") || value.endsWith(" ")) return false;
		for (int index = 0; index < value.length(); index++) {
			char letter = value.charAt(index);
			if (letter < 32 || "<>:\"/\\|?*".indexOf(letter) >= 0) return false;
		}
		String stem = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
		if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return false;
		return true;
	}

	public static boolean sameFileName(String first, String second) {
		return first.equalsIgnoreCase(second);
	}
}
