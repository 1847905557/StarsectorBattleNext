package data.scripts.tournament;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.page.BattleNext_BattleSelectorPage;
import data.scripts.ui.page.BattleNext_TournamentPage;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_DropdownField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import data.scripts.ui.widget.view.BattleNext_TournamentTemplate;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class BattleNext_StartedTournamentUiTest extends BattleNext_TestFleetIoFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void templateCreationAndInstanceActivationFollowSelectionAndPreserveBrowseState(boolean cnText) throws Exception {
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Settings.save(cnText, true, false, false, 500f, 1f, 3f, 50);
		quickBattles("alpha,beta,0,1\n");
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		sources.getTournamentStore().save(configuration);
		String original = configuration.toJson().toString();
		CustomPanelAPI root = panel(1200f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_BattleSelectorPage selector = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		selector.attach(root);
		selector.selectSource("cup");
		selector.refreshContent();
		assertEquals(List.of("tournament:create", "tournament:check"), actionIds(selector));
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		selector.buttonPressed("tournament:active");
		instances.reload();
		assertTrue(instances.getActiveId().isEmpty());
		BattleNext_BattleSourceField field = (BattleNext_BattleSourceField) getField(selector, "instanceField");
		assertEquals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_tournament_instance"), ((LabelAPI) getField(field, "label")).getText());
		BattleNext_TournamentDisplay templateDisplay = (BattleNext_TournamentDisplay) getField(selector, "tournamentDisplay");
		assertTrue(templateDisplay.isEditable());
		selector.buttonPressed("tournament:create");
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_minimum_fleets"), 2), selector.getStatusText());
		instances.reload();
		assertTrue(instances.getAll().isEmpty());
		var templates = (Map<String, BattleNext_TournamentTemplate>) getField(selector, "templates");
		templates.get("cup").select(0, "alpha");
		templates.get("cup").select(1, "alpha");
		selector.buttonPressed("tournament:create");
		assertEquals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_duplicate_fleets"), selector.getStatusText());
		instances.reload();
		assertTrue(instances.getAll().isEmpty());
		assertEquals(List.of("alpha", "alpha"), templates.get("cup").getFleetIds());
		templates.get("cup").select(1, "beta");
		selector.refreshContent();
		templateDisplay = (BattleNext_TournamentDisplay) getField(selector, "tournamentDisplay");
		BattleNext_TournamentView templateChart = (BattleNext_TournamentView) getField(templateDisplay, "chart");
		templateChart.zoomAt(90f, 90f, 2);
		templateChart.panBy(8f, 12f);
		BattleNext_TournamentDisplay.ViewState templateState = templateDisplay.captureState();
		selector.buttonPressed("tournament:create");
		String first = (String) getField(selector, "selectedInstanceId");
		assertEquals(List.of("tournament:active"), actionIds(selector));
		instances.reload();
		assertTrue(instances.getActiveId().isEmpty());
		assertEquals("cup", instances.get(first).getSourceTournamentId());
		assertEquals(List.of("alpha", "beta"), instances.get(first).getFleetIds());
		assertFalse(((BattleNext_TournamentDisplay) getField(selector, "tournamentDisplay")).isEditable());
		selector.buttonPressed("tournament:create");
		instances.reload();
		assertEquals(List.of(first), instances.getAll().stream().map(BattleNext_StartedTournament::getId).toList());
		selector.buttonPressed("tournament:active");
		instances.reload();
		assertEquals(first, instances.getActiveId());
		selectInstance(selector, "");
		assertEquals(List.of("tournament:create", "tournament:check"), actionIds(selector));
		assertEquals(templateState, ((BattleNext_TournamentDisplay) getField(selector, "tournamentDisplay")).captureState());
		selector.buttonPressed("tournament:create");
		String second = (String) getField(selector, "selectedInstanceId");
		assertNotEquals(first, second);
		instances.reload();
		assertEquals(first, instances.getActiveId());
		assertEquals(List.of("tournament:active"), actionIds(selector));
		field = (BattleNext_BattleSourceField) getField(selector, "instanceField");
		BattleNext_DropdownField dropdown = (BattleNext_DropdownField) getField(field, "dropdown");
		List<?> choices = (List<?>) getField(dropdown, "options");
		assertEquals("", getField(choices.get(0), "id"));
		assertEquals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_tournament_base_template"), getField(choices.get(0), "label"));
		assertEquals(instances.getForSource("cup", BattleNext_StartedTournament.ExecutionKind.MANUAL).stream()
				.map(BattleNext_StartedTournament::getId).toList(), choiceIds(choices.subList(1, choices.size())));
		selector.selectSource("");
		selector.refreshContent();
		selector.selectSource("cup");
		selector.refreshContent();
		assertEquals(second, getField(selector, "selectedInstanceId"));
		BattleNext_BattleSelectorPage activeDefault = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		activeDefault.attach(panel(1200f, 900f, new BaseCustomUIPanelPlugin()));
		assertEquals(first, getField(activeDefault, "selectedInstanceId"));
		activeDefault.close();
		selector.buttonPressed("tournament:active");
		instances.reload();
		assertEquals(second, instances.getActiveId());
		instances.activate("");
		BattleNext_BattleSelectorPage recentDefault = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		recentDefault.attach(panel(1200f, 900f, new BaseCustomUIPanelPlugin()));
		recentDefault.selectSource("cup");
		recentDefault.refreshContent();
		assertEquals(instances.getForSource("cup", BattleNext_StartedTournament.ExecutionKind.MANUAL).get(0).getId(),
				getField(recentDefault, "selectedInstanceId"));
		recentDefault.close();
		sources.getTournamentStore().reload();
		assertEquals(original, sources.getTournamentStore().get("cup").toJson().toString());
		selector.close();
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (label, action) -> action.run());
		page.attach(root);
		page.buttonPressed("tournament:select:cup");
		assertTrue(((BattleNext_TournamentEditor) getField(page, "editor")).isReadOnly());
		assertFalse(buttons.get("save").get());
		assertFalse(buttons.get("delete").get());
		page.close();
	}

	private List<Object> actionIds(BattleNext_BattleSelectorPage page) throws Exception {
		BattleNext_TournamentDisplay display = (BattleNext_TournamentDisplay) getField(page, "tournamentDisplay");
		List<?> actions = (List<?>) getField(display, "actions");
		return actions.stream().map(action -> ((BattleNext_TournamentDisplay.Action) action).id()).toList();
	}

	@Test
	void templateCheckRereadsAssetsPreservesCameraAndCreationUsesItsQualification() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		var sources = new BattleNext_BattleSourceProvider();
		sources.reloadCatalogs();
		var configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 3);
		sources.getTournamentStore().save(configuration);
		var selector = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		selector.attach(panel(1200f, 900f, new BaseCustomUIPanelPlugin()));
		selector.selectSource("cup");
		selector.refreshContent();
		var templates = (Map<String, BattleNext_TournamentTemplate>) getField(selector, "templates");
		templates.get("cup").select(0, "alpha");
		templates.get("cup").select(2, "beta");
		selector.refreshContent();
		var display = (BattleNext_TournamentDisplay) getField(selector, "tournamentDisplay");
		var chart = (BattleNext_TournamentView) getField(display, "chart");
		chart.zoomAt(90f, 90f, 2);
		chart.panBy(10f, 20f);
		var state = display.captureState();
		selector.buttonPressed("tournament:check");
		assertSame(display, getField(selector, "tournamentDisplay"));
		assertEquals(state, display.captureState());
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_check_passed")));
		var instances = new BattleNext_StartedTournamentStore();
		instances.reload();
		assertTrue(instances.getAll().isEmpty());
		assertTrue(instances.getActiveId().isEmpty());
		var result = getField(selector, "checkResult");
		BattleNext_Settings.save(true, true, false, false, 500f, 1f, 3f, 50);
		selector.refreshContent();
		assertSame(result, getField(selector, "checkResult"));
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_check_passed")));
		write("tournament/beta/fleet_data.json", "{broken");
		selector.buttonPressed("tournament:check");
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_check_failed")));
		selector.buttonPressed("tournament:create");
		instances.reload();
		assertTrue(instances.getAll().isEmpty());
		standard("beta", "beta", "child_variant");
		selector.buttonPressed("tournament:create");
		instances.reload();
		assertEquals(List.of("alpha", "", "beta"), instances.getAll().get(0).getFleetIds());
		assertTrue(instances.getActiveId().isEmpty());
		selector.selectSource("");
		assertNull(getField(selector, "checkResult"));
		selector.close();
	}

	@Test
	void fleetSummaryListsFilledSeatsInOrderAndUpdatesWhenASeatIsCleared() throws Exception {
		var configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.DOUBLE_ELIMINATION, 1, 4);
		List<String> slots = new ArrayList<>(List.of("", "alpha", "", "beta"));
		var details = new BattleNext_TournamentDisplay.Details(Map.of("alpha", "Alpha 50%", "beta", "风风雨雨"), true, null, "", List.of());
		var root = panel(1200f, 900f, new BaseCustomUIPanelPlugin());
		var display = new BattleNext_TournamentDisplay(new BattleNext_UiInputRouter(), configuration, null, details, List.of(), ignored -> {});
		display.enableTemplateEditing(root, new BattleNext_FleetSourceProvider(new BattleNext_FleetStore()), () -> slots, slots::set);
		display.attach(root);
		assertEquals(List.of("Alpha 50%", "风风雨雨"), fleetSummary(display));
		slots.set(1, "");
		display.update(configuration, null, details, List.of());
		assertEquals(List.of("风风雨雨"), fleetSummary(display));
		slots.set(3, "");
		display.update(configuration, null, details, List.of());
		assertEquals(List.of(), fleetSummary(display));
		display.close();
	}

	@Test
	void expandedCheckDiagnosticsUpdateScrollExtentAndKeepTheFinalActionsReachable() throws Exception {
		var configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		var details = new BattleNext_TournamentDisplay.Details(Map.of(), true, null, "", List.of());
		var actions = List.of(new BattleNext_TournamentDisplay.Action(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_create"), "create", true, List.of(), "create"),
				new BattleNext_TournamentDisplay.Action(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_check_fleets"), "check", true, List.of(), "create"));
		var display = new BattleNext_TournamentDisplay(new BattleNext_UiInputRouter(), configuration, null, details, actions, ignored -> {});
		display.attach(panel(650f, 350f, new BaseCustomUIPanelPlugin()));
		var content = (TooltipMakerAPI) getField(display, "content");
		var scroller = content.getExternalScroller();
		var chart = (BattleNext_TournamentView) getField(display, "chart");
		chart.panBy(12f, 8f);
		var camera = chart.captureState();
		scroller.setYOffset(30f);
		float offset = scroller.getYOffset();
		float originalHeight = content.getPosition().getHeight();
		var previousViewport = (CustomPanelAPI) getField(display, "viewport");
		List<BattleNext_BattleValidation.Failure> failures = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			failures.add(new BattleNext_BattleValidation.Failure(BattleNext_BattleValidation.FailureKind.RULE, "fleet-" + index,
					"Complete diagnosis 50% " + "long restriction details ".repeat(4)));
		}
		display.update(configuration, null, details.withCheckResult(new BattleNext_BattleValidation.Result(failures)), actions);
		var updatedContent = (TooltipMakerAPI) getField(display, "content");
		assertNotSame(content, updatedContent);
		assertTrue(removedComponents.contains(previousViewport));
		content = updatedContent;
		scroller = content.getExternalScroller();
		assertEquals(content.getHeightSoFar(), tooltips.get(content).getMountedHeight());
		assertTrue(content.getPosition().getHeight() > originalHeight);
		assertEquals(content.getHeightSoFar(), content.getPosition().getHeight());
		assertEquals(offset, scroller.getYOffset());
		assertSame(chart, getField(display, "chart"));
		assertEquals(camera, chart.captureState());
		scroller.setYOffset(Float.MAX_VALUE);
		assertEquals(content.getHeightSoFar() - scroller.getPosition().getHeight(), scroller.getYOffset());
		assertTrue(buttonPositions.get("check").getY() >= scroller.getPosition().getY() - 0.001f);
		assertTrue(buttonPositions.get("check").getY() + buttonPositions.get("check").getHeight()
				<= scroller.getPosition().getY() + scroller.getPosition().getHeight() + 0.001f);
		display.update(configuration, null, details, actions);
		content = (TooltipMakerAPI) getField(display, "content");
		scroller = content.getExternalScroller();
		assertEquals(originalHeight, content.getPosition().getHeight());
		assertTrue(scroller.getYOffset() <= originalHeight - scroller.getPosition().getHeight());
		display.close();
	}

	private List<String> fleetSummary(BattleNext_TournamentDisplay display) throws Exception {
		var summary = (CustomPanelAPI) getField(display, "summaryPanel");
		List<TooltipMakerAPI> contents = children.get(summary).stream().map(TooltipMakerAPI.class::cast).toList();
		TooltipMakerAPI title = contents.stream().filter(content -> tooltips.get(content).getComponents().stream()
				.anyMatch(component -> component instanceof LabelAPI label && label.getText().equals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_participant_fleets")))).findFirst().orElseThrow();
		TooltipMakerAPI fleets = contents.stream().filter(content -> content != title && content.getPosition().getX() == title.getPosition().getX()).findFirst().orElseThrow();
		return tooltips.get(fleets).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).map(LabelAPI::getText).toList();
	}

	private List<String> choiceIds(List<?> choices) throws Exception {
		List<String> ids = new ArrayList<>();
		for (Object choice : choices) {
			ids.add((String) getField(choice, "id"));
		}
		return ids;
	}

	private void selectInstance(BattleNext_BattleSelectorPage page, String id) throws Exception {
		BattleNext_BattleSourceField field = (BattleNext_BattleSourceField) getField(page, "instanceField");
		BattleNext_DropdownField dropdown = (BattleNext_DropdownField) getField(field, "dropdown");
		CustomPanelAPI row = (CustomPanelAPI) getField(dropdown, "row");
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		for (Object choice : (List<?>) getField(dropdown, "options")) {
			if (!id.equals(getField(choice, "id"))) continue;
			plugins.get((CustomPanelAPI) getField(dropdown, "popup")).buttonPressed(choice);
			assertFalse(dropdown.isOpen());
			assertTrue(((BattleNext_UiInputRouter) getField(page, "inputRouter")).isEmpty());
			return;
		}
		throw new AssertionError("Missing instance choice: " + id);
	}
}
