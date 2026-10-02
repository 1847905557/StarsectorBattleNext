package data.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_TestStructureAuditTest {

	@Test
	void sourceAndTestFileCountsAndMirrorCountsAreExplicit() throws IOException {
		Path productionRoot = Path.of("src/data");
		Path testRoot = Path.of("src/test/java");
		List<Path> production = javaFiles(productionRoot);
		List<Path> tests = javaFiles(testRoot);
		long mirrored = tests.stream().filter(test -> production.contains(mirrorPath(test))).count();

		assertTrue(production.size() > tests.size());
		assertTrue(mirrored > 0);
		assertTrue(tests.size() > mirrored);
	}

	@Test
	void pinnedVariantMethodsAreSplitByDomain() throws IOException {
		List<String> files = List.of(
				"src/test/java/data/model/pinnedvariant/BattleNext_PinnedVariantStoreTest.java",
				"src/test/java/data/scripts/campaign/BattleNext_LunaPinnedVariantRefitButtonTest.java",
				"src/test/java/data/scripts/campaign/BattleNext_LunaPinnedVariantPanelTest.java",
				"src/test/java/data/scripts/ui/widget/BattleNext_VariantDetailsTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_PinnedVariantPageTest.java",
				"src/test/java/data/scripts/ui/editor/BattleNext_FleetEditorIntegrationTest.java",
				"src/test/java/data/scripts/ui/integration/BattleNext_UiManagerIntegrationTest.java",
				"src/test/java/data/scripts/ui/integration/BattleNext_PageLifecycleIntegrationTest.java",
				"src/test/java/data/scripts/ui/widget/BattleNext_TableWidgetTest.java");
		assertFalse(Files.exists(Path.of("src/test/java/data/scripts/campaign/BattleNext_PinnedVariantTest.java")));
		for (String file : files) {
			assertTrue(Files.exists(Path.of(file)), file);
		}
		List<String> fixtures = List.of(
				"src/test/java/data/test/fixture/BattleNext_TestGameFixture.java",
				"src/test/java/data/test/fixture/BattleNext_TestUiFixture.java",
				"src/test/java/data/test/fixture/BattleNext_TestVariantFixture.java",
				"src/test/java/data/test/fixture/BattleNext_TestPageFixture.java");
		assertFalse(Files.exists(Path.of("src/test/java/data/test/support/BattleNext_PinnedVariantTestSupport.java")));
		for (String file : fixtures) {
			String fixture = Files.readString(Path.of(file), StandardCharsets.UTF_8);
			assertFalse(fixture.contains("@Test"), file);
			assertFalse(fixture.contains("@ParameterizedTest"), file);
			assertFalse(fixture.matches("(?s).*^\\s*(?:public|protected)\\s+[^\\n(]+\\s+\\w+\\s*(?:;|=).*$"), file);
		}
	}

	@Test
	void integrationTestsUseExplicitIntegrationNames() throws IOException {
		List<String> expected = List.of(
				"src/test/java/data/scripts/ui/BattleNext_PageLayoutSoundIntegrationTest.java",
				"src/test/java/data/scripts/ui/BattleNext_PageLayoutMissingIconIntegrationTest.java",
				"src/test/java/data/scripts/ui/widget/provider/BattleNext_SourceProviderTooltipIntegrationTest.java",
				"src/test/java/data/order/BattleNext_OrderThreadIntegrationTest.java",
				"src/test/java/data/order/BattleNext_OrderScriptsIntegrationTest.java",
				"src/test/java/data/order/BattleNext_OrderFieldIntegrationTest.java",
				"src/test/java/data/model/quickbattle/io/BattleNext_QuickBattleLoadingIntegrationTest.java",
				"src/test/java/data/scripts/plugin/BattleNext_ScorekeeperReportIntegrationTest.java",
				"src/test/java/data/scripts/ai/rtt/BattleNext_RttManualSystemControlIntegrationTest.java",
				"src/test/java/data/scripts/ui/editor/BattleNext_FleetEditorIntegrationTest.java",
				"src/test/java/data/scripts/ui/integration/BattleNext_UiManagerIntegrationTest.java",
				"src/test/java/data/scripts/ui/integration/BattleNext_PageLifecycleIntegrationTest.java");
		for (String file : expected) {
			assertTrue(Files.exists(Path.of(file)), file);
		}
	}

	@Test
	void highRiskPublicEntrypointsHaveDedicatedTests() {
		List<String> tests = List.of(
				"src/test/java/data/scripts/ui/page/BattleNext_BattleSelectorPageTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_FleetLibraryPageTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_PinnedVariantPageTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_BattleReportPageTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_TournamentPageTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_RulesPageTest.java",
				"src/test/java/data/scripts/ui/page/BattleNext_SettingsPageTest.java",
				"src/test/java/data/scripts/ui/editor/BattleNext_FleetEditorTest.java",
				"src/test/java/data/scripts/ui/editor/BattleNext_RuleEditorTest.java",
				"src/test/java/data/scripts/ui/editor/BattleNext_TournamentEditorTest.java",
				"src/test/java/data/scripts/ui/editor/BattleNext_QuickBattleEditorTest.java",
				"src/test/java/data/scripts/ui/widget/BattleNext_ConfirmationDialogTest.java",
				"src/test/java/data/scripts/ui/widget/BattleNext_TableWidgetTest.java",
				"src/test/java/data/model/fleet/BattleNext_FleetStoreTest.java",
				"src/test/java/data/model/rule/BattleNext_RuleStoreTest.java",
				"src/test/java/data/model/tournament/BattleNext_TournamentStoreTest.java",
				"src/test/java/data/model/quickbattle/BattleNext_QuickBattleStoreTest.java",
				"src/test/java/data/model/report/BattleNext_BattleReportStoreTest.java",
				"src/test/java/data/model/pinnedvariant/BattleNext_PinnedVariantStoreTest.java",
				"src/test/java/data/model/fleet/io/BattleNext_FleetStandardLoaderTest.java",
				"src/test/java/data/model/fleet/io/BattleNext_FleetLegacyLoaderTest.java",
				"src/test/java/data/model/rule/io/BattleNext_RuleStandardLoaderTest.java",
				"src/test/java/data/model/tournament/io/BattleNext_TournamentStandardLoaderTest.java",
				"src/test/java/data/model/quickbattle/io/BattleNext_QuickBattleStandardLoaderTest.java",
				"src/test/java/data/model/quickbattle/io/BattleNext_QuickBattleLegacyLoaderTest.java",
				"src/test/java/data/model/report/io/BattleNext_BattleReportStandardLoaderTest.java",
				"src/test/java/data/model/pinnedvariant/io/BattleNext_PinnedVariantStandardLoaderTest.java");
		for (String test : tests) {
			assertTrue(Files.exists(Path.of(test)), test);
		}
	}

	private List<Path> javaFiles(Path root) throws IOException {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(path -> path.toString().endsWith(".java")).toList();
		}
	}

	private Path mirrorPath(Path test) {
		String relative = Path.of("src/test/java").relativize(test).toString();
		return Path.of("src", relative.replaceFirst("Test\\.java$", ".java"));
	}
}
