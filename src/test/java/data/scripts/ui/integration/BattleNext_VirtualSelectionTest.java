package data.scripts.ui.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;
import data.scripts.ui.widget.provider.BattleNext_FleetMemberProvider;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

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
