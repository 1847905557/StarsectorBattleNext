package data.scripts.battlesnext.util;

import com.fs.starfarer.api.Global;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BattleNext_I18nSection {

	private final String category;
	private final String keyPrefix;

	public BattleNext_I18nSection(String category, String keyPrefix) {
		this.category = "Misc".equals(category) && "BattleNext_ui_".equals(keyPrefix) ? "UI" : category;
		this.keyPrefix = Objects.requireNonNullElse(keyPrefix, "");

		sections.add(this);
	}

	public BattleNext_I18nSection(String category) {
		this(category, null);
	}

	public String format(String keyMainBody, @Nullable Object... args) {
		if (args != null && args.length > 0) {
			return absFormat(keyMainBody, args);
		}
		return get(keyMainBody);
	}

	public String get() {
		try {
			return Global.getSettings().getString(category, keyPrefix);
		} catch (Exception e) {
			return "[NULL]";
		}
	}

	public String get(String key) {
		try {
			return Global.getSettings().getString(category, keyPrefix + key);
		} catch (Exception e) {
			return "[NULL]";
		}
	}

	private String absFormat(String key, Object... args) {
		String result;
		try {
			result = String.format(get(key), args);
		} catch (Exception e) {
			return "[NULL]";
		}

		return result;
	}

	private static final List<BattleNext_I18nSection> sections = new ArrayList<>();
	public static BattleNext_I18nSection getInstance(String category, String keyPrefix) {
		if ("Misc".equals(category) && "BattleNext_ui_".equals(keyPrefix)) category = "UI";
		for (BattleNext_I18nSection section : sections) {
			if (section.category.contentEquals(category) && section.keyPrefix.contentEquals(keyPrefix)) {
				return section;
			}
		}

		return new BattleNext_I18nSection(category, keyPrefix);
	}

	public static String buildPercent(int value) {
		return value + "%";
	}

	public static String buildPercent(float value) {
		return buildFactor(value) + "%";
	}

	public static String buildFactor(float value) {
		String text = String.format("%.2f", value);
		if (text.equals("-0.00")) return "0";
		if (text.endsWith(".00")) return text.substring(0, text.length() - 3);
		if (text.endsWith("0")) return text.substring(0, text.length() - 1);
		return text;
	}

	public static String buildNumber(int value) {
		return String.valueOf(value);
	}

	public static String buildNumber(float value) {
		return buildNumber(Math.round(value));
	}
}
