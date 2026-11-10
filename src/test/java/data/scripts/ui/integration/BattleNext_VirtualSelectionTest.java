package data.scripts.ui.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.page.BattleNext_FleetLibraryPage;
import data.scripts.ui.widget.provider.BattleNext_VariantCatalog;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;
import data.scripts.ui.widget.provider.BattleNext_FleetMemberProvider;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_VirtualSelectionTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@CsvSource({"600,2", "600,3", "310,2", "310,3"})
	void footerButtonsUseTheirCountAndLeaveOneGapBeforeStatus(int width, int count) {
		UIRect footer = new UIRect(0f, 0f, width, 30f);
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footer, count);
		UIRect last = BattleNext_PageLayout.managementFooterButtonRect(footer, buttonWidth, count - 1);
		UIRect status = BattleNext_PageLayout.managementFooterStatusRect(footer, buttonWidth, count);
		assertTrue(last.getX() + last.getWidth() <= width);
		assertEquals(BattleNext_PageLayout.MANAGEMENT_FOOTER_BUTTON_GAP,
				status.getX() - last.getX() - last.getWidth(), 1f);
	}

	@ParameterizedTest
	@CsvSource({"400,145,28,20", "800,190,40,20", "400,145,28,56"})
	void actionRowUsesButtonAndTextBoundsAndTheParentInnerEdge(int width, int buttonWidth, int buttonHeight, int textHeight) {
		UIRect parent = new UIRect(0f, 0f, width, 600f);
		UIRect actions = BattleNext_PageLayout.managementActionsRect(parent, Math.max(buttonHeight, textHeight));
		UIRect button = BattleNext_PageLayout.managementDeleteButtonLocalRect(actions, buttonWidth, buttonHeight);
		UIRect note = BattleNext_PageLayout.managementSourceNoteLocalRect(actions, button);
		UIRect editor = BattleNext_PageLayout.managementEditorRect(parent, actions);
		assertEquals(BattleNext_PageLayout.SECTION_GAP, note.getX() - button.getX() - button.getWidth());
		assertEquals(width - BattleNext_PageLayout.PAGE_CONTENT_INSET, note.getX() + note.getWidth());
		assertEquals(actions.getY() + actions.getHeight() * 0.5f, button.getY() + button.getHeight() * 0.5f);
		assertEquals(BattleNext_PageLayout.SECTION_GAP, actions.getY() - editor.getY() - editor.getHeight());
		assertEquals(BattleNext_PageLayout.PAGE_CONTENT_INSET, parent.getHeight() - actions.getY() - actions.getHeight());
	}

	@ParameterizedTest
	@CsvSource({"600,20", "1000,20", "600,56"})
	void managedPageMeasuresItsBottomRowAndCentersTheSourceNote(int width, int lineHeight) throws Exception {
		paragraphLineHeight = lineHeight;
		shortenLabels = true;
		BattleNext_FleetModel model = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), List.of());
		BattleNext_FleetStore store = new BattleNext_FleetStore() {
			@Override
			public void ensureLoaded() {

			}

			@Override
			public BattleNext_FleetModel get(String id) {
				return model;
			}

			@Override
			public List<BattleNext_FleetModel> getAllCopy() {
				return List.of(model);
			}
		};
		BattleNext_FleetLibraryPage page = new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (text, action) -> {},
				store, new BattleNext_VariantCatalog(new BattleNext_PinnedVariantStore())) {
			@Override
			protected String sourceNote() {
				return "来源说明";
			}
		};
		CustomPanelAPI root = panel(width, 700f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		page.buttonPressed("fleet:fleet");
		CustomPanelAPI detail = (CustomPanelAPI) getField(page, "detailPanel");
		TooltipMakerAPI note = children.get(detail).stream().filter(TooltipMakerAPI.class::isInstance)
				.map(TooltipMakerAPI.class::cast).filter(content -> tooltips.get(content).getComponents().stream()
						.anyMatch(component -> component instanceof LabelAPI label && label.getText().equals("来源说明")))
				.findFirst().orElseThrow();
		LabelAPI label = (LabelAPI) tooltips.get(note).getComponents().get(0);
		PositionAPI button = buttonPositions.get("delete");
		assertEquals(button.getX() + button.getWidth() + BattleNext_PageLayout.SECTION_GAP, label.getPosition().getX());
		assertEquals(button.getCenterY(), label.getPosition().getCenterY());
		assertEquals(detail.getPosition().getX() + detail.getPosition().getWidth() - BattleNext_PageLayout.SECTION_GAP,
				note.getPosition().getX() + note.getPosition().getWidth());
		page.close();
	}

	@Test
	void defaultListTooltipMeasuresItsBodyAndKeepsTheButtonAsItsTarget() {
		CustomPanelAPI root = panel(1000f, 500f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = root.createUIElement(600f, 400f, true);
		String text = "Measured title ".repeat(3);
		ButtonAPI button = BattleNext_PageLayout.listButton(content, text, "measured", 500f, null);
		TooltipMakerAPI.TooltipCreator creator = attachedTooltips.get(button);
		assertNotNull(creator);
		TooltipMakerAPI body = tooltip(creator.getTooltipWidth(null), 0f, false);
		creator.createTooltip(body, false, null);
		assertEquals(text, ((LabelAPI) tooltips.get(body).getComponents().get(0)).getText());
		assertEquals(315f + BattleNext_PageLayout.SECTION_GAP * 2f, creator.getTooltipWidth(null));
		assertSame(button, content.getPrev());
		assertEquals(1, tooltips.get(content).getComponents().size());
	}

	@Test
	void distantExpandedMembersKeepTheirMeasuredExtentThroughCopyMoveAndRemoval() throws Exception {
		registeredVariants.put("parent_variant", source().getApi());
		List<BattleNext_MemberModel> members = IntStream.range(0, 128).mapToObj(index ->
				new BattleNext_MemberModel("parent_variant", "Ship " + index, "", List.of(), "", "", List.of())).toList();
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), members), false);
		attachEditor(editor, panel(1000f, 340f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:126");
		editor.buttonPressed("toggle_member:127");
		TooltipMakerAPI viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertEquals(viewport.getHeightSoFar(), tooltips.get(viewport).getMountedHeight());
		viewport.getExternalScroller().setYOffset(viewport.getHeightSoFar());
		editor.advance(0f);
		Object fields = ((List<?>) getField(editor, "memberFields")).get(127);
		((TextFieldAPI) getField(fields, "nameField")).setText("Edited tail");
		for (String action : List.of("copy_member:127", "move_member_up:128", "remove_member:128")) {
			editor.buttonPressed(action);
			viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
			assertEquals(viewport.getHeightSoFar(), tooltips.get(viewport).getMountedHeight());
			viewport.getExternalScroller().setYOffset(viewport.getHeightSoFar());
			editor.advance(0f);
			assertEquals(viewport.getHeightSoFar() - 340f, viewport.getExternalScroller().getYOffset());
		}
		BattleNext_FleetModel draft = (BattleNext_FleetModel) getField(editor, "draft");
		assertEquals(128, draft.getMembersReadonly().size());
		assertEquals("Edited tail", draft.getMembersReadonly().get(127).getName());
		assertTrue(editor.hasUnsavedChanges());
		editor.close();
	}

	@Test
	void scrollingPreservesMemberEditsAndFormattingIncludesCollapsedMembers() throws Exception {

		registeredVariants.put("parent_variant", source().getApi());
		List<BattleNext_MemberModel> members = IntStream.range(0, 128).mapToObj(index ->
				new BattleNext_MemberModel("parent_variant", "Ship " + index, "", List.of(), "", "", List.of())).toList();
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), members), false);
		attachEditor(editor, panel(1000f, 400f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:0");
		Object first = ((List<?>) getField(editor, "memberFields")).get(0);
		((TextFieldAPI) getField(first, "nameField")).setText("Draft row");
		BattleNext_FleetModel draft = (BattleNext_FleetModel) getField(editor, "draft");
		AtomicInteger distantReads = new AtomicInteger();
		BattleNext_MemberModel distant = new BattleNext_MemberModel("parent_variant", "Distant", "", List.of(), "", "", List.of()) {
			@Override
			public String getName() {
				distantReads.incrementAndGet();
				return super.getName();
			}
		};
		((List<BattleNext_MemberModel>) getField(draft, "members")).set(127, distant);
		TooltipMakerAPI viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		viewport.getExternalScroller().setYOffset(3000f);
		editor.advance(0f);
		viewport.getExternalScroller().setYOffset(0f);
		editor.advance(0f);
		assertEquals(0, distantReads.get());
		assertEquals("Draft row", draft.getMembersReadonly().get(0).getName());
		assertNull(((List<?>) getField(editor, "memberFields")).get(127));

		((TextFieldAPI) getField(editor, "variantPrefixField")).setText("audit");
		editor.buttonPressed("format_ids");
		draft = (BattleNext_FleetModel) getField(editor, "draft");
		assertEquals(128, draft.getMembersReadonly().size());
		assertTrue(draft.getMembersReadonly().stream().allMatch(member -> member.getVariantId().startsWith("audit_")), editor.getStatusText());
		assertEquals("Draft row", draft.getMembersReadonly().get(0).getName());
		editor.close();
		assertTrue(((List<?>) getField(editor, "memberFields")).isEmpty());
	}

	@Test
	void fleetKeepsItsScrollContainerStableWhileMountingAndReleasingRows() throws Exception {

		registeredVariants.put("parent_variant", source().getApi());
		List<BattleNext_MemberModel> members = IntStream.range(0, 128).mapToObj(index ->
				new BattleNext_MemberModel("parent_variant", "Ship " + index, "", List.of(), "", "", List.of())).toList();
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), members), false);
		attachEditor(editor, panel(1000f, 400f, new BaseCustomUIPanelPlugin()));
		TooltipMakerAPI viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		ScrollPanelAPI scroller = viewport.getExternalScroller();
		List<UIComponentAPI> mounted = List.copyOf(tooltips.get(viewport).getComponents());
		for (float offset : new float[]{0f, 1000f, 2200f, 1000f, 0f, 1000f, 2200f, 0f}) {
			scroller.setYOffset(offset);
			editor.advance(0f);
			assertEquals(mounted, tooltips.get(viewport).getComponents());
			Map<Integer, CustomPanelAPI> current = (Map<Integer, CustomPanelAPI>) getField(editor, "memberPanels");
			List<CustomPanelAPI> cards = current.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
			assertTrue(cards.size() < members.size());
			for (int index = 1; index < cards.size(); index++) {
				assertEquals(0f, cards.get(index - 1).getPosition().getY()
						- cards.get(index).getPosition().getY() - cards.get(index).getPosition().getHeight(), 0.001f);
			}
		}
		editor.close();
	}

	@ParameterizedTest
	@CsvSource({"1,340,0", "30,340,29", "128,400,127", "30,340,20", "30,700,29"})
	void expandingThenCollapsingAtTheBottomPreservesInputsAndKeepsTheEndReachable(int count, int height, int member) throws Exception {

		registeredVariants.put("parent_variant", source().getApi());
		List<BattleNext_MemberModel> members = IntStream.range(0, count).mapToObj(index ->
				new BattleNext_MemberModel("parent_variant", "Ship " + index, "", List.of(), "", "", List.of())).toList();
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), members), false);
		attachEditor(editor, panel(1000f, height, new BaseCustomUIPanelPlugin()));
		TooltipMakerAPI viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		viewport.getExternalScroller().setYOffset(member * 44f);
		editor.advance(0f);
		editor.buttonPressed("toggle_member:" + member);
		((TextFieldAPI) getField(editor, "nameField")).setText("Editing fleet");
		((TextFieldAPI) getField(editor, "nameField")).grabFocus(false);
		editor.advance(0f);
		assertEquals("Editing fleet", ((TextFieldAPI) getField(editor, "nameField")).getText());
		assertTrue(((TextFieldAPI) getField(editor, "nameField")).hasFocus());
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertEquals(viewport.getHeightSoFar(), tooltips.get(viewport).getMountedHeight());
		viewport.getExternalScroller().setYOffset(viewport.getHeightSoFar());
		editor.advance(0f);
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertEquals(Math.max(0f, viewport.getHeightSoFar() - height), viewport.getExternalScroller().getYOffset());
		PositionAPI button = ((ButtonAPI) getField(editor, "addMemberButton")).getPosition();
		assertEquals(viewport.getPosition().getY() + viewport.getPosition().getHeight() - viewport.getHeightSoFar() + 8f, button.getY());
		float expandedHeight = viewport.getHeightSoFar();

		viewport.getExternalScroller().setYOffset(member * 44f);
		editor.advance(0f);
		Object fields = ((List<?>) getField(editor, "memberFields")).get(member);
		((TextFieldAPI) getField(fields, "nameField")).setText("Edited member");
		((TextFieldAPI) getField(fields, "tagsField")).setText("tail,edited");
		viewport.getExternalScroller().setYOffset(viewport.getHeightSoFar());
		editor.advance(0f);
		editor.buttonPressed("toggle_member:" + member);
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertTrue(viewport.getHeightSoFar() < expandedHeight);
		assertEquals(Math.max(0f, viewport.getHeightSoFar() - height), viewport.getExternalScroller().getYOffset());
		assertEquals(viewport.getHeightSoFar(), tooltips.get(viewport).getMountedHeight());
		button = ((ButtonAPI) getField(editor, "addMemberButton")).getPosition();
		assertEquals(viewport.getPosition().getY() + viewport.getPosition().getHeight() - viewport.getHeightSoFar() + 8f, button.getY());

		editor.buttonPressed("toggle_member:" + member);
		editor.advance(0f);
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		viewport.getExternalScroller().setYOffset(member * 44f);
		editor.advance(0f);
		fields = ((List<?>) getField(editor, "memberFields")).get(member);
		assertEquals("Edited member", ((TextFieldAPI) getField(fields, "nameField")).getText());
		assertEquals("tail,edited", ((TextFieldAPI) getField(fields, "tagsField")).getText());
		editor.close();
	}

	@Test
	void appendingAnExpandedMemberMeasuresItsHeightBeforeTheBottomIsReached() throws Exception {

		registeredVariants.put("parent_variant", source().getApi());
		List<BattleNext_MemberModel> members = IntStream.range(0, 30).mapToObj(index ->
				new BattleNext_MemberModel("parent_variant", "Ship " + index, "", List.of(), "", "", List.of())).toList();
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), members), false);
		attachEditor(editor, panel(1000f, 340f, new BaseCustomUIPanelPlugin()));
		TooltipMakerAPI viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		viewport.getExternalScroller().setYOffset(viewport.getHeightSoFar());
		editor.advance(0f);
		addFleetMembers(editor, List.of("parent_variant"));
		editor.advance(0f);
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertEquals(viewport.getHeightSoFar(), tooltips.get(viewport).getMountedHeight());
		viewport.getExternalScroller().setYOffset(viewport.getHeightSoFar());
		editor.advance(0f);
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertEquals(viewport.getHeightSoFar() - 340f, viewport.getExternalScroller().getYOffset());
		PositionAPI button = ((ButtonAPI) getField(editor, "addMemberButton")).getPosition();
		assertEquals(viewport.getPosition().getY() + viewport.getPosition().getHeight() - viewport.getHeightSoFar() + 8f, button.getY());
		editor.buttonPressed("toggle_member:30");
		viewport = (TooltipMakerAPI) getField(editor, "memberViewport");
		assertEquals(viewport.getHeightSoFar() - 340f, viewport.getExternalScroller().getYOffset());
		editor.close();
	}

	@Test
	void addingAcrossHullsAndPinnedPreservesCountsThroughNavigationUntilConfirmation() throws Exception {

		ShipVariantAPI rootVariant = source().getApi();
		registeredVariants.put("parent_variant", rootVariant);
		registeredVariants.put("child_variant", rootVariant.getModuleVariant("module"));
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
					if (method.getName().equals("getAllVariantIds")) return List.of("parent_variant", "child_variant");
					try {
						return method.invoke(delegate, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}));
		BattleNext_PinnedVariantStore pins = new BattleNext_PinnedVariantStore();
		pins.reload();
		String pinned = "pinned:" + pins.saveVariant("Pinned", rootVariant).getPinId();
		AtomicReference<List<String>> added = new AtomicReference<>();
		CustomPanelAPI owner = panel(1000f, 800f, new BaseCustomUIPanelPlugin());
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(new BattleNext_UiInputRouter(), owner,
				new BattleNext_FleetMemberProvider(pins), BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, added::set);
		picker.openAt(owner, () -> new BattleNext_OptionPicker.Anchor(200f, 400f, 200f, 30f));
		clickPickerOption(picker, "parent_variant");
		clickPickerOption(picker, "parent_variant");
		clickPickerOption(picker, "child_variant");
		clickPickerOption(picker, pinned);
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		popup.getPlugin().buttonPressed("browse:hulls");
		popup.getPlugin().buttonPressed("browse:selected");
		assertEquals(Map.of("parent_variant", 2, "child_variant", 1, pinned, 1), getField(picker, "selection"));
		assertNull(added.get());

		popup.getPlugin().buttonPressed(buttonIds.get("CONFIRM"));
		assertEquals(4, added.get().size());
		assertEquals(2, Collections.frequency(added.get(), "parent_variant"));
		assertEquals(1, Collections.frequency(added.get(), pinned));
		picker.close();
	}

	@Test
	void longTableKeepsFieldsFocusAndCheckboxValuesThroughScrollingAndStateRestore() throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(1400f, 300f, new BaseCustomUIPanelPlugin());
		List<BattleNext_QuickBattleModel> entries = IntStream.range(0, 100)
				.mapToObj(index -> new BattleNext_QuickBattleModel("alpha", "beta", -1, 1f)).toList();
		BattleNext_QuickBattleDisplay.Presentation presentation = new BattleNext_QuickBattleDisplay.Presentation(
				List.of(Column.PLAYER, Column.ENEMY, Column.MAP, Column.SCALE, Column.STATUS, Column.ENABLED, Column.ACTIONS),
				List.of(0.25f, 0.25f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f), true,
				row -> new BattleNext_QuickBattleDisplay.Action("Delete", row.getIndex(), true), ignored -> { });
		BattleNext_FleetStore fleets = new BattleNext_FleetStore();
		fleets.reload();
		BattleNext_QuickBattleDisplay display = new BattleNext_QuickBattleDisplay(router, entries, presentation,
				entry -> entry, row -> List.of(), fleets, owner);
		display.attach(owner);
		BattleNext_TableWidget table = (BattleNext_TableWidget) getField(display, "tableWidget");
		VisibleRows visible = (VisibleRows) getField(table, "visibleRows");
		ScrollPanelAPI scroller = table.getContent().getExternalScroller();
		BattleNext_QuickBattleDisplay.Row first = display.getRowsReadonly().get(0);
		TextFieldAPI originalField = first.getField(Column.MAP);
		first.getField(Column.MAP).setText("pending");
		first.setEnabled(false);
		first.getField(Column.MAP).grabFocus(false);
		scroller.setYOffset(2500f);
		visible.update(scroller.getYOffset(), scroller.getPosition().getHeight());
		assertSame(originalField, first.getField(Column.MAP));
		assertTrue(first.getField(Column.MAP).hasFocus());

		router.releaseFocus();
		visible.update(scroller.getYOffset(), scroller.getPosition().getHeight());
		assertNull(first.getField(Column.MAP));
		assertEquals("pending", first.text(Column.MAP));
		assertFalse(first.isEnabled());

		BattleNext_QuickBattleDisplay.ViewState state = display.captureState();
		scroller.setYOffset(0f);
		visible.update(0f, scroller.getPosition().getHeight());
		first.getField(Column.MAP).setText("changed");
		first.setEnabled(true);
		display.restoreState(state);
		scroller.setYOffset(0f);
		assertEquals("pending", first.getField(Column.MAP).getText());
		assertFalse(first.isEnabled());
		display.close();
	}
}
