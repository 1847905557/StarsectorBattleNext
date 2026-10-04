package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestUiFixture;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_FleetTooltipIntegrationTest extends BattleNext_TestUiFixture {

	private final RecordingAppender appender = new RecordingAppender();
	private BattleNext_FleetModel fleet;
	private Variant parent;
	private Variant module;
	private int personalityReads;
	private boolean failPersonalities;
	private String failPhase = "";
	private Logger logger;
	private Level previousLevel;
	private boolean previousAdditivity;

	@BeforeEach
	void configureFleetTooltip() throws Exception {

		parent = variant("root", spec("wolf", "Wolf", 5.5f, 4, List.of("built_in_wing"), Set.of("left", "right")));
		parent.getJson().put("wings", new JSONArray().put("built_in_wing").put("wing").put("").put("wing"));
		module = variant("module", spec("module_hull", "Module", 99f, 99, List.of(), Set.of("nested")));
		module.getApi().setWingId(0, "module_wing");
		Variant nested = variant("nested", spec("nested_hull", "Nested", 99f, 99, List.of(), Set.of()));
		nested.getApi().setWingId(0, "nested_wing");
		nested.getApi().setWingId(1, "nested_wing");
		module.getApi().setModuleVariant("nested", nested.getApi());
		parent.getApi().setModuleVariant("left", module.getApi());
		parent.getApi().setModuleVariant("right", module.getApi());
		registeredVariants.put("root", controlled(parent.getApi()));
		fleet = new BattleNext_FleetModel("fleet", "Fleet 50%", List.of(), List.of(), List.of());
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.text(args[0], args[1]);
			if (method.getName().equals("getMergedSpreadsheetData")) {
				assertEquals("data/characters/personalities.csv", args[1]);
				personalityReads++;
				if (failPersonalities) throw new IllegalArgumentException("Personality CSV read failed");
				return new JSONArray().put(new JSONObject().put("id", "steady").put("name", "Steady"))
						.put(new JSONObject().put("id", "aggressive").put("name", "Aggressive"));
			}
			if (method.getName().equals("createFleetMember") || method.getName().equals("createPerson")) {
				throw new AssertionError("Preview reads definitions and variants directly");
			}
			try {
				return method.invoke(previous, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Logger.fields();
		logger = Global.getLogger(BattleNext_Logger.class);
		previousLevel = logger.getLevel();
		previousAdditivity = logger.getAdditivity();
		logger.setLevel(Level.INFO);
		logger.setAdditivity(false);
		logger.addAppender(appender);
	}

	@AfterEach
	void restoreLogging() {
		logger.removeAppender(appender);
		logger.setLevel(previousLevel);
		logger.setAdditivity(previousAdditivity);
	}

	@Test
	void duplicateMembersCountBaseCostsOnceEachAndAllNestedWingsPerInstance() {

		fleet.setMembers(List.of(member("root", List.of(), "aggressive"), member("root", List.of("one"), "")));
		TooltipMakerAPI tooltip = render();
		assertEquals(List.of("Fleet 50%", "fleet", "Ships: 2 - Fighter wings: 18", "Base DP: 11 - Base FP: 8",
				"Fleet members", "Wolf - No officer - Steady", "Wolf - Level 1 - Steady"), values(tooltip));
		assertEquals(1, personalityReads);
		assertEquals(2, parent.getApi().getStationModules().size());
		assertEquals(5.5f, parent.getApi().getHullSpec().getSuppliesToRecover());
		assertTrue(previewLogs().isEmpty());
	}

	@Test
	void officerSkillIdentityAndEliteDuplicatesDetermineTextAndOnlyEliteGetsGreen() {

		fleet.setMembers(List.of(member("root", List.of("a", "a", "b", "b", "c", "c", "d", "e"), "steady"),
				member("root", List.of("a", "b", "c"), "aggressive"),
				member("root", List.of(), "aggressive")));
		TooltipMakerAPI tooltip = render();
		assertEquals(List.of("Wolf - Level 5 (3) - Steady", "Wolf - Level 3 - Aggressive", "Wolf - No officer - Steady"),
				values(tooltip).subList(5, 8));
		LabelAPI elite = textLabels(tooltip).get(5);
		assertEquals(List.of("Wolf", "Level 5", "3", "Steady"), labelHighlights.get(elite.getPosition()));
		assertEquals(List.of(BattleNext_UiTheme.TEXT, BattleNext_UiTheme.TEXT,
				Misc.getPositiveHighlightColor(), BattleNext_UiTheme.TEXT), labelHighlightColors.get(elite.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(elite.getPosition()));
		for (int index : List.of(6, 7)) {
			assertTrue(labelHighlights.get(textLabels(tooltip).get(index).getPosition()).isEmpty());
		}
		assertEquals(1, personalityReads);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void nativeLayoutKeepsHeaderCompactSummaryMembersAndBottomInset(boolean deferred) {

		deferTooltipWidth = deferred;
		fleet.setMembers(List.of(member("root", List.of(), ""), member("root", List.of("a", "a"), "steady")));
		TooltipMakerAPI tooltip = render();
		List<LabelAPI> text = textLabels(tooltip);
		for (int index = 0; index < text.size(); index++) {
			var position = text.get(index).getPosition();
			assertEquals(index == 0 ? "small-orbitron" : index == 1 ? "small-insignia" : "default", labelFonts.get(position));
			assertEquals(index == 0 || index == 4 ? BattleNext_UiTheme.ACCENT
					: index == 1 ? BattleNext_UiTheme.MUTED : BattleNext_UiTheme.TEXT, labelColors.get(position));
			if (index == 0) continue;
			var previous = text.get(index - 1).getPosition();
			assertEquals(index == 1 ? 5f : index == 3 || index > 4 ? 2f : 8f,
					previous.getY() - position.getY() - position.getHeight());
		}
		var components = tooltips.get(tooltip).getComponents();
		assertEquals(8f, components.get(components.size() - 1).getPosition().getHeight());
	}

	@Test
	void localVariantWinsOverRegisteredVariantAndUsesTheCurrentFleetSnapshot() throws Exception {

		fleet.setMembers(List.of(member("root", List.of(), "")));
		var localHull = spec("local", "Local hull", 7.25f, 9, List.of(), Set.of());
		Variant local = variant("root", localHull);
		local.getApi().setWingId(0, "local_wing");
		fleet.putVariant(BattleNext_VariantBuilder.fromVariant(local.getApi()));
		registeredVariants.remove("root");
		assertEquals(List.of("Fleet 50%", "fleet", "Ships: 1 - Fighter wings: 1", "Base DP: 7.25 - Base FP: 9",
				"Fleet members", "Local hull - No officer - Steady"), values(render()));
		assertTrue(previewLogs().isEmpty());
	}

	@Test
	void emptyFleetProducesZeroTotalsWithTheNormalMemberHeading() {
		assertEquals(List.of("Fleet 50%", "fleet", "Ships: 0 - Fighter wings: 0", "Base DP: 0 - Base FP: 0",
				"Fleet members"), values(render()));
		assertEquals(0, personalityReads);
	}

	@Test
	void missingMemberKeepsItsOriginalPositionAndMarksOnlyIncompleteTotals() throws Exception {

		fleet.setMembers(List.of(member("root", List.of(), ""), member("missing", List.of("a"), "steady"),
				member("root", List.of(), "")));
		List<String> text = values(render());
		assertEquals("Ships: 3 - Fighter wings: 18 (Partial)", text.get(2));
		assertEquals("Base DP: 11 (Partial) - Base FP: 8 (Partial)", text.get(3));
		assertEquals(List.of("Wolf - No officer - Steady", "Unavailable: missing - Level 1 - Steady",
				"Wolf - No officer - Steady"), text.subList(5, 8));
		assertTrue(text.get(8).contains("missing"));
		assertEquals(1, previewLogs().size());
		assertEquals("variant", previewLogs().get(0).getJSONObject("fields").optString("phase"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"base_dp", "base_fp", "wings", "modules"})
	void queryFailuresRetainIndependentStatisticsAndLogCompleteContext(String phase) throws Exception {

		failPhase = phase;
		fleet.setMembers(List.of(member("root", List.of(), "")));
		List<String> text = values(render());
		assertEquals(phase.equals("wings") ? "Ships: 1 - Fighter wings: 6 (Partial)"
				: phase.equals("modules") ? "Ships: 1 - Fighter wings: 3 (Partial)"
				: "Ships: 1 - Fighter wings: 9", text.get(2));
		assertEquals(phase.equals("base_dp") ? "Base DP: 0 (Partial) - Base FP: 4"
				: phase.equals("base_fp") ? "Base DP: 5.5 - Base FP: 0 (Partial)"
				: "Base DP: 5.5 - Base FP: 4", text.get(3));
		assertEquals("Wolf - No officer - Steady", text.get(5));
		assertEquals(1, previewLogs().size());
		JSONObject fields = previewLogs().get(0).getJSONObject("fields");
		assertEquals("fleet", fields.getString("fleet_id"));
		assertEquals("root", fields.getString("variant_id"));
		assertEquals(0, fields.getInt("member_index"));
		assertEquals("", fields.getString("module_path"));
		assertEquals(phase, fields.getString("phase"));
	}

	@Test
	void missingModuleKeepsSiblingWingsAndFullBaseCosts() throws Exception {
		parent.getModules().put("left", null);
		fleet.setMembers(List.of(member("root", List.of(), "")));
		var text = values(render());
		assertEquals("Ships: 1 - Fighter wings: 6 (Partial)", text.get(2));
		assertEquals("Base DP: 5.5 - Base FP: 4", text.get(3));
		assertEquals("left", previewLogs().get(0).getJSONObject("fields").getString("module_path"));
	}

	@Test
	void cyclicModulesBecomePartialWithoutCountingTheCycleAgain() throws Exception {
		module.getApi().setModuleVariant("nested", parent.getApi());
		registeredVariants.put("root", parent.getApi());
		fleet.setMembers(List.of(member("root", List.of(), "")));
		assertEquals("Ships: 1 - Fighter wings: 5 (Partial)", values(render()).get(2));
		assertEquals(List.of("left/nested", "right/nested"), previewLogs().stream()
				.map(log -> log.optJSONObject("fields").optString("module_path")).toList());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void personalityFailuresKeepRawIdsAndLeaveCostsComplete(boolean loadFailure) {

		failPersonalities = loadFailure;
		fleet.setMembers(List.of(member("root", List.of("a"), "unknown_personality")));
		List<String> text = values(render());
		assertEquals("Ships: 1 - Fighter wings: 9", text.get(2));
		assertEquals("Base DP: 5.5 - Base FP: 4", text.get(3));
		assertEquals("Wolf - Level 1 - unknown_personality", text.get(5));
		assertEquals(loadFailure ? 2 : 1, previewLogs().size());
		assertEquals(1, personalityReads);
	}

	@Test
	void longRawNamesAndPercentCharactersRemainCompleteAcrossEliteFormatting() {

		String hullName = "Hull 50% %s " + "Long name ".repeat(20);
		hulls.put("special", spec("special", hullName, 1f, 1, List.of(), Set.of()));
		ShipVariantAPI special = proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
			case "getHullSpec" -> hulls.get("special");
			case "getFittedWings" -> List.of();
			case "getStationModules" -> Map.of();
			default -> null;
		});
		registeredVariants.put("special", special);
		fleet.setMembers(List.of(member("special", List.of("a", "a"), "steady")));
		assertEquals(hullName + " - Level 1 (1) - Steady", values(render()).get(5));
	}

	@Test
	void repositoryUiTextUsesTheApprovedHyphenSeparator() throws Exception {

		List<Path> files = new ArrayList<>();
		for (Path root : List.of(Path.of("src/data"), Path.of("data"))) {
			try (Stream<Path> paths = Files.walk(root)) {
				files.addAll(paths.filter(Files::isRegularFile)
						.filter(path -> Set.of("java", "json", "csv", "txt").contains(
								path.toString().substring(path.toString().lastIndexOf('.') + 1))).toList());
			}
		}
		for (Path file : files) {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			assertTrue(text.codePoints().noneMatch(value -> value == 0x00b7 || value == 0x2022), file.toString());
		}
	}

	private TooltipMakerAPI render() {
		BattleNext_FleetStore store = new BattleNext_FleetStore() {
			@Override
			public BattleNext_FleetModel get(String id) {
				return fleet.getId().equals(id) ? fleet : null;
			}
		};
		TooltipMakerAPI tooltip = tooltip(360f, 0f, false);
		new BattleNext_FleetSourceProvider(store).createTooltip(tooltip, new OptionChoice(fleet.getId(), fleet.getName()));
		return tooltip;
	}

	private List<LabelAPI> textLabels(TooltipMakerAPI tooltip) {
		return tooltips.get(tooltip).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
	}

	private List<String> values(TooltipMakerAPI tooltip) {
		return textLabels(tooltip).stream().map(LabelAPI::getText).toList();
	}

	private List<JSONObject> previewLogs() {
		return appender.events.stream().filter(event -> event.getLevel() == Level.WARN)
				.map(event -> {
					try {
						return new JSONObject(event.getRenderedMessage());
					} catch (Exception exception) {
						throw new AssertionError(exception);
					}
				}).filter(log -> log.optString("source").equals(BattleNext_FleetSourceProvider.class.getName())).toList();
	}

	private BattleNext_MemberModel member(String variantId, List<String> skills, String personality) {
		return new BattleNext_MemberModel(variantId, "Ship name", personality, skills, "", "", List.of());
	}

	private ShipHullSpecAPI spec(String id, String name, float dp, int fp, List<String> builtWings, Set<String> slots) {

		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> id;
			case "getHullName" -> name;
			case "getSuppliesToRecover" -> {
				if (id.equals("wolf") && failPhase.equals("base_dp")) throw new IllegalStateException("DP read failed");
				yield dp;
			}
			case "getFleetPoints" -> {
				if (id.equals("wolf") && failPhase.equals("base_fp")) throw new IllegalStateException("FP read failed");
				yield fp;
			}
			case "getBuiltInWings" -> builtWings;
			case "getWeaponSlot" -> proxy(WeaponSlotAPI.class, (key, values) -> key.equals("isStationModule") && slots.contains(args[0]));
			default -> null;
		});
		hulls.put(id, hull);
		return hull;
	}

	private ShipVariantAPI controlled(ShipVariantAPI variant) {
		return (ShipVariantAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipVariantAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getFittedWings") && failPhase.equals("wings")) throw new IllegalStateException("Wings read failed");
			if (method.getName().equals("getStationModules") && failPhase.equals("modules")) throw new IllegalStateException("Modules read failed");
			try {
				return method.invoke(variant, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		});
	}

	private static class RecordingAppender extends AppenderSkeleton {
		private final List<LoggingEvent> events = new ArrayList<>();

		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
		}

		@Override
		public void close() {

		}

		@Override
		public boolean requiresLayout() {
			return false;
		}
	}
}
