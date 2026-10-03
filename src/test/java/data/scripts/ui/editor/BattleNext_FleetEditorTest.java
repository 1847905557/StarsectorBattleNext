package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

class BattleNext_FleetEditorTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@CsvSource({"0,false", "1,false", "0,true", "1,true"})
	void memberRemovalKeepsSurvivorFieldsBoundToTheirOwnData(int removed, boolean collapsed) throws Exception {

		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		editor.attach(root);
		for (int index = 0; index < 3; index++) {
			editor.buttonPressed("add_member");
		}
		List<?> fields = (List<?>) getField(editor, "memberFields");
		List<String> names = List.of("first", "second", "third");
		for (int index = 0; index < names.size(); index++) {
			((TextFieldAPI) getField(fields.get(index), "name")).setText(names.get(index));
			((TextFieldAPI) getField(fields.get(index), "orders")).setText("orders_" + names.get(index));
		}
		if (collapsed) editor.buttonPressed("toggle_member:2");
		editor.buttonPressed("remove_member:" + removed);
		BattleNext_FleetModel draft = (BattleNext_FleetModel) getField(editor, "draft");
		List<String> expected = IntStream.range(0, names.size()).filter(index -> index != removed)
				.mapToObj(names::get).toList();
		assertEquals(expected, draft.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		assertEquals(expected.stream().map(name -> "orders_" + name).toList(),
				draft.getMembers().stream().map(BattleNext_MemberModel::getOrders).toList());
		editor.refreshContent();
		editor.closePicker();
		assertEquals(expected, draft.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		assertTrue(router.isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"1000, 12", "1000, 17", "1000, 20", "320, 17"})
	void memberFieldsShareMetadataSpacingAndMeasureTheirExpandedHeight(int width, int lineHeight) throws Exception {
		paragraphLineHeight = lineHeight;
		deferTooltipWidth = true;
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(width, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		editor.attach(root);
		editor.buttonPressed("add_member");
		TooltipMakerAPI metadata = (TooltipMakerAPI) getField(getField(editor, "id"), "host");
		assertFieldFlow(metadata, 2, Set.of(5));
		List<BattleNext_OptionField> pickers = (List<BattleNext_OptionField>) getField(editor, "memberPickers");
		BattleNext_OptionField variant = pickers.get(0);
		TooltipMakerAPI content = (TooltipMakerAPI) getField(variant, "host");
		CustomPanelAPI body = (CustomPanelAPI) getField(variant, "panelOwner");
		assertEquals(1L, plugins.values().stream().filter(plugin -> plugin == body.getPlugin()).count());
		assertFieldFlow(content, 1, Set.of(0, 2, 3, 4));
		assertEquals(tooltips.get(content).getUsedHeight(), tooltips.get(content).getHeightAtMount());
		assertEquals(tooltips.get(content).getMountedHeight() + 16f, body.getPosition().getHeight());
		PositionAPI member = geometry.get(body.getPosition()).getParent();
		assertEquals(BattleNext_ExpandablePanel.HEADER_HEIGHT + body.getPosition().getHeight(), member.getHeight());
		List<UIComponentAPI> components = tooltips.get(content).getComponents();
		PositionAPI heading = components.get(0).getPosition();
		assertEquals(member.getY() + member.getHeight() - BattleNext_ExpandablePanel.HEADER_HEIGHT - 8f,
				heading.getY() + heading.getHeight());
		PositionAPI bounds = tooltips.get(content).getViewport();
		for (UIComponentAPI component : components) {
			PositionAPI position = component.getPosition();
			assertTrue(position.getY() >= bounds.getY());
			assertTrue(position.getY() + position.getHeight() <= bounds.getY() + bounds.getHeight());
		}
		PositionAPI remove = components.get(15).getPosition();
		assertEquals(body.getPosition().getY() + 8f, remove.getY());
		TooltipMakerAPI viewport = ((TooltipMakerAPI[]) getField(variant, "viewports"))[0];
		List<UIComponentAPI> memberRows = tooltips.get(viewport).getComponents();
		assertEquals(memberRows.get(0).getPosition().getHeight() + BattleNext_ExpandablePanel.ROW_GAP
				+ member.getHeight() + BattleNext_PageLayout.BUTTON_VERTICAL_GAP + BattleNext_PageLayout.FOOTER_HEIGHT,
				tooltips.get(viewport).getMountedHeight());
		List<?> members = (List<?>) getField(editor, "memberFields");
		((TextFieldAPI) getField(members.get(0), "name")).setText("compact ship");
		editor.buttonPressed("toggle_member:0");
		assertEquals(7, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		editor.buttonPressed("toggle_member:0");
		members = (List<?>) getField(editor, "memberFields");
		assertEquals("compact ship", ((TextFieldAPI) getField(members.get(0), "name")).getText());
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		pickers = (List<BattleNext_OptionField>) getField(editor, "memberPickers");
		content = (TooltipMakerAPI) getField(pickers.get(0), "host");
		assertFieldFlow(content, 1, Set.of(0, 2, 3, 4));
		assertEquals(tooltips.get(content).getUsedHeight(), tooltips.get(content).getHeightAtMount());
	}

	private void assertFieldFlow(TooltipMakerAPI content, int firstLabel, Set<Integer> pickerIndices) {
		List<UIComponentAPI> components = tooltips.get(content).getComponents();
		PositionAPI firstInput = components.get(firstLabel + 1).getPosition();
		PositionAPI previous = components.get(firstLabel - 1).getPosition();
		for (int index = 0; index < 7; index++) {
			PositionAPI label = components.get(firstLabel + index * 2).getPosition();
			PositionAPI input = components.get(firstLabel + index * 2 + 1).getPosition();
			assertEquals(5f, previous.getY() - label.getY() - label.getHeight());
			assertEquals(pickerIndices.contains(index) ? 2f : 0f,
					label.getY() - input.getY() - input.getHeight());
			assertEquals(BattleNext_PageLayout.FORM_FIELD_HEIGHT, input.getHeight());
			assertEquals(firstInput.getX(), input.getX());
			assertEquals(firstInput.getX() + firstInput.getWidth(), input.getX() + input.getWidth());
			previous = input;
		}
	}

	@Test
	void editorAttachRefreshAndInputLifecycle() throws Exception {
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(null, false);
		editor.setInputRouter(new BattleNext_UiInputRouter());
		CustomPanelAPI panel = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.attach(panel);
		assertNotNull(editor.getStatusText());
		editor.refreshContent();
	}

	@Test
	void allFiveFleetSelectorsUseExplicitWidthsRootHostAndTheirViewport() throws Exception {
		deferTooltipWidth = true;
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		editor.attach(root);
		((TextFieldAPI) getField(editor, "id")).setText("draft_id");
		editor.buttonPressed("add_member");
		float usableWidth = 1000f - BattleNext_PageLayout.EDITOR_COLUMN_GAP;
		float metaWidth = (float) Math.floor(usableWidth * BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		BattleNext_OptionField skills = (BattleNext_OptionField) getField(editor, "skillsPicker");
		assertEquals(Math.max(72f, metaWidth - 24f), getField(skills, "width"));
		assertSame(root, getField(skills, "overlayHost"));
		List<BattleNext_OptionField> members = List.copyOf((List<BattleNext_OptionField>) getField(editor, "memberPickers"));
		assertEquals(4, members.size());
		for (BattleNext_OptionField member : members) {
			assertEquals(Math.max(72f, usableWidth - metaWidth - 16f - 24f), getField(member, "width"));
			assertSame(root, getField(member, "overlayHost"));
			assertEquals(1, ((TooltipMakerAPI[]) getField(member, "viewports")).length);
		}
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		editor.closePicker();
		assertEquals("draft_id", editor.getDraftId());
		editor.refreshContent();
		assertEquals("draft_id", editor.getDraftId());
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		assertTrue(((List<?>) getField(skills, "ownedPanels")).isEmpty());
		for (BattleNext_OptionField member : members) {
			assertTrue(((List<?>) getField(member, "ownedPanels")).isEmpty());
		}
	}
}
