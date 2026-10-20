package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.provider.BattleNext_RestrictionSourceProvider;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BattleNext_RuleEditorTest extends BattleNext_TestPageFixture {
	private Object previousEntries;
	private Object previousFactory;
	private Object previousLoadErrors;

	@BeforeEach
	void installRestrictionChoices() throws Exception {
		var registry = BattleNext_RestrictionRegistry.getInstance();
		previousEntries = getField(registry, "entries");
		previousFactory = getField(registry, "scriptFactory");
		previousLoadErrors = getField(registry, "loadErrors");
		registry.load(new JSONArray()
				.put(new JSONObject().put("id", "limitNumber").put("name", "Ship limit").put("type", "number").put("script", "number").put("desc", "Number limit"))
				.put(new JSONObject().put("id", "limitList").put("name", "Equipment filter").put("type", "list").put("script", "list").put("desc", "Item limit")),
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
		Field loadErrors = registry.getClass().getDeclaredField("loadErrors");
		loadErrors.setAccessible(true);
		loadErrors.set(registry, previousLoadErrors);
	}

	@Test
	void saveAggregatesNameIdAndNumericRestrictionErrorsBeforePersistence() throws Exception {
		var store = new BattleNext_RuleStore();
		store.reload();
		var model = new BattleNext_RuleModel("rule", "Rule");
		var first = new BattleNext_RuleRestriction("limitNumber", "number");
		var second = new BattleNext_RuleRestriction("anotherNumber", "number");
		model.setRestrictions(List.of(first, second));
		BattleNext_RestrictionRegistry.getInstance().load(new JSONArray()
				.put(new JSONObject().put("id", "limitNumber").put("name", "First").put("type", "number").put("script", "number").put("desc", "First limit"))
				.put(new JSONObject().put("id", "anotherNumber").put("name", "Second").put("type", "number").put("script", "number").put("desc", "Second limit")),
				name -> (BattleNext_RestrictionScript) (fleet, restriction) -> List.of());
		var editor = new BattleNext_RuleEditor(store);
		editor.open(model);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("bad?id");
		((TextFieldAPI) getField(editor, "nameField")).setText("");
		Map<String, TextFieldAPI> values = (Map<String, TextFieldAPI>) getField(editor, "valueFields");
		values.get("limitNumber").setText("first-invalid");
		values.get("anotherNumber").setText("second-invalid");
		assertFalse(editor.save());
		assertEquals(4, editor.getSaveErrors().size());
		assertTrue(editor.getSaveErrors().get(0).contains(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_name_required")));
		assertTrue(editor.getSaveErrors().get(1).contains("U+003F"));
		assertTrue(editor.getSaveErrors().get(2).contains("limitNumber = \"first-invalid\""));
		assertTrue(editor.getSaveErrors().get(3).contains("anotherNumber = \"second-invalid\""));
		assertTrue(store.getAll().isEmpty());
		editor.discard();
		assertTrue(editor.getSaveErrors().isEmpty());
		assertFalse(editor.hasUnsavedChanges());
		editor.close();
	}

	@Test
	void restrictionChoicesShowNamesAndDescriptionsAndFollowCurrentDraft() throws Exception {

		BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
		editor.open(null);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(editor, "restrictionPicker");
		BattleNext_RestrictionSourceProvider source = (BattleNext_RestrictionSourceProvider) getField(picker, "source");
		List<BattleNext_OptionPicker.OptionChoice> options = source.getOptions();
		assertEquals(List.of("limitNumber", "limitList"), options.stream().map(BattleNext_OptionPicker.OptionChoice::getId).toList());
		assertEquals(List.of("Ship limit", "Equipment filter"), options.stream().map(BattleNext_OptionPicker.OptionChoice::getLabel).toList());
		List<List<String>> expected = List.of(List.of("Ship limit", "limitNumber  [number]", "Number limit"),
				List.of("Equipment filter", "limitList  [list]", "Item limit"));
		for (int index = 0; index < options.size(); index++) {
			TooltipMakerAPI tooltip = tooltip(source.getTooltipWidth(), 0f, false);
			source.createTooltip(tooltip, options.get(index));
			assertEquals(expected.get(index), tooltips.get(tooltip).getComponents().stream()
					.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
		}
		confirmRestrictions(editor, List.of("limitNumber"));
		assertEquals(List.of("limitList"), source.getOptions().stream().map(BattleNext_OptionPicker.OptionChoice::getId).toList());
		assertRestrictionRow(editor, "Ship limit", expected.get(0));
		editor.buttonPressed("rule:remove:0");
		assertEquals(List.of("limitNumber", "limitList"), source.getOptions().stream().map(BattleNext_OptionPicker.OptionChoice::getId).toList());
		confirmRestrictions(editor, List.of("limitList"));
		assertRestrictionRow(editor, "Equipment filter", expected.get(1));
		editor.discard();
		assertEquals(List.of("limitNumber", "limitList"), source.getOptions().stream().map(BattleNext_OptionPicker.OptionChoice::getId).toList());
		editor.close();
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
		for (int iteration = 0; iteration < 2; iteration++) {
			List<?> previousFields = List.copyOf(((Map<?, ?>) getField(router.getSession(), "registrations")).keySet());
			confirmRestrictions(editor, List.of("limitList"));
			TextFieldAPI added = ((Map<String, TextFieldAPI>) getField(editor, "valueFields")).get("limitList");
			editor.buttonPressed("rule:remove:1");
			Map<?, ?> registrations = (Map<?, ?>) getField(router.getSession(), "registrations");
			values = (Map<String, TextFieldAPI>) getField(editor, "valueFields");
			assertEquals(Set.of("limitNumber"), values.keySet());
			assertEquals(Set.of(getField(editor, "idField"), getField(editor, "nameField"), values.get("limitNumber")), registrations.keySet());
			for (Object previous : previousFields) {
				assertFalse(registrations.containsKey(previous));
			}
			assertFalse(registrations.containsKey(added));
			assertEquals("12", values.get("limitNumber").getText());
			assertFalse(((Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes")).get("limitNumber").isChecked());
			assertTrue(router.isEmpty());
		}
		editor.close();
		assertFalse(router.getSession().hasInputState());
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
		String missing = BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_missing_restriction");
		assertRestrictionRow(editor, missing, List.of(missing, "lateLimit  [number]"));
		BattleNext_RestrictionRegistry.getInstance().load(new JSONArray().put(new JSONObject()
				.put("id", "lateLimit").put("name", "lateLimit").put("type", "number").put("script", "late").put("desc", "Late limit")),
				name -> (BattleNext_RestrictionScript) (fleet, value) -> List.of());
		editor.refreshContent();
		assertEquals("9.0", ((Map<String, TextFieldAPI>) getField(editor, "valueFields")).get("lateLimit").getText());
		assertRestrictionRow(editor, "lateLimit", List.of("lateLimit", "lateLimit  [number]", "Late limit"));
		assertTrue(((Map<String, ButtonAPI>) getField(editor, "enabledCheckboxes")).get("lateLimit").isChecked());
		assertFalse(editor.hasUnsavedChanges());
	}

	private void assertRestrictionRow(BattleNext_RuleEditor editor, String name, List<String> expected) throws Exception {
		BattleNext_TableWidget table = (BattleNext_TableWidget) getField(editor, "tableWidget");
		CustomPanelAPI row = (CustomPanelAPI) tooltips.get(table.getContent()).getComponents().get(0);
		LabelAPI label = children.get(row).stream().filter(TooltipMakerAPI.class::isInstance)
				.map(TooltipMakerAPI.class::cast).flatMap(cell -> tooltips.get(cell).getComponents().stream())
				.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).findFirst().orElseThrow();
		assertEquals(name, label.getText());
		TooltipMakerAPI.TooltipCreator creator = attachedTooltips.get(label);
		assertEquals(BattleNext_EquipmentTooltip.WIDTH, creator.getTooltipWidth(null));
		TooltipMakerAPI tooltip = tooltip(creator.getTooltipWidth(null), 0f, false);
		creator.createTooltip(tooltip, false, null);
		assertEquals(expected, tooltips.get(tooltip).getComponents().stream()
				.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
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
		assertEquals(BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_add_restriction"), buttonLabels.get("TOGGLE"));
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
