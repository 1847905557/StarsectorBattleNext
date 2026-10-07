package data.scripts.battlesnext.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestTextCatalog.TextKey;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

class BattleNext_I18nSectionTest {

	private SettingsAPI previousSettings;
	private Field settingsValues;
	private Object previousValues;
	private final List<TextKey> requested = new ArrayList<>();
	private final AtomicReference<JSONObject> stored = new AtomicReference<>(new JSONObject());

	@BeforeEach
	void configureLanguage() throws Exception {
		previousSettings = Global.getSettings();
		settingsValues = BattleNext_Settings.class.getDeclaredField("values");
		settingsValues.setAccessible(true);
		previousValues = settingsValues.get(null);
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> switch (method.getName()) {
					case "getString" -> {
						requested.add(new TextKey(arguments[0].toString(), arguments[1].toString()));
						yield BattleNext_TestTextCatalog.lookup(arguments[0], arguments[1]);
					}
					case "getBoolean" -> false;
					case "readJSONFromCommon" -> stored.get();
					case "writeJSONToCommon" -> { stored.set(new JSONObject(((JSONObject) arguments[1]).toString())); yield null; }
					default -> null;
				}));
		BattleNext_Settings.load();
	}

	@AfterEach
	void restoreLanguage() throws Exception {
		Global.setSettings(previousSettings);
		settingsValues.set(null, previousValues);
	}

	@Test
	void catalogDomainsKeysEncodingAndOrderingFollowTheTextContract() throws Exception {

		Set<String> categories = new TreeSet<>();
		String raw = BattleNext_TestTextCatalog.source();
		List<String> ordered = new ArrayList<>();
		for (Map.Entry<TextKey, String> entry : BattleNext_TestTextCatalog.entries().entrySet()) {
			TextKey key = entry.getKey();
			String category = key.category().endsWith("_CN")
					? key.category().substring(0, key.category().length() - 3) : key.category();
			String prefix = "BattleNext_" + category.toLowerCase(Locale.ROOT) + "_";
			assertTrue(key.id().startsWith(prefix), key.toString());
			assertTrue(key.id().substring(prefix.length()).matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"), key.toString());
			assertFalse(entry.getValue().isBlank(), key.toString());
			if (!key.category().endsWith("_CN")) {
				assertTrue(entry.getValue().codePoints().allMatch(value -> value < 128), key.toString());
			}
			categories.add(key.category());
			ordered.add(key.id());
		}
		List<String> rawOrder = Pattern.compile("\"(BattleNext_[a-z0-9_]+)\"\\s*:")
				.matcher(raw).results().map(match -> match.group(1)).toList();
		assertEquals(ordered, rawOrder);
		List<String> domains = Pattern.compile("(?m)^\\t\"([A-Za-z]+(?:_CN)?)\"\\s*:")
				.matcher(raw).results().map(match -> match.group(1)).toList();
		assertEquals(categories.stream().toList(), domains);
	}

	@Test
	void bilingualDomainsHaveIdenticalKeysAndFormatTokens() {
		Map<TextKey, String> entries = BattleNext_TestTextCatalog.entries();
		Pattern format = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");
		for (var entry : entries.entrySet()) {
			TextKey key = entry.getKey();
			String otherCategory = key.category().endsWith("_CN")
					? key.category().substring(0, key.category().length() - 3) : key.category() + "_CN";
			TextKey other = new TextKey(otherCategory, key.id());
			assertTrue(entries.containsKey(other), other.toString());
			assertEquals(format.matcher(entry.getValue()).results().map(match -> match.group()).toList(),
					format.matcher(entries.get(other)).results().map(match -> match.group()).toList(), key.toString());
		}
	}

	@Test
	void actualLookupAndFormattingUseTheDeclaredDomain() {

		var previous = Global.getSettings();
		try {
			Global.setSettings((SettingsAPI) Proxy.newProxyInstance(
					getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
					(instance, method, arguments) -> method.getName().equals("getString")
							? BattleNext_TestTextCatalog.lookup(arguments[0], arguments[1]) : null));
			BattleNext_I18nSection fleet = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
			assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_commander_skills"), fleet.get("commander_skills"));
			assertEquals(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_members"), 5), fleet.format("player_save_members", 5));
			assertEquals(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_saved"), "Example", "tournament/id/fleet_data.json"),
					fleet.format("player_save_saved", "Example", "tournament/id/fleet_data.json"));
			assertSame(fleet, BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_"));
		} finally {
			Global.setSettings(previous);
		}
	}

	@Test
	void existingSectionsSwitchBothLookupFormsAndFormattingAfterEachCommit() {
		BattleNext_I18nSection fleet = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
		BattleNext_I18nSection label = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_commander_skills");
		for (boolean enabled : List.of(true, false, true)) {
			BattleNext_Settings.save(enabled, true, false, false, 500f, 1f, 3f, 50);
			requested.clear();
			String category = "Fleet";
			assertSame(fleet, BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_"));
			assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_fleet_commander_skills"), fleet.get("commander_skills"));
			assertEquals(fleet.get("commander_skills"), label.get());
			assertEquals(fleet.get("commander_skills"), fleet.format("commander_skills"));
			assertEquals(String.format(BattleNext_TestTextCatalog.text(category, "BattleNext_fleet_player_save_saved"),
					"舰队 50%", "tournament/example/fleet_data.json"), fleet.format("player_save_saved", "舰队 50%", "tournament/example/fleet_data.json"));
			String expectedDomain = "Fleet";
			if (enabled) {
				expectedDomain += "_CN";
			}
			assertEquals(List.of(new TextKey(expectedDomain, "BattleNext_fleet_commander_skills"),
					new TextKey(expectedDomain, "BattleNext_fleet_commander_skills"),
					new TextKey(expectedDomain, "BattleNext_fleet_commander_skills"),
					new TextKey(expectedDomain, "BattleNext_fleet_commander_skills"),
					new TextKey(expectedDomain, "BattleNext_fleet_commander_skills"),
					new TextKey(expectedDomain, "BattleNext_fleet_player_save_saved")), requested);
		}
	}

	@Test
	void chineseLookupsKeepExistingFailureResultsAndSelectedCategory() {
		BattleNext_Settings.save(true, true, false, false, 500f, 1f, 3f, 50);
		List<TextKey> requested = new ArrayList<>();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			requested.add(new TextKey((String) arguments[0], (String) arguments[1]));
			if (arguments[1].equals("BattleNext_common_saved")) throw new IllegalArgumentException("Injected missing translation");
			return "%d";
		}));
		BattleNext_I18nSection common = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
		assertEquals("[NULL]", common.get("saved"));
		assertEquals("[NULL]", common.format("skill_effect_level", "invalid"));
		assertEquals(List.of(new TextKey("Common_CN", "BattleNext_common_saved"),
				new TextKey("Common_CN", "BattleNext_common_skill_effect_level")), requested);
	}

}
