package data.simulator;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.model.report.BattleNext_BattleReportModel;
import data.missions.BattleNext_MissionBattleMap;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.page.BattleNext_SettingsPage;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_DropdownField;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorWidgetTest extends BattleNext_TestFleetIoFixture {

	private static final String FIRST_ID = "first_" + "complete".repeat(40);

	@Test
	void quickRowsKeepOrderDisabledRowsAndRawContentWithReadonlyFieldsAndQualifiedActions() throws Exception {

		catalog();
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		sources.reloadCatalogs();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(900f, 500f, new BaseCustomUIPanelPlugin());
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		widget.attach(root);
		BattleNext_QuickBattleDisplay display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
		BattleNext_TableWidget table = (BattleNext_TableWidget) getField(display, "tableWidget");
		assertArrayEquals(new String[]{"BattleNext_battle_player_fleet_id_label", "BattleNext_battle_enemy_fleet_id_label",
				"BattleNext_battle_map_label", "BattleNext_battle_map_scale_label", "BattleNext_common_status", "BattleNext_common_actions"},
				(String[]) getField(table, "headers"));
		var entries = sources.getQuickBattleStore().getAll();
		assertEquals(List.of(FIRST_ID, FIRST_ID, FIRST_ID, "", FIRST_ID), entries.stream().map(entry -> entry.getPlayer()).toList());
		assertFalse(entries.get(1).isEnabled());
		assertEquals(entries.get(0).toCsvLine(), entries.get(4).toCsvLine());
		Map<Path, String> before = storedFiles();
		for (int index = 0; index < entries.size(); index++) {
			String action = "simulator:quick:" + entries.get(index).getId();
			assertEquals(List.of(true, true, false, false, true).get(index), buttons.get(action).get());
			assertEquals("BattleNext_battle_simulate", buttonLabels.get(action));
		}
		assertEquals(before, storedFiles());
		Map<List<String>, BattleNext_TextField> fields = (Map<List<String>, BattleNext_TextField>) getField(display, "fields");
		assertEquals("# note", fields.get(List.of(entries.get(3).getId(), "raw")).getText());
		for (BattleNext_TextField field : fields.values()) {
			assertFalse(field.isEditable());
			assertFalse(field.appendCharIfPossible('X', false));
		}
		assertEquals(FIRST_ID, fields.get(List.of(entries.get(0).getId(), "player")).getText());
		assertTrue(sources.quickBattleErrors(entries.get(2)).contains("BattleNext_battle_invalid_enemy_fleet"));
		assertEquals("BattleNext_battle_raw_row_status", ((LabelAPI) getField(display.getRows().get(3), "statusLabel")).getText());
		widget.close();
		assertTrue(children.get(root).isEmpty());
		assertFalse(router.getSession().hasInputState());
	}

	@ParameterizedTest
	@EnumSource(TournamentBracketType.class)
	void tournamentGraphsAndSimulationActionsDescribeEveryPhaseWithoutChangingTheActiveSource(TournamentBracketType bracket) throws Exception {

		catalog();
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
		for (String phase : List.of("new", "running", "completed")) {
			BattleNext_TournamentModel tournament = tournament(phase, bracket);
			if (!phase.equals("new")) {
				engine.start(tournament);
			}
			if (phase.equals("completed")) {
				while (!tournament.isCompleted()) {
					var match = engine.getNextMatch(tournament);
					engine.recordRound(tournament, match.getId(), match.getPlayer(), new BattleNext_BattleReportModel());
				}
			}
			tournaments.save(tournament);
		}
		tournaments.setActiveId("running");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		sources.reloadCatalogs();
		Map<Path, String> before = storedFiles();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 600f, new BaseCustomUIPanelPlugin());
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		widget.attach(root);
		for (String phase : List.of("new", "running", "completed")) {
			select(widget, phase);
			BattleNext_TournamentDisplay display = (BattleNext_TournamentDisplay) getField(widget, "tournamentDisplay");
			BattleNext_TournamentView chart = (BattleNext_TournamentView) getField(display, "chart");
			BattleNext_TournamentModel model = sources.getTournamentStore().get(phase);
			String json = model.toJson().toString();
			assertTrue(chart.errors().isEmpty(), chart.errors().toString());
			assertTrue(labels.contains("BattleNext_tournament_summary"));
			assertTrue(labels.contains("BattleNext_tournament_participant_fleets"));
			assertEquals("BattleNext_battle_simulate_tournament", buttonLabels.get("simulator:tournament:" + phase));
			assertTrue(buttons.get("simulator:tournament:" + phase).get());
			chart.zoomAt(60f, 60f, 3);
			chart.panBy(12f, 7f);
			BattleNext_TournamentDisplay.ViewState state = display.captureState();
			assertEquals(json, model.toJson().toString());
			select(widget, "");
			select(widget, phase);
			display = (BattleNext_TournamentDisplay) getField(widget, "tournamentDisplay");
			assertEquals(state, display.captureState());
		}
		assertEquals("running", sources.getTournamentStore().getActiveId());
		assertEquals(before, storedFiles());
		widget.close();
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void reloadReadsSavedChangesWhileSourceSwitchAndRebuildPreserveCurrentCatalogAndBrowseState() throws Exception {

		catalog();
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		tournaments.save(tournament("cup", TournamentBracketType.SINGLE_ELIMINATION));
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		sources.reloadCatalogs();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(900f, 200f, new BaseCustomUIPanelPlugin());
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		widget.attach(root);
		int reads = dataReads.size();
		BattleNext_QuickBattleDisplay display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
		Map<List<String>, BattleNext_TextField> fields = (Map<List<String>, BattleNext_TextField>) getField(display, "fields");
		BattleNext_TextField field = fields.get(List.of(sources.getQuickBattleStore().getAll().get(0).getId(), "player"));
		assertTrue(field.captureEditState().scroll() > 0f);
		BattleNext_QuickBattleDisplay.ViewState state = display.captureState();
		select(widget, "cup");
		select(widget, "");
		display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
		assertEquals(state, display.captureState());
		widget.refreshContent();
		display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
		assertEquals(state, display.captureState());
		assertEquals(reads, dataReads.size());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nsecond,second,8,2\n");
		widget.refreshContent();
		assertEquals(FIRST_ID, sources.getQuickBattleStore().getAll().get(0).getPlayer());
		widget.close();
		sources.reloadCatalogs();
		assertTrue(dataReads.size() > reads);
		widget.attach(root);
		display = (BattleNext_QuickBattleDisplay) getField(widget, "quickBattleDisplay");
		fields = (Map<List<String>, BattleNext_TextField>) getField(display, "fields");
		assertEquals("second", fields.get(List.of(sources.getQuickBattleStore().getAll().get(0).getId(), "player")).getText());
		select(widget, "cup");
		widget.close();
		tournaments.reload();
		tournaments.delete("cup");
		sources.reloadCatalogs();
		widget.attach(root);
		assertEquals("cup", browser.getSelectedId());
		assertTrue(labels.stream().anyMatch(label -> label.contains("BattleNext_tournament_missing_detail")));
		widget.close();
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void managementWindowPreservesSettingsDraftLanguageAndSelectedSimulatorSourceUntilClose() throws Exception {

		catalog();
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		tournaments.save(tournament("cup", TournamentBracketType.SINGLE_ELIMINATION));
		tournaments.save(tournament("active", TournamentBracketType.SINGLE_ELIMINATION));
		tournaments.setActiveId("active");
		AtomicReference<JSONObject> saved = new AtomicReference<>(new JSONObject().put("cnText", false));
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.text(arguments[0], arguments[1]);
			if (method.getName().equals("readJSONFromCommon")) return saved.get();
			if (method.getName().equals("writeJSONToCommon")) {
				saved.set(new JSONObject(((JSONObject) arguments[1]).toString()));
				return null;
			}
			try {
				return method.invoke(delegate, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		manager.open();
		Object settings = buttonIds.get("SETTINGS");
		Object fleets = buttonIds.get("FLEETS");
		manager.buttonPressed(settings);
		BattleNext_SettingsPage page = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		BattleNext_TextField field = (BattleNext_TextField) getField(getField(page, "editor"), "defaultTimeMultField");
		field.setText("pending");
		BattleNext_SimulatorWidget widget = (BattleNext_SimulatorWidget) getField(page, "simulatorWidget");
		manager.advance(0f);
		assertNull(getField(widget, "statusContent"));
		assertNull(getField(widget, "statusLabel"));
		manager.buttonPressed("simulator_enable");
		assertNull(getField(widget, "statusLabel"));
		manager.buttonPressed("action_cancel");
		manager.advance(0f);
		assertNull(getField(widget, "statusLabel"));
		manager.buttonPressed("simulator_enable");
		manager.buttonPressed("action_confirm");
		manager.advance(0f);
		assertNotNull(getField(widget, "statusLabel"));
		select(widget, "cup");
		BattleNext_SimulatorBrowser browser = (BattleNext_SimulatorBrowser) getField(page, "simulatorBrowser");
		assertEquals("cup", browser.getSelectedId());
		assertSame(field, getField(getField(page, "editor"), "defaultTimeMultField"));
		assertEquals("pending", field.getText());
		manager.buttonPressed("settings_save");
		assertTrue(page.hasUnsavedChanges());
		assertEquals("cup", browser.getSelectedId());
		field.setText("1.5");
		((ButtonAPI) getField(getField(page, "editor"), "cnTextCheckbox")).setChecked(true);
		manager.buttonPressed("settings_save");
		assertTrue(saved.get().getBoolean("cnText"));
		assertFalse(page.hasUnsavedChanges());
		assertEquals("模拟赛事", buttonLabels.get("simulator:tournament:cup"));
		manager.buttonPressed(fleets);
		manager.buttonPressed(settings);
		assertSame(page, getField(manager, "settingsPage"));
		assertEquals("cup", browser.getSelectedId());
		assertEquals("1.5", ((BattleNext_TextField) getField(getField(page, "editor"), "defaultTimeMultField")).getText());
		assertEquals("模拟赛事", buttonLabels.get("simulator:tournament:cup"));
		tournaments.reload();
		assertEquals("active", tournaments.getActiveId());
		manager.buttonPressed("close");
		manager.open();
		manager.buttonPressed(settings);
		BattleNext_SettingsPage reopened = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		assertNotSame(page, reopened);
		assertEquals("", ((BattleNext_SimulatorBrowser) getField(reopened, "simulatorBrowser")).getSelectedId());
		manager.advance(0f);
		assertNull(getField(getField(reopened, "simulatorWidget"), "statusLabel"));
		manager.buttonPressed("close");
		assertFalse(((BattleNext_UiInputRouter) getField(manager, "inputRouter")).getSession().hasInputState());
	}

	@Test
	void catalogFailureKeepsTheSelectedSourceAndShowsItsDiagnosticWithAnUnavailableAction() throws Exception {
		catalog();
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		tournaments.save(tournament("cup", TournamentBracketType.SINGLE_ELIMINATION));
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
		sources.reloadCatalogs();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(900f, 600f, new BaseCustomUIPanelPlugin());
		BattleNext_SimulatorWidget widget = new BattleNext_SimulatorWidget(router, root, () -> true, browser, sources);
		widget.attach(root);
		select(widget, "cup");
		widget.close();
		write(sources.getTournamentStore().get("cup").getSourcePath(), "{ invalid json");
		sources.reloadCatalogs();
		widget.attach(root);
		assertEquals("cup", browser.getSelectedId());
		assertFalse(sources.getTournamentStore().isLoaded());
		assertFalse(buttons.get("simulator:tournament:cup").get());
		assertTrue(labels.stream().anyMatch(label -> label.contains(sources.getTournamentStore().getLoadError())));
		widget.close();
		assertTrue(router.isEmpty());
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void tournamentQualificationReportsRulesAssemblyReferencesConfigurationAndEvaluationFailures() throws Exception {

		catalog();
		standard("broken", "broken", "unknown_variant");
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		Object entries = getField(registry, "entries");
		Object factory = getField(registry, "scriptFactory");
		try {
			registry.load(new JSONArray().put(new JSONObject().put("id", "limit").put("type", "number")
					.put("script", "fixture").put("desc", "Fleet limit")),
					name -> (BattleNext_RestrictionScript) (fleet, restriction) -> {
						if (restriction.getValue() == 2) throw new IllegalStateException("Evaluation failed");
						return List.of("Fleet violates limit");
					});
			BattleNext_RuleStore rules = new BattleNext_RuleStore();
			rules.reload();
			for (String id : List.of("violates", "throws", "missing_script")) {
				BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id.equals("missing_script") ? "unknown" : "limit", "number");
				restriction.setEnabled(true);
				restriction.setValue(id.equals("throws") ? 2 : 1);
				BattleNext_RuleModel rule = new BattleNext_RuleModel(id, id);
				rule.setRestrictions(List.of(restriction));
				rules.save(rule);
			}
			BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
			BattleNext_SimulatorBrowser browser = new BattleNext_SimulatorBrowser();
			sources.reloadCatalogs();
			BattleNext_TournamentModel model = tournament("cup", TournamentBracketType.SINGLE_ELIMINATION);
			assertTrue(sources.tournamentErrors(model).isEmpty());
			for (String id : List.of("violates", "throws", "missing_script", "missing_rule")) {
				model.setRuleId(id);
				String reason = switch (id) {
					case "violates" -> "Fleet violates limit";
					case "throws" -> "Evaluation failed";
					case "missing_script" -> "Missing restriction script: unknown";
					default -> "Missing rule: missing_rule";
				};
				assertTrue(sources.tournamentErrors(model).stream().anyMatch(error -> error.contains(reason)));
				BattleNext_BattleValidation.Result result = sources.getValidation().tournamentResult(model, sources.getRuleStore());
				BattleNext_BattleValidation.FailureKind kind = id.equals("violates") ? BattleNext_BattleValidation.FailureKind.RULE
						: id.equals("throws") ? BattleNext_BattleValidation.FailureKind.EVALUATION : BattleNext_BattleValidation.FailureKind.REFERENCE;
				assertTrue(result.failures().stream().allMatch(failure -> failure.kind() == kind));
				assertEquals(!id.equals("violates"), result.isBlocking());
			}
			model.setRuleId("");
			model.setFleetIds(List.of("broken", "missing"));
			List<String> reasons = sources.tournamentErrors(model);
			assertTrue(reasons.stream().anyMatch(error -> error.contains("unknown_variant")));
			assertTrue(reasons.contains("Missing fleet: missing"));
			model.setFleetIds(List.of(FIRST_ID, FIRST_ID));
			assertTrue(sources.tournamentErrors(model).contains("Tournament fleets must be unique"));
			model.setFleetIds(List.of(FIRST_ID, "second"));
			model.setMapId(BattleNext_MissionBattleMap.LAST_MAP_ID + 1);
			assertTrue(sources.tournamentErrors(model).contains("BattleNext_battle_invalid_map"));
		} finally {
			Field entryField = registry.getClass().getDeclaredField("entries");
			entryField.setAccessible(true);
			entryField.set(registry, entries);
			Field factoryField = registry.getClass().getDeclaredField("scriptFactory");
			factoryField.setAccessible(true);
			factoryField.set(registry, factory);
		}
	}

	private void catalog() throws Exception {
		standard("first", FIRST_ID, "parent_variant");
		standard("second", "second", "parent_variant");
		String row = FIRST_ID + ",second,-1,1";
		write("tournament/quick_battle.csv", "player,enemy,map,size\n" + row + "\n# " + FIRST_ID + ",second,4,2\n"
				+ FIRST_ID + ",missing,1,1\n# note\n" + row + "\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n");
	}

	private BattleNext_TournamentModel tournament(String id, TournamentBracketType bracket) {
		BattleNext_TournamentModel model = new BattleNext_TournamentModel(id, "Cup " + id, bracket, 1, 2);
		model.setFleetIds(List.of(FIRST_ID, "second"));
		return model;
	}

	private void select(BattleNext_SimulatorWidget widget, String id) throws Exception {
		BattleNext_BattleSourceField source = (BattleNext_BattleSourceField) getField(widget, "sourceField");
		BattleNext_DropdownField dropdown = (BattleNext_DropdownField) getField(source, "dropdown");
		plugins.get((CustomPanelAPI) getField(dropdown, "row")).buttonPressed(buttonIds.get("TOGGLE"));
		for (Object choice : (List<?>) getField(dropdown, "options")) {
			if (!id.equals(getField(choice, "id"))) continue;
			plugins.get((CustomPanelAPI) getField(dropdown, "popup")).buttonPressed(choice);
			assertFalse(dropdown.isOpen());
			return;
		}
		throw new AssertionError("Source option missing: " + id);
	}

	private Map<Path, String> storedFiles() throws Exception {
		Map<Path, String> result = new LinkedHashMap<>();
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				result.put(path, Files.readString(path, StandardCharsets.UTF_8));
			}
		}
		return result;
	}
}
