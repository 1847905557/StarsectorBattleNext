package data.scripts.battlesnext.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_Match.MatchStage;
import data.scripts.battle.BattleNext_Match.MatchStatus;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestI18nSource;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestTextCatalog.TextKey;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.json.JSONObject;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_I18nSectionTest {

	private static BattleNext_TestI18nSource source;
	private static List<BattleNext_TestI18nSource.Read> reads;
	private SettingsAPI previousSettings;
	private Field settingsValues;
	private Object previousValues;
	private final AtomicReference<JSONObject> stored = new AtomicReference<>(new JSONObject());

	@BeforeEach
	void configureLanguage() throws Exception {
		previousSettings = Global.getSettings();
		settingsValues = BattleNext_Settings.class.getDeclaredField("values");
		settingsValues.setAccessible(true);
		previousValues = settingsValues.get(null);
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> switch (method.getName()) {
					case "getString" -> BattleNext_TestTextCatalog.text(arguments[0], arguments[1]);
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

	@BeforeAll
	static void inspectRuntime() throws Exception {
		source = new BattleNext_TestI18nSource(Path.of("src/data"));
		reads = source.reads();
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
	void everyRuntimeReadResolvesAndEveryCatalogEntryHasARuntimeConsumer() {
		Set<TextKey> consumed = new LinkedHashSet<>();
		for (BattleNext_TestI18nSource.Read read : reads) {
			assertTrue(BattleNext_TestTextCatalog.entries().containsKey(read.key()),
					read.key() + " at " + read.origin().getRange());
			consumed.add(read.key());
			consumed.add(new TextKey(read.key().category() + "_CN", read.key().id()));
		}
		assertEquals(BattleNext_TestTextCatalog.entries().keySet(), consumed);
	}

	@Test
	void sectionsUseTheirOwnExplicitCategoryAndPrefix() {
		Set<String> categories = new TreeSet<>();
		for (BattleNext_TestI18nSource.Section section : source.sections()) {
			assertEquals("BattleNext_" + section.category().toLowerCase(Locale.ROOT) + "_", section.prefix());
			categories.add(section.category());
		}
		assertEquals(new TreeSet<>(BattleNext_TestTextCatalog.entries().keySet().stream()
				.map(TextKey::category).filter(category -> !category.endsWith("_CN")).toList()), categories);
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
	void everyFormattedReadMatchesArgumentCountAndTypes() {

		for (BattleNext_TestI18nSource.Read read : reads) {
			if (read.arguments().isEmpty()) continue;
			for (String category : List.of(read.key().category(), read.key().category() + "_CN")) {
				String pattern = BattleNext_TestTextCatalog.text(category, read.key().id());
				List<String> conversions = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
						.matcher(pattern).results().map(match -> match.group(1))
						.filter(conversion -> !conversion.equals("%") && !conversion.equals("n")).toList();
				assertEquals(conversions.size(), read.arguments().size(), read.key().toString());
				Object[] values = new Object[read.arguments().size()];
				for (int index = 0; index < values.length; index++) {
					String type = read.arguments().get(index).calculateResolvedType().describe();
					values[index] = switch (type) {
						case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short",
								"java.lang.Integer", "java.lang.Long" -> 1;
						case "float", "double", "java.lang.Float", "java.lang.Double" -> 1.25f;
						case "boolean", "java.lang.Boolean" -> true;
						case "char", "java.lang.Character" -> 'A';
						case "java.lang.String" -> "value";
						default -> throw new AssertionError("Unsupported format argument type: " + type);
					};
				}
				assertDoesNotThrow(() -> String.format(Locale.ROOT, pattern, values), read.key().toString());
			}
		}
	}

	@ParameterizedTest
	@EnumSource(TournamentBracketType.class)
	void everyBracketUsesItsRuntimeEnumKey(TournamentBracketType type) {
		assertConsumed("Tournament", "bracket_" + type.name().toLowerCase(Locale.ROOT));
	}

	@ParameterizedTest
	@EnumSource(MatchStage.class)
	void everyStageUsesItsRuntimeEnumKey(MatchStage stage) {
		assertConsumed("Tournament", "stage_" + stage.name().toLowerCase(Locale.ROOT));
	}

	@ParameterizedTest
	@EnumSource(MatchStatus.class)
	void everyMatchStateUsesItsRuntimeEnumKey(MatchStatus status) {
		assertConsumed("Tournament", "match_status_" + status.name().toLowerCase(Locale.ROOT));
	}

	@ParameterizedTest
	@ValueSource(strings = {"alive", "destroyed", "retreated"})
	void everyReportMemberStateUsesItsRuntimeSwitchKey(String status) {
		assertConsumed("Report", "status_" + status);
	}

	@Test
	void semanticLabelsDistinguishCommandersOfficersParticipantsAndAutofire() {
		Map<String, String> labels = Map.of(
				"Fleet/commander_skills", "Commander skills",
				"Member/officer_skills", "Officer skills",
				"Member/officer_portrait", "Officer portrait",
				"Member/officer_personality", "Officer personality",
				"Tournament/invalid_participant_fleet", "Invalid participant fleet reference",
				"Variant/autofire_enabled", "Autofire enabled",
				"Variant/autofire_disabled", "Autofire disabled",
				"Management/page_pinned_variants", "Pinned variants");
		for (Map.Entry<String, String> entry : labels.entrySet()) {
			String[] parts = entry.getKey().split("/");
			String key = "BattleNext_" + parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1];
			assertEquals(entry.getValue(), BattleNext_TestTextCatalog.text(parts[0], key));
			assertConsumed(parts[0], parts[1]);
		}
	}

	@Test
	void actualLookupAndFormattingUseTheDeclaredDomain() {

		var previous = Global.getSettings();
		try {
			Global.setSettings((SettingsAPI) Proxy.newProxyInstance(
					getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
					(instance, method, arguments) -> method.getName().equals("getString")
							? BattleNext_TestTextCatalog.text(arguments[0], arguments[1]) : null));
			BattleNext_I18nSection fleet = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
			assertEquals("Commander skills", fleet.get("commander_skills"));
			assertEquals("Ships: 5", fleet.format("player_save_members", 5));
			assertEquals("Saved fleet: Example\nSaved to: tournament/id/fleet_data.json",
					fleet.format("player_save_saved", "Example", "tournament/id/fleet_data.json"));
			assertSame(fleet, BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_"));
		} finally {
			Global.setSettings(previous);
		}
	}

	private void assertConsumed(String category, String suffix) {
		TextKey key = new TextKey(category, "BattleNext_" + category.toLowerCase(Locale.ROOT) + "_" + suffix);
		assertTrue(reads.stream().anyMatch(read -> read.key().equals(key)), key.toString());
	}

	@Test
	void existingSectionsSwitchBothLookupFormsAndFormattingAfterEachCommit() {
		BattleNext_I18nSection fleet = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
		BattleNext_I18nSection label = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_commander_skills");
		for (boolean enabled : List.of(true, false, true)) {
			BattleNext_Settings.save(enabled, true, false, false, 500f, 1f, 3f, 50);
			String category = enabled ? "Fleet_CN" : "Fleet";
			assertSame(fleet, BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_"));
			assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_fleet_commander_skills"), fleet.get("commander_skills"));
			assertEquals(fleet.get("commander_skills"), label.get());
			assertEquals(fleet.get("commander_skills"), fleet.format("commander_skills"));
			assertEquals(String.format(BattleNext_TestTextCatalog.text(category, "BattleNext_fleet_player_save_saved"),
					"舰队 50%", "tournament/example/fleet_data.json"), fleet.format("player_save_saved", "舰队 50%", "tournament/example/fleet_data.json"));
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

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void everyCurrentCatalogEntryCanBeReadThroughTheSelectedLanguage(boolean enabled) {
		BattleNext_Settings.save(enabled, true, false, false, 500f, 1f, 3f, 50);
		for (TextKey key : BattleNext_TestTextCatalog.entries().keySet()) {
			if (key.category().endsWith("_CN")) continue;
			String category = enabled ? key.category() + "_CN" : key.category();
			assertEquals(BattleNext_TestTextCatalog.text(category, key.id()),
					BattleNext_I18nSection.getInstance(key.category(), key.id()).get(), key.toString());
		}
	}
}
