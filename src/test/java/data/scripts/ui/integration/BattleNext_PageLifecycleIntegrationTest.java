package data.scripts.ui.integration;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.BattleNext_BaseStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.page.*;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.simulator.BattleNext_SimulatorWidget;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_PageLifecycleIntegrationTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {1400})
	void everyPageUsesShellAndMainContentInsetsAndAlignedFooters(int width) throws Exception {

		deferTooltipWidth = true;
		Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		CustomPanelAPI root = panel(width, 900f, manager);
		manager.attach(root);
		manager.open();
		for (Object tab : tabs) {
			manager.buttonPressed(tab);
			BattleNext_PageView page = (BattleNext_PageView) getField(manager, "attachedView");
			CustomPanelAPI body = (CustomPanelAPI) getField(page, "panel");
			assertEquals(8f, body.getPosition().getX() - root.getPosition().getX());
			assertEquals(8f, root.getPosition().getX() + width - body.getPosition().getX() - body.getPosition().getWidth());
			if (page instanceof BattleNext_ManagementPage) {
				UIRect list = (UIRect) getField(page, "listRect");
				UIRect detail = (UIRect) getField(page, "detailRect");
				UIRect footer = (UIRect) getField(page, "footerRect");
				assertEquals(16f, body.getPosition().getX() - root.getPosition().getX() + list.getX());
				assertEquals(16f, width - 8f - detail.getX() - detail.getWidth());
				assertEquals(8f, footer.getX());
				assertEquals(8f, body.getPosition().getWidth() - footer.getX() - footer.getWidth());
			} else if (page instanceof BattleNext_SettingsPage) {
				Object editor = getField(page, "editor");
				TextFieldAPI field = (TextFieldAPI) getField(editor, "battleTimeLimitField");
				CustomPanelAPI editorPanel = (CustomPanelAPI) getField(editor, "panel");
				assertEquals(16f, field.getPosition().getX() - root.getPosition().getX());
				assertEquals(editorPanel.getPosition().getWidth(), field.getPosition().getWidth());
				assertEquals(16f, editorPanel.getPosition().getX() - root.getPosition().getX());
				PositionAPI simulator = ((BattleNext_SimulatorWidget) getField(page, "simulatorWidget")).getPanel().getPosition();
				assertEquals(16f, root.getPosition().getX() + width - simulator.getX() - simulator.getWidth());
				Map<?, ?> viewports = (Map<?, ?>) getField(editor, "viewports");
				List<UIComponentAPI> components = tooltips.get(viewports.get("settings")).getComponents();
				assertEquals(field.getPosition().getX(), components.get(0).getPosition().getX());
				assertEquals(field.getPosition().getX(), components.get(1).getPosition().getX());
				assertEquals(16f, buttonPositions.get("settings_save").getX() - root.getPosition().getX());
			} else {
				Object editor = getField(page, "quickBattleEditor");
				BattleNext_TableWidget table = (BattleNext_TableWidget) getField(getField(editor, "display"), "tableWidget");
				TooltipMakerAPI header = (TooltipMakerAPI) children.get(table.getPanel()).get(0);
				assertEquals(16f, header.getPosition().getX() - root.getPosition().getX());
				assertEquals(16f, root.getPosition().getX() + width - header.getPosition().getX() - header.getPosition().getWidth());
				assertEquals(16f, buttonPositions.get("add").getX() - root.getPosition().getX());
			}
		}
		manager.buttonPressed("close");
		assertFalse(((BattleNext_UiInputRouter) getField(manager, "inputRouter")).getSession().hasInputState());
	}

	@ParameterizedTest
	@CsvSource({"fleet,180", "pin,1400", "report,1400", "rules,1400", "tournament,1400"})
	void catalogsUseSymmetricButtonsDeclaredWidthsAndCompleteHoverText(String kind, int width) throws Exception {

		deferTooltipWidth = true;
		shortenLabels = true;
		String name = "目录 中文 😀 50% " + "完整名称 ".repeat(20);
		BattleNext_ManagementPage page = directoryPage(kind);
		saveDirectoryEntries(kind, page, name);
		CustomPanelAPI root = panel(width, 200f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		CustomPanelAPI listPanel = (CustomPanelAPI) getField(page, "listPanel");
		TooltipMakerAPI list = (TooltipMakerAPI) getField(page, "listContent");
		TooltipState state = tooltips.get(list);
		List<UIComponentAPI> components = state.getComponents();
		assertEquals(8f, components.get(0).getPosition().getX() - listPanel.getPosition().getX());
		int count = 0;
		for (UIComponentAPI component : components) {
			if (!(component instanceof ButtonAPI button)) continue;
			count++;
			PositionAPI bounds = button.getPosition();
			assertEquals(8f, bounds.getX() - listPanel.getPosition().getX());
			assertEquals(8f, listPanel.getPosition().getX() + listPanel.getPosition().getWidth() - bounds.getX() - bounds.getWidth());
			assertEquals(listPanel.getPosition().getWidth() - 16f, bounds.getWidth());
			assertEquals(bounds.getWidth() - 10f, list.getWidthSoFar());
			assertEquals(bounds.getWidth() + 5f, state.getViewport().getWidth());
			assertTrue(button.getText().endsWith("..."));
			TooltipMakerAPI hover = tooltip(400f, 0f, false);
			attachedTooltips.get(button).createTooltip(hover, false, null);
			assertTrue(tooltips.get(hover).getComponents().stream().filter(LabelAPI.class::isInstance)
					.map(LabelAPI.class::cast).map(LabelAPI::getText).anyMatch(value -> value.contains(name)));
		}
		assertEquals(List.of("first", "second").size(), count);
		state.getScroller().setYOffset(state.getUsedHeight());
		float offset = state.getScroller().getYOffset();
		state.getScroller().setYOffset(0f);
		ButtonAPI selected = components.stream().filter(ButtonAPI.class::isInstance).map(ButtonAPI.class::cast).findFirst().orElseThrow();
		Object action = selected.getCustomData();
		page.buttonPressed(action);
		assertNotNull(getField(getField(page, "editor"), "nameField"));
		assertFalse(page.hasUnsavedChanges());
		page.refreshContent();
		TooltipMakerAPI next = (TooltipMakerAPI) getField(page, "listContent");
		next.getExternalScroller().setYOffset(offset);
		page.refreshContent();
		next = (TooltipMakerAPI) getField(page, "listContent");
		assertEquals(offset, next.getExternalScroller().getYOffset());
		page.close();
		assertTrue(children.get(root).isEmpty());
	}

	private BattleNext_ManagementPage directoryPage(String kind) {
		return switch (kind) {
			case "fleet" -> new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {});
			case "pin" -> new BattleNext_PinnedVariantPage(() -> {}, Runnable::run, (description, action) -> {});
			case "report" -> new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
			case "rules" -> new BattleNext_RulesPage(() -> {}, Runnable::run, (description, action) -> {});
			default -> new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
		};
	}

	private void saveDirectoryEntries(String kind, BattleNext_ManagementPage page, String name) throws Exception {
		String field = kind.equals("tournament") ? "tournamentStore" : kind.equals("report") ? "reportStore" : "store";
		BattleNext_BaseStore<?> store = (BattleNext_BaseStore<?>) getField(page, field);
		store.reload();
		for (String id : List.of("first", "second")) {
			String entryName = name + id;
			switch (kind) {
				case "fleet" -> ((BattleNext_FleetStore) store).save(new BattleNext_FleetModel(id, entryName, List.of(), List.of(), List.of()));
				case "pin" -> ((BattleNext_PinnedVariantStore) store).saveVariant(entryName, source().getApi());
				case "report" -> {
					BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
					report.setId(id);
					report.setName(entryName);
					((BattleNext_BattleReportStore) store).save(report);
				}
				case "rules" -> ((BattleNext_RuleStore) store).save(new BattleNext_RuleModel(id, entryName));
				default -> ((BattleNext_TournamentStore) store).save(new BattleNext_TournamentModel(id, entryName,
						BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {1400})
	void managementPagesShareRegionsPromptsScrollingAndFooter(int width) throws Exception {

		List<BattleNext_ManagementPage> pages = List.of(
				new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {}),
				new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {}));
		for (BattleNext_ManagementPage page : pages) {
			buttons.clear();
			CustomPanelAPI root = panel(width, 700f, new BaseCustomUIPanelPlugin());
			page.attach(root);
			CustomPanelAPI shell = (CustomPanelAPI) getField(page, "panel");
			UIRect[] expected = BattleNext_PageLayout.managementRegions(shell);
			UIRect listRect = (UIRect) getField(page, "listRect");
			UIRect detailRect = (UIRect) getField(page, "detailRect");
			assertEquals(expected[0].getX(), listRect.getX());
			assertEquals(expected[0].getWidth(), listRect.getWidth());
			assertEquals(expected[1].getX(), detailRect.getX());
			assertEquals(expected[1].getHeight(), detailRect.getHeight());
			TooltipMakerAPI list = (TooltipMakerAPI) getField(page, "listContent");
			TooltipState listState = tooltips.get(list);
			assertTrue(listState.isScrollable());
			assertEquals(listState.getUsedHeight(), listState.getMountedHeight());
			CustomPanelAPI detail = (CustomPanelAPI) getField(page, "detailPanel");
			TooltipState prompt = tooltips.get(children.get(detail).get(0));
			assertFalse(prompt.isScrollable());
			assertEquals(8f, prompt.getViewport().getX() - detail.getPosition().getX());
			assertEquals(8f, detail.getPosition().getY() + detail.getPosition().getHeight()
					- prompt.getViewport().getY() - prompt.getViewport().getHeight());
			assertFalse(buttons.get("save").get());
			boolean add = page instanceof BattleNext_FleetLibraryPage || page instanceof BattleNext_TournamentPage
					|| page instanceof BattleNext_RulesPage;
			assertEquals(add ? List.of("add", "save", "back") : List.of("save", "back"),
					new ArrayList<>(buttons.keySet()));
			float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(expected[2], add ? 3 : 2);
			UIRect save = BattleNext_PageLayout.managementFooterButtonRect(expected[2], buttonWidth, add ? 1 : 0);
			PositionAPI savePosition = buttonPositions.get("save");
			assertEquals(save.getX(), savePosition.getX() - shell.getPosition().getX());
			assertEquals(save.getY(), shell.getPosition().getY() + shell.getPosition().getHeight()
					- savePosition.getY() - savePosition.getHeight());
			int childCount = children.get(shell).size();
			page.refreshContent();
			assertEquals(childCount, children.get(shell).size());
			assertEquals(1, children.get(root).size());
			page.createContents();
			assertEquals(1, children.get(root).size());
			page.close();
			assertTrue(children.get(root).isEmpty());
		}
	}

	@Test
	void pageLifecycleOwnsMountsInputAndSaveDiscardBridges() throws Exception {

		ProbePage page = new ProbePage();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		page.setInputRouter(router);
		CustomPanelAPI firstRoot = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		page.attach(firstRoot);
		assertTrue(page.getEvents().indexOf("load") < page.getEvents().indexOf("build"));
		assertTrue(page.getEvents().indexOf("build") < page.getEvents().indexOf("refresh"));
		assertTrue(page.getEvents().indexOf("refresh") < page.getEvents().indexOf("baseline"));
		AtomicBoolean closed = new AtomicBoolean();
		router.push(new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return firstRoot.getPosition();
			}

			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				return false;
			}

			@Override
			public void close() {
				closed.set(true);
			}
		});
		page.refreshContent();
		assertTrue(closed.get());
		assertTrue(router.isEmpty());
		assertEquals(1, children.get(firstRoot).size());
		page.setDirty(true);
		assertTrue(page.hasUnsavedChanges());
		assertTrue(page.save());
		assertEquals("saved", page.getStatusText());
		assertEquals("saved", ((LabelAPI) getField(page, "statusLabel")).getText());
		assertFalse(page.hasUnsavedChanges());
		page.setDirty(true);
		page.discard();
		assertFalse(page.hasUnsavedChanges());
		CustomPanelAPI secondRoot = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		page.attach(secondRoot);
		assertTrue(children.get(firstRoot).isEmpty());
		assertEquals(1, children.get(secondRoot).size());
		page.close();
		assertTrue(children.get(secondRoot).isEmpty());
	}

	@Test
	void selectorKeepsRawDraftAndDirtyStateAcrossRefresh() throws Exception {
		BattleNext_BattleSelectorPage page = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("add");
		Object editor = getField(page, "quickBattleEditor");
		Object row = ((List<?>) getField(getField(editor, "display"), "rows")).get(0);
		((TextFieldAPI) getField(row, "playerField")).setText("Draft fleet");
		((TextFieldAPI) getField(row, "mapField")).setText("Unfinished number");
		String snapshot = ((BattleNext_EditorView) editor).serializeState();
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		assertSame(editor, getField(page, "quickBattleEditor"));
		assertEquals(snapshot, ((BattleNext_EditorView) editor).serializeState());
		assertTrue(page.hasUnsavedChanges());
	}

	@Test
	void settingsKeepsRawDraftAndDirtyStateAcrossRefresh() throws Exception {
		BattleNext_SettingsPage page = new BattleNext_SettingsPage(action -> {});
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(getField(page, "editor"), "battleTimeLimitField")).setText("pending");
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		assertEquals("pending", ((TextFieldAPI) getField(getField(page, "editor"), "battleTimeLimitField")).getText());
		assertTrue(page.hasUnsavedChanges());
		CustomPanelAPI content = (CustomPanelAPI) getField(getField(page, "editor"), "panel");
		TooltipState state = tooltips.get(children.get(content).get(0));
		PositionAPI save = buttonPositions.get("settings_save");
		assertEquals(8f, state.getViewport().getY() - save.getY() - save.getHeight());
		page.discard();
		assertFalse(page.hasUnsavedChanges());
	}

	@ParameterizedTest
	@ValueSource(strings = {"fleet", "rules", "tournament"})
	void writableEditorsKeepDraftAndBaselineAcrossPageRefresh(String kind) throws Exception {
		BattleNext_ManagementPage page = switch (kind) {
			case "fleet" -> new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {});
			case "rules" -> new BattleNext_RulesPage(() -> {}, Runnable::run, (description, action) -> {});
			default -> new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
		};
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		assertFalse(buttons.get("save").get());
		page.buttonPressed("add");
		assertTrue(buttons.get("save").get());
		Object editor = getField(page, "editor");
		TextFieldAPI field = (TextFieldAPI) getField(editor, "fleet".equals(kind) ? "nameField" : "nameField");
		field.setText("Draft name");
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		field = (TextFieldAPI) getField(editor, "fleet".equals(kind) ? "nameField" : "nameField");
		assertEquals("Draft name", field.getText());
		assertTrue(page.hasUnsavedChanges());
		page.buttonPressed("back");
		assertFalse(buttons.get("save").get());
	}

	@ParameterizedTest
	@ValueSource(strings = {"fleet", "pin", "report", "tournament", "rules"})
	void managementSaveRequiresEveryStoreUsedByTheSelectedObject(String kind) throws Exception {

		BattleNext_ManagementPage page = switch (kind) {
			case "fleet" -> new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {});
			case "pin" -> new BattleNext_PinnedVariantPage(() -> {}, Runnable::run, (description, action) -> {});
			case "report" -> new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
			case "tournament" -> new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
			default -> new BattleNext_RulesPage(() -> {}, Runnable::run, (description, action) -> {});
		};
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		assertFalse(buttons.get("save").get());
		if ("pin".equals(kind)) {
			BattleNext_PinnedVariantStore store = (BattleNext_PinnedVariantStore) getField(page, "store");
			var pin = store.saveVariant("Pin", source().getApi());
			page.attach(root);
			page.buttonPressed("pin:" + pin.getSourcePath());
		} else if ("report".equals(kind)) {
			var store = (BattleNext_BattleReportStore) getField(page, "reportStore");
			var report = new BattleNext_BattleReportModel();
			report.setId("report-1");
			report.setName("Report");
			store.save(report);
			page.attach(root);
			page.buttonPressed("report:report:report-1");
		} else {
			page.buttonPressed("add");
		}
		assertTrue(buttons.get("save").get());
		List<String> dependencies = switch (kind) {
			case "tournament" -> List.of("tournamentStore", "ruleStore");
			case "report" -> List.of("reportStore");
			default -> List.of("store");
		};
		Field loadError = BattleNext_BaseStore.class.getDeclaredField("loadError");
		loadError.setAccessible(true);
		for (String dependency : dependencies) {
			Object store = getField(page, dependency);
			loadError.set(store, "Injected load failure");
			page.refreshContent();
			assertFalse(buttons.get("save").get(), dependency);
			loadError.set(store, "");
			page.refreshContent();
			assertTrue(buttons.get("save").get(), dependency);
		}
	}

	@Test
	void tournamentSaveIsDisabledForStartedObjectsAcrossRefresh() throws Exception {
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		var store = (BattleNext_TournamentStore) getField(page, "tournamentStore");
		var tournament = new BattleNext_TournamentModel();
		tournament.setId("cup");
		tournament.setName("Cup");
		store.save(tournament);
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		instances.save(new BattleNext_StartedTournament(UUID.randomUUID().toString(), tournament.getId(), BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("alpha", "beta")));
		page.attach(root);
		page.buttonPressed("tournament:select:cup");
		assertFalse(buttons.get("save").get());
		assertFalse(page.hasUnsavedChanges());
		page.refreshContent();
		assertFalse(buttons.get("save").get());
		assertTrue(((BattleNext_TournamentEditor) getField(page, "editor")).isReadOnly());
	}

	@Test
	void reportDetailsKeepDraftExpansionAndFooterSaveAcrossRefresh() throws Exception {

		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report-1");
		report.setName("Original report");
		report.addPlayerMember(new BattleNext_MemberReport("parent_variant", "parent", "Parent"));
		store.save(report);
		BattleNext_BattleReportPage page = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("report:report:report-1");
		assertTrue(buttons.get("save").get());
		assertTrue(labels.contains("report-1"));
		((TextFieldAPI) getField(getField(page, "editor"), "nameField")).setText("Draft report");
		page.buttonPressed("member:0");
		assertEquals(1, ((Set<?>) getField(getField(getField(page, "editor"), "details"), "expandedMembers")).size());
		assertEquals("Draft report", ((TextFieldAPI) getField(getField(page, "editor"), "nameField")).getText());
		assertTrue(page.hasUnsavedChanges());
		CustomPanelAPI detail = (CustomPanelAPI) getField(page, "detailPanel");
		UIRect detailRect = (UIRect) getField(page, "detailRect");
		UIRect delete = BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect);
		assertEquals(delete.getX(), buttonPositions.get("delete").getX() - detail.getPosition().getX());
		assertEquals(delete.getY(), detail.getPosition().getY() + detail.getPosition().getHeight()
				- buttonPositions.get("delete").getY() - buttonPositions.get("delete").getHeight());
		assertTrue(page.save());
		assertFalse(page.hasUnsavedChanges());
		store.reload();
		assertEquals("Draft report", store.get("report-1").getName());
	}
}
