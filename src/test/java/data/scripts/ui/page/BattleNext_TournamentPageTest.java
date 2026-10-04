package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.report.BattleNext_BattleReportModel;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_TournamentPageTest extends BattleNext_TestPageFixture {

	@Test
	void attachRefreshAndCloseOwnThePagePanel() throws Exception {
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());

		page.attach(root);
		assertNotNull(getField(page, "panel"));
		assertEquals(1, children.get(root).size());
		page.refreshContent();
		assertEquals(1, children.get(root).size());
		page.close();
		assertEquals(0, children.get(root).size());
	}

	@ParameterizedTest
	@EnumSource(TournamentBracketType.class)
	void startedAndCompletedFormatsShareSelectorContentAndKeepStoredProgressReadOnly(TournamentBracketType type) throws Exception {

		deferTooltipWidth = true;
		saveFleets(List.of("first", "second", "third"));
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		rules.save(new BattleNext_RuleModel("rule", "Rule"));
		BattleNext_TournamentModel tournament = tournament("cup", type, true);
		tournament.setRuleId("rule");
		BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
		BattleNext_Match first = tournament.getNextPendingMatch();
		engine.recordRound(tournament, first.getId(), "draw", new BattleNext_BattleReportModel());
		for (boolean completed : List.of(false, true)) {
			if (completed) {
				while (!tournament.isCompleted()) {
					BattleNext_Match next = engine.getNextMatch(tournament);
					engine.recordRound(tournament, next.getId(), next.getPlayer(), new BattleNext_BattleReportModel());
				}
			}
			saveTournament(tournament, true);
			String persisted = Files.readString(directory.resolve(tournament.getSourcePath()), StandardCharsets.UTF_8);
			for (float width : new float[]{900f, 1400f}) {
				BattleNext_TournamentPage page = page();
				page.attach(panel(width, 900f, new BaseCustomUIPanelPlugin()));
				page.buttonPressed("tournament:select:cup");
				BattleNext_TournamentDisplay display = display(page);
				BattleNext_BattleSelectorPage selector = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
				selector.attach(panel(width, 900f, new BaseCustomUIPanelPlugin()));
				BattleNext_TournamentDisplay reference = (BattleNext_TournamentDisplay) getField(selector, "tournamentDisplay");
				assertNotNull(display);
				assertEquals(displayText(reference), displayText(display));
				BattleNext_TournamentDisplay.Details details = (BattleNext_TournamentDisplay.Details) getField(display, "details");
				BattleNext_TournamentDisplay.Details expected = (BattleNext_TournamentDisplay.Details) getField(reference, "details");
				assertEquals(expected.fleetNames(), details.fleetNames());
				assertEquals(expected.ruleAvailable(), details.ruleAvailable());
				assertEquals(expected.warnings(), details.warnings());
				assertEquals(completed, details.currentMatch() == null);
				assertFalse(details.startEnabled());
				assertFalse(details.authorizeAvailable());
				assertFalse(page.hasUnsavedChanges());
				assertFalse(((ButtonAPI) getField(page, "saveButton")).isEnabled());
				CustomPanelAPI canvas = (CustomPanelAPI) getField(chart(display), "panel");
				float available = display.getPanel().getPosition().getWidth() - 12f;
				assertEquals(Math.min(available, BattleNext_TournamentView.CANVAS_WIDTH), canvas.getPosition().getWidth());
				assertEquals(canvas.getPosition().getWidth() / 3f, canvas.getPosition().getHeight(), 0.001f);
				assertSame(getField(page, "inputRouter"), getField(display, "inputRouter"));
				assertSame(getField(page, "inputRouter"), getField(chart(display), "inputRouter"));
				BattleNext_TournamentModel loaded = ((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("cup");
				assertJsonEquals(tournament.toJson(), loaded.toJson());
				selector.close();
				page.close();
				assertEquals(persisted, Files.readString(directory.resolve(tournament.getSourcePath()), StandardCharsets.UTF_8));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void sourceAndRuleWarningsFollowActiveSelectionWhileOperationsStayAtSelector(boolean active) throws Exception {

		saveFleets(List.of("first", "second", "third"));
		BattleNext_TournamentModel tournament = tournament("cup", TournamentBracketType.SINGLE_ELIMINATION, true);
		saveTournament(tournament, active);
		BattleNext_TournamentPage page = page();
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		AtomicInteger loads = new AtomicInteger();
		setField(page, "provider", new BattleNext_BattleSourceProvider() {
			@Override
			public List<BattleNext_Match> load() {
				loads.incrementAndGet();
				return List.of();
			}

			@Override
			public List<String> getWarnings() {
				return List.of("Source warning");
			}

			@Override
			public List<String> getRuleWarnings() {
				return List.of("Rule warning");
			}
		});
		page.buttonPressed("tournament:select:cup");
		assertEquals(active ? 1 : 0, loads.get());
		BattleNext_TournamentDisplay.Details details = (BattleNext_TournamentDisplay.Details) getField(display(page), "details");
		assertEquals(active ? List.of("Source warning", "Rule warning") : List.of(), details.warnings());
		assertEquals(active, displayText(display(page)).contains("Rule warning"));
		assertFalse(buttons.containsKey("tournament:authorize"));
		assertFalse(buttons.containsKey("tournament:start"));
		String before = ((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("cup").toJson().toString();
		page.buttonPressed("tournament:authorize");
		page.buttonPressed("tournament:start");
		assertEquals(before, ((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("cup").toJson().toString());
		page.close();
	}

	@Test
	void missingFleetsRulesAndLoadFailuresRemainVisibleAlongsideChartAndSummary() throws Exception {
		saveFleets(List.of("first"));
		BattleNext_TournamentModel tournament = tournament("cup", TournamentBracketType.SINGLE_ELIMINATION, true);
		tournament.setRuleId("missing_rule");
		saveTournament(tournament, false);
		var invalidRule = Files.createDirectories(directory.resolve("tournament/_rules")).resolve("invalid.json");
		Files.writeString(invalidRule, "{\"formatVersion\":" + (BattleNext_RuleModel.FORMAT_VERSION + 1) + "}", StandardCharsets.UTF_8);
		BattleNext_TournamentPage page = page();
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("tournament:select:cup");
		List<String> text = displayText(display(page));
		assertFalse(((BattleNext_RuleStore) getField(page, "ruleStore")).isLoaded());
		assertTrue(page.getStatusText().contains("invalid.json"));
		assertTrue(text.contains(page.getStatusText()));
		assertTrue(text.stream().anyMatch(value -> value.contains("BattleNext_tournament_rules_unavailable")));
		assertTrue(text.stream().anyMatch(value -> value.contains("BattleNext_tournament_invalid_participant_fleet [second]")));
		assertTrue(text.stream().anyMatch(value -> value.contains("BattleNext_tournament_chart_error")));
		assertTrue(text.contains("BattleNext_tournament_summary"));
		assertNotNull(chart(display(page)));
		page.close();
	}

	@Test
	void scrollingChartOutOfTheViewportLeavesWheelInputToThePage() throws Exception {

		saveFleets(List.of("first", "second", "third"));
		saveTournament(tournament("cup", TournamentBracketType.SINGLE_ELIMINATION, true), false);
		BattleNext_TournamentPage page = page();
		page.attach(panel(1400f, 400f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("tournament:select:cup");
		BattleNext_TournamentDisplay display = display(page);
		BattleNext_TournamentView chart = chart(display);
		CustomPanelAPI viewport = (CustomPanelAPI) children.get(display.getPanel()).stream()
				.filter(CustomPanelAPI.class::isInstance).findFirst().orElseThrow();
		TooltipMakerAPI content = (TooltipMakerAPI) children.get(viewport).get(0);
		assertTrue(content.getHeightSoFar() > viewport.getPosition().getHeight());
		PositionAPI canvas = ((CustomPanelAPI) getField(chart, "panel")).getPosition();
		float x = canvas.getX() + canvas.getWidth() * 0.5f;
		float y = canvas.getY() + canvas.getHeight() * 0.5f;
		AtomicBoolean consumed = new AtomicBoolean();
		page.handleGlobalInput(List.of(pointer("wheel", x, y, 120, consumed)));
		assertTrue(consumed.get());
		content.getExternalScroller().setYOffset(content.getHeightSoFar());
		assertTrue(canvas.getY() >= viewport.getPosition().getY() + viewport.getPosition().getHeight());
		consumed.set(false);
		page.handleGlobalInput(List.of(pointer("wheel", x, y, 120, consumed)));
		assertFalse(consumed.get());
		assertNull(getField(chart, "hovered"));
		content.getExternalScroller().setYOffset(0f);
		page.handleGlobalInput(List.of(pointer("wheel", x, y, 120, consumed)));
		assertTrue(consumed.get());
		page.close();
	}

	@Test
	void switchingRefreshingReturningAndClosingReleaseDisplayOwnershipAndInput() throws Exception {

		saveFleets(List.of("first", "second", "third"));
		saveTournament(tournament("cup", TournamentBracketType.SINGLE_ELIMINATION, true), false);
		saveTournament(tournament("other", TournamentBracketType.ROUND_ROBIN, true), false);
		saveTournament(tournament("draft", TournamentBracketType.SINGLE_ELIMINATION, false), false);
		BattleNext_TournamentPage page = page();
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		page.buttonPressed("tournament:select:cup");
		int mounted = componentCount((CustomPanelAPI) getField(page, "panel"));
		for (int iteration = 0; iteration < 3; iteration++) {
			BattleNext_TournamentDisplay previous = display(page);
			BattleNext_TournamentView oldChart = chart(previous);
			page.refreshContent();
			assertReleased(previous, oldChart);
			assertEquals(mounted, componentCount((CustomPanelAPI) getField(page, "panel")));
			previous = display(page);
			oldChart = chart(previous);
			page.buttonPressed("tournament:select:draft");
			assertReleased(previous, oldChart);
			assertNull(display(page));
			assertFalse(((Map<?, ?>) getField(((BattleNext_UiInputRouter) getField(page, "inputRouter")).getSession(), "REGISTERED")).isEmpty());
			page.buttonPressed("tournament:select:other");
			assertNotNull(display(page));
			assertTrue(((Map<?, ?>) getField(((BattleNext_UiInputRouter) getField(page, "inputRouter")).getSession(), "REGISTERED")).isEmpty());
			previous = display(page);
			oldChart = chart(previous);
			page.buttonPressed("back");
			assertReleased(previous, oldChart);
			assertNull(display(page));
			page.buttonPressed("tournament:select:cup");
		}
		BattleNext_TournamentDisplay previous = display(page);
		BattleNext_TournamentView oldChart = chart(previous);
		page.close();
		assertReleased(previous, oldChart);
		assertTrue(children.get(root).isEmpty());
		page.close();
		page.attach(root);
		page.buttonPressed("tournament:select:cup");
		assertNotSame(previous, display(page));
		page.close();
	}

	@Test
	void pageRoutesHoverZoomDragToolbarAndClippedModalInputToTheSharedChart() throws Exception {

		saveFleets(List.of("first", "second", "third"));
		saveTournament(tournament("cup", TournamentBracketType.SINGLE_ELIMINATION, true), false);
		BattleNext_TournamentPage page = page();
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("tournament:select:cup");
		BattleNext_TournamentView chart = chart(display(page));
		chart.resetZoom();
		BattleNext_TournamentView.Node node = ((List<BattleNext_TournamentView.Node>) getField(chart, "nodes")).stream()
				.filter(value -> value.match != null && !value.enemy.fleetId.isEmpty()).findFirst().orElseThrow();
		float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
		page.handleGlobalInput(List.of(pointer("move", center[0], center[1], 0, new AtomicBoolean())));
		UIComponentAPI target = (UIComponentAPI) getField(chart, "tooltipTarget");
		assertTrue(target.getPosition().getHeight() > 0f);
		TooltipMakerAPI tooltip = tooltip(360f, 0f, false);
		attachedTooltips.get(target).createTooltip(tooltip, false, null);
		assertTrue(tooltips.get(tooltip).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).anyMatch(label -> label.getText().contains(node.id)));
		AtomicBoolean consumed = new AtomicBoolean();
		page.handleGlobalInput(List.of(pointer("wheel", center[0], center[1], 120, consumed)));
		assertTrue(consumed.get());
		float[] before = chart.project(node.x, node.y);
		AtomicBoolean down = new AtomicBoolean();
		AtomicBoolean move = new AtomicBoolean();
		AtomicBoolean up = new AtomicBoolean();
		page.handleGlobalInput(List.of(pointer("down", center[0], center[1], 0, down),
				pointer("move", center[0] + 20f, center[1] + 20f, 0, move),
				pointer("up", center[0] + 20f, center[1] + 20f, 0, up)));
		assertTrue(down.get() && move.get() && up.get());
		assertArrayEquals(new float[]{before[0] + 20f, before[1] + 20f}, chart.project(node.x, node.y), 0.01f);
		chart.buttonPressed("view:fit");
		assertNull(getField(chart, "hovered"));
		chart.buttonPressed("view:reset");
		PositionAPI canvas = ((CustomPanelAPI) getField(chart, "panel")).getPosition();
		consumed.set(false);
		page.handleGlobalInput(List.of(pointer("wheel", canvas.getX() - 1f, center[1], 120, consumed)));
		assertFalse(consumed.get());
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(page, "inputRouter");
		BattleNext_ConfirmationDialog dialog = confirmation(page, router);
		dialog.attach((CustomPanelAPI) getField(page, "panel"));
		dialog.request(() -> {});
		page.handleGlobalInput(List.of(pointer("wheel", center[0], center[1], 120, consumed)));
		assertFalse(consumed.get());
		assertEquals(0f, target.getPosition().getHeight());
		dialog.handle("confirm_cancel");
		consumed.set(false);
		page.handleGlobalInput(List.of(pointer("wheel", center[0], center[1], 120, consumed)));
		assertTrue(consumed.get());
		dialog.close();
		page.close();
	}

	@Test
	void newAndUnstartedEditorsKeepSaveDeleteAndNavigationConfirmationFlows() throws Exception {

		saveFleets(List.of("first", "second", "third"));
		List<Runnable> navigation = new ArrayList<>();
		List<Runnable> deletion = new ArrayList<>();
		AtomicBoolean back = new AtomicBoolean();
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> back.set(true), navigation::add,
				(description, action) -> deletion.add(action));
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		page.buttonPressed("add");
		navigation.remove(0).run();
		assertNull(display(page));
		BattleNext_TournamentEditor editor = (BattleNext_TournamentEditor) getField(page, "editor");
		((TextFieldAPI) getField(editor, "idField")).setText("created");
		((TextFieldAPI) getField(editor, "nameField")).setText("Created cup");
		((TextFieldAPI) getField(editor, "maxFleetField")).setText("3");
		confirmTournamentFleets(editor, List.of("first", "second", "third"));
		assertTrue(page.hasUnsavedChanges());
		page.buttonPressed("save");
		assertFalse(page.hasUnsavedChanges());
		assertEquals("Created cup", ((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("created").getName());
		((TextFieldAPI) getField(editor, "nameField")).setText("Edited cup");
		page.buttonPressed("back");
		Runnable leave = navigation.remove(0);
		BattleNext_ConfirmationDialog dialog = confirmation(page, (BattleNext_UiInputRouter) getField(page, "inputRouter"));
		dialog.attach(root);
		dialog.request(leave);
		dialog.handle("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		assertEquals("Edited cup", ((TextFieldAPI) getField(editor, "nameField")).getText());
		dialog.request(leave);
		dialog.handle("confirm_save");
		assertFalse(page.hasUnsavedChanges());
		page.buttonPressed("tournament:select:created");
		navigation.remove(0).run();
		assertNull(display(page));
		assertEquals("Edited cup", ((TextFieldAPI) getField(editor, "nameField")).getText());
		page.buttonPressed("delete");
		assertEquals(1, deletion.size());
		deletion.remove(0).run();
		assertNull(((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("created"));
		page.buttonPressed("back");
		assertTrue(back.get());
		dialog.close();
		page.close();
	}

	private BattleNext_TournamentPage page() {
		return new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> action.run());
	}

	private BattleNext_TournamentModel tournament(String id, TournamentBracketType type, boolean started) {
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel(id, "Cup " + id, type, 3, 3);
		tournament.setFleetIds(List.of("first", "second", "third"));
		tournament.setMapId(3);
		if (started) {
			new BattleNext_TournamentEngine().start(tournament);
		}
		return tournament;
	}

	private void saveFleets(List<String> ids) throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		for (String id : ids) {
			store.save(new BattleNext_FleetModel(id, "Fleet " + id + " with a full descriptive name", List.of(), List.of(), List.of()));
		}
	}

	private void saveTournament(BattleNext_TournamentModel tournament, boolean active) throws Exception {
		BattleNext_TournamentStore store = new BattleNext_TournamentStore();
		store.reload();
		store.save(tournament);
		if (active) {
			store.setActiveId(tournament.getId());
		}
	}

	private BattleNext_TournamentDisplay display(BattleNext_TournamentPage page) throws Exception {
		return (BattleNext_TournamentDisplay) getField(page, "tournamentDisplay");
	}

	private BattleNext_TournamentView chart(BattleNext_TournamentDisplay display) throws Exception {
		return (BattleNext_TournamentView) getField(display, "chart");
	}

	private List<String> displayText(BattleNext_TournamentDisplay display) throws Exception {
		List<String> text = new ArrayList<>();
		collectText(display.getPanel(), text);
		for (Object label : (List<?>) getField(chart(display), "labels")) {
			text.add(((LabelAPI) label).getText());
		}
		return text;
	}

	private void collectText(Object component, List<String> text) {
		if (component instanceof LabelAPI label) {
			text.add(label.getText());
		} else if (component instanceof CustomPanelAPI panel) {
			for (Object child : children.getOrDefault(panel, List.of())) {
				collectText(child, text);
			}
		} else if (component instanceof TooltipMakerAPI host) {
			for (Object child : tooltips.get(host).getComponents()) {
				collectText(child, text);
			}
		}
	}

	private int componentCount(Object component) {
		List<?> mounted = component instanceof CustomPanelAPI panel ? children.getOrDefault(panel, List.of())
				: component instanceof TooltipMakerAPI host ? tooltips.get(host).getComponents() : List.of();
		int count = 1;
		for (Object child : mounted) {
			count += componentCount(child);
		}
		return count;
	}

	private void assertReleased(BattleNext_TournamentDisplay display, BattleNext_TournamentView chart) throws Exception {
		assertNull(display.getPanel());
		assertNull(getField(display, "chart"));
		assertTrue(((List<?>) getField(display, "ownedPanels")).isEmpty());
		assertNull(getField(chart, "hovered"));
		assertFalse((Boolean) getField(chart, "dragging"));
		assertEquals(0f, ((UIComponentAPI) getField(chart, "tooltipTarget")).getPosition().getHeight());
		AtomicBoolean consumed = new AtomicBoolean();
		display.handleGlobalInput(List.of(pointer("wheel", 400f, 400f, 120, consumed)));
		assertFalse(consumed.get());
	}

	private InputEventAPI pointer(String type, float x, float y, int value, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "getX" -> (int) x;
			case "getY" -> (int) y;
			case "getEventValue" -> value;
			case "isMouseEvent" -> true;
			case "isMouseScrollEvent" -> type.equals("wheel");
			case "isLMBDownEvent" -> type.equals("down");
			case "isMouseMoveEvent" -> type.equals("move");
			case "isLMBUpEvent" -> type.equals("up");
			case "isConsumed" -> consumed.get();
			case "consume" -> { consumed.set(true); yield null; }
			default -> null;
		});
	}

	private BattleNext_ConfirmationDialog confirmation(BattleNext_TournamentPage page, BattleNext_UiInputRouter router) {
		return new BattleNext_ConfirmationDialog(new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
			@Override
			public boolean saveCurrentPage() {
				return page.save();
			}

			@Override
			public String getSaveError() {
				return page.getStatusText();
			}

			@Override
			public void discardCurrentPage() {
				page.discard();
			}
		}, router);
	}

	private void setField(Object target, String name, Object value) throws Exception {
		Class<?> owner = target.getClass();
		while (Arrays.stream(owner.getDeclaredFields()).noneMatch(field -> field.getName().equals(name))) {
			owner = owner.getSuperclass();
		}
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
