package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BattleNext_RuleEditorTest extends BattleNext_TestPageFixture {
	private Object previousEntries;
	private Object previousFactory;

	@BeforeEach
	void installRestrictionChoices() throws Exception {
		var registry = BattleNext_RestrictionRegistry.getInstance();
		previousEntries = getField(registry, "entries");
		previousFactory = getField(registry, "scriptFactory");
		registry.load(new JSONArray()
				.put(new JSONObject().put("id", "limitNumber").put("type", "number").put("script", "number").put("desc", "Number limit"))
				.put(new JSONObject().put("id", "limitList").put("type", "list").put("script", "list").put("desc", "Item limit")),
				name -> (BattleNext_RestrictionScript) (fleet, restriction) -> List.of());
	}

	@AfterEach
	void restoreRestrictionChoices() throws Exception {
		var registry = BattleNext_RestrictionRegistry.getInstance();
		Field entries = registry.getClass().getDeclaredField("entries");
		entries.setAccessible(true);
		entries.set(registry, previousEntries);
		Field factory = registry.getClass().getDeclaredField("scriptFactory");
		factory.setAccessible(true);
		factory.set(registry, previousFactory);
	}

	@Test
	void pickerConfirmationAddsRowsPreservesExistingInputsAndSupportsSaveDiscard() throws Exception {

		BattleNext_RuleStore store = new BattleNext_RuleStore();
		store.reload();
		BattleNext_RuleEditor editor = new BattleNext_RuleEditor(store);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		editor.setInputRouter(router);
		editor.open(null);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("draft_rule");
		((TextFieldAPI) getField(editor, "nameField")).setText("Draft rule");
		confirmRestrictions(editor, List.of("limitNumber"));
		Map<String, TextFieldAPI> values = (Map<String, TextFieldAPI>) getField(editor, "valueFields");
		Map<String, ButtonAPI> enabled = (Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes");
		values.get("limitNumber").setText("12");
		enabled.get("limitNumber").setChecked(false);
		confirmRestrictions(editor, List.of("limitList"));
		values = (Map<String, TextFieldAPI>) getField(editor, "valueFields");
		enabled = (Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes");
		assertEquals(Set.of("limitNumber", "limitList"), values.keySet());
		assertEquals(values.keySet(), enabled.keySet());
		assertEquals("12", values.get("limitNumber").getText());
		assertFalse(enabled.get("limitNumber").isChecked());
		assertTrue(enabled.get("limitList").isChecked());
		values.get("limitList").setText("laser,pulse");
		editor.buttonPressed("rule:up:1");
		BattleNext_RuleModel draft = (BattleNext_RuleModel) getField(editor, "draft");
		assertEquals(List.of("limitList", "limitNumber"), draft.getRestrictions().stream().map(entry -> entry.getId()).toList());
		assertEquals(List.of("laser", "pulse"), draft.getRestriction("limitList").getItems());
		assertTrue(editor.hasUnsavedChanges());
		assertTrue(editor.save(), editor.getStatusText());
		store.reload();
		assertEquals(draft.toJson().toString(), store.get("draft_rule").toJson().toString());
		editor.buttonPressed("rule:remove:0");
		assertEquals(List.of("limitNumber"), draft.getRestrictions().stream().map(entry -> entry.getId()).toList());
		editor.discard();
		assertFalse(editor.hasUnsavedChanges());
		assertEquals(Set.of("limitNumber", "limitList"), ((Map<?, ?>) getField(editor, "valueFields")).keySet());
		assertTrue(router.isEmpty());
	}

	@Test
	void refreshRetainsUnfinishedInputAndRepeatedAddRemoveReleasesOldBindings() throws Exception {

		BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		editor.open(null);
		editor.setInputRouter(router);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		confirmRestrictions(editor, List.of("limitNumber"));
		Map<String, TextFieldAPI> values = (Map<String, TextFieldAPI>) getField(editor, "valueFields");
		values.get("limitNumber").setText("pending");
		((Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes")).get("limitNumber").setChecked(false);
		editor.refreshContent();
		values = (Map<String, TextFieldAPI>) getField(editor, "valueFields");
		assertEquals("pending", values.get("limitNumber").getText());
		assertFalse(((Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes")).get("limitNumber").isChecked());
		values.get("limitNumber").setText("12");
		int registrations = ((Map<?, ?>) getField(router.getSession(), "registrations")).size();
		for (int iteration = 0; iteration < 20; iteration++) {
			confirmRestrictions(editor, List.of("limitList"));
			editor.buttonPressed("rule:remove:1");
			assertEquals(registrations, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
			assertEquals("12", ((Map<String, TextFieldAPI>) getField(editor, "valueFields")).get("limitNumber").getText());
			assertTrue(router.isEmpty());
		}
	}

	@Test
	void registeredReadonlyRestrictionBuildsEditableBindingsAfterRegistryReload() throws Exception {

		BattleNext_RuleModel source = new BattleNext_RuleModel("rule", "Rule");
		BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction("lateLimit", "number");
		restriction.setEnabled(true);
		restriction.setValue(9d);
		source.setRestrictions(List.of(restriction));
		BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
		editor.open(source);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		assertTrue(((Map<?, ?>) getField(editor, "valueFields")).isEmpty());
		BattleNext_RestrictionRegistry.getInstance().load(new JSONArray().put(new JSONObject()
				.put("id", "lateLimit").put("type", "number").put("script", "late").put("desc", "Late limit")),
				name -> (BattleNext_RestrictionScript) (fleet, value) -> List.of());
		editor.refreshContent();
		assertEquals("9.0", ((Map<String, TextFieldAPI>) getField(editor, "valueFields")).get("lateLimit").getText());
		assertTrue(((Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes")).get("lateLimit").isChecked());
		assertFalse(editor.hasUnsavedChanges());
	}

	private void confirmRestrictions(BattleNext_RuleEditor editor, List<String> ids) throws Exception {
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(editor, "restrictionPicker");
		Method open = BattleNext_OptionPicker.class.getDeclaredMethod("open");
		open.setAccessible(true);
		open.invoke(picker);
		Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
		for (String id : ids) {
			selection.put(id, 1);
		}
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		plugins.get(popup).buttonPressed(buttonIds.get("CONFIRM"));
		assertNull(getField(picker, "popup"));
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(ints = {180, 1000})
	void restrictionButtonUsesTableWidthTitleAlignmentAndNestedViewports(int width) throws Exception {
		deferTooltipWidth = true;
		BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(width, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null);
		editor.setInputRouter(router);
		attachEditor(editor, root);
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(editor, "restrictionPicker");
		BattleNext_TableWidget table = (BattleNext_TableWidget) getField(editor, "tableWidget");
		assertTablePickerAlignment(table, picker);
		float rowWidth = table.getPanel().getPosition().getWidth();
		assertEquals(Math.min(190f, rowWidth), getField(picker, "buttonWidth"));
		assertEquals(30f, getField(picker, "buttonHeight"));
		assertEquals("BattleNext_rule_add_restriction", buttonLabels.get("TOGGLE"));
		assertSame(root, getField(picker, "overlayHost"));
		TooltipMakerAPI[] viewports = (TooltipMakerAPI[]) getField(picker, "viewports");
		assertEquals(2, viewports.length);
		assertSame(table.getContent(), viewports[0]);
		((TextFieldAPI) getField(editor, "idField")).setText("rule_draft");
		editor.refreshContent();
		assertEquals("rule_draft", editor.getDraftId());
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
		assertEquals(2, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		assertTablePickerAlignment((BattleNext_TableWidget) getField(editor, "tableWidget"),
				(BattleNext_OptionPicker) getField(editor, "restrictionPicker"));
		confirmRestrictions(editor, List.of("limitNumber", "limitList"));
		assertTablePickerAlignment((BattleNext_TableWidget) getField(editor, "tableWidget"),
				(BattleNext_OptionPicker) getField(editor, "restrictionPicker"));
		editor.buttonPressed("rule:remove:0");
		assertTablePickerAlignment((BattleNext_TableWidget) getField(editor, "tableWidget"),
				(BattleNext_OptionPicker) getField(editor, "restrictionPicker"));
		editor.buttonPressed("rule:remove:0");
		assertTablePickerAlignment((BattleNext_TableWidget) getField(editor, "tableWidget"),
				(BattleNext_OptionPicker) getField(editor, "restrictionPicker"));
		editor.close();
		assertTrue(children.get(root).isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
	}
}
