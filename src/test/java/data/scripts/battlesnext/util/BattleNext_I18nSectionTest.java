package data.scripts.battlesnext.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import java.lang.reflect.Proxy;

import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_Match.MatchStage;
import data.scripts.battle.BattleNext_Match.MatchStatus;
import data.test.fixture.BattleNext_TestI18nSource;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestTextCatalog.TextKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_I18nSectionTest {

	private static BattleNext_TestI18nSource source;
	private static List<BattleNext_TestI18nSource.Read> reads;

	@BeforeAll
	static void inspectRuntime() throws Exception {
		source = new BattleNext_TestI18nSource(Path.of("src/data"));
		reads = source.reads();
	}

	@Test
	void catalogDomainsKeysEncodingAndOrderingFollowTheTextContract() throws Exception {

		Map<String, Integer> expected = Map.ofEntries(
				Map.entry("Battle", 22), Map.entry("Combat", 4), Map.entry("Common", 30),
				Map.entry("Fleet", 25), Map.entry("Management", 10), Map.entry("Member", 8),
				Map.entry("Refit", 6), Map.entry("Report", 26), Map.entry("Rule", 18),
				Map.entry("Settings", 9), Map.entry("Tournament", 63), Map.entry("Variant", 38));
		Map<String, Integer> counts = new TreeMap<>();
		String raw = BattleNext_TestTextCatalog.source();
		List<String> ordered = new ArrayList<>();
		for (Map.Entry<TextKey, String> entry : BattleNext_TestTextCatalog.entries().entrySet()) {
			TextKey key = entry.getKey();
			String prefix = "BattleNext_" + key.category().toLowerCase(Locale.ROOT) + "_";
			assertTrue(key.id().startsWith(prefix), key.toString());
			assertTrue(key.id().substring(prefix.length()).matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"), key.toString());
			assertFalse(entry.getValue().isBlank(), key.toString());
			assertTrue(entry.getValue().codePoints().allMatch(value -> value < 128), key.toString());
			counts.merge(key.category(), 1, Integer::sum);
			ordered.add(key.id());
		}
		assertEquals(expected, counts);
		assertEquals(259, ordered.size());
		List<String> rawOrder = Pattern.compile("\"(BattleNext_[a-z0-9_]+)\"\\s*:")
				.matcher(raw).results().map(match -> match.group(1)).toList();
		assertEquals(ordered, rawOrder);
		List<String> domains = Pattern.compile("(?m)^\\t\"([A-Za-z]+)\"\\s*:")
				.matcher(raw).results().map(match -> match.group(1)).toList();
		assertEquals(counts.keySet().stream().toList(), domains);
	}

	@Test
	void everyRuntimeReadResolvesAndEveryCatalogEntryHasARuntimeConsumer() {
		Set<TextKey> consumed = new LinkedHashSet<>();
		for (BattleNext_TestI18nSource.Read read : reads) {
			assertTrue(BattleNext_TestTextCatalog.entries().containsKey(read.key()),
					read.key() + " at " + read.origin().getRange());
			consumed.add(read.key());
		}
		assertEquals(BattleNext_TestTextCatalog.entries().keySet(), consumed);
		assertTrue(reads.size() >= 259);
	}

	@Test
	void sectionsUseTheirOwnExplicitCategoryAndPrefix() {
		Set<String> categories = new TreeSet<>();
		for (BattleNext_TestI18nSource.Section section : source.sections()) {
			assertEquals("BattleNext_" + section.category().toLowerCase(Locale.ROOT) + "_", section.prefix());
			categories.add(section.category());
		}
		assertEquals(new TreeSet<>(BattleNext_TestTextCatalog.entries().keySet().stream()
				.map(TextKey::category).toList()), categories);
	}

	@Test
	void everyFormattedReadMatchesArgumentCountAndTypes() {

		for (BattleNext_TestI18nSource.Read read : reads) {
			if (read.arguments().isEmpty()) continue;
			String pattern = BattleNext_TestTextCatalog.text(read.key().category(), read.key().id());
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
	void semanticLabelsDistinguishCommandersCaptainsParticipantsAndAutofire() {
		Map<String, String> labels = Map.of(
				"Fleet/commander_skills", "Commander skills",
				"Member/captain_skills", "Captain skills",
				"Member/captain_portrait", "Captain portrait",
				"Member/captain_personality", "Captain personality",
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
}
