package data.scripts.util;

import com.fs.starfarer.api.Global;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BattleNext_I18nSection {
	private final String category;
	private final String keyPrefix;
	private static final List<BattleNext_I18nSection> sections = new ArrayList<>();
	public BattleNext_I18nSection(String category, String keyPrefix) { this.category = category; this.keyPrefix = Objects.requireNonNullElse(keyPrefix, ""); sections.add(this); }
	public BattleNext_I18nSection(String category) { this(category, null); }
	public String format(String key, @Nullable Object... args) { return args != null && args.length > 0 ? String.format(get(key), args) : get(key); }
	public String get() { return get(""); }
	public String get(String key) { try { return Global.getSettings().getString(category, keyPrefix + key); } catch (Exception exception) { return "[NULL]"; } }
	public static BattleNext_I18nSection getInstance(String category, String keyPrefix) { for (BattleNext_I18nSection section : sections) if (section.category.equals(category) && section.keyPrefix.equals(keyPrefix)) return section; return new BattleNext_I18nSection(category, keyPrefix); }
}
