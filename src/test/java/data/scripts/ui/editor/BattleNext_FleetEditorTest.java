package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;

class BattleNext_FleetEditorTest extends BattleNext_TestPageFixture {

	@Test
	void saveReportsFolderAndIdConflictsTogetherAndCanSaveAfterCorrection() throws Exception {
		var store = new BattleNext_FleetStore();
		store.reload();
		store.save(new BattleNext_FleetModel("taken", "Existing", List.of(), List.of(), List.of()));
		var editor = new BattleNext_FleetEditor(store);
		editor.open(null, false);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("taken");
		((TextFieldAPI) getField(editor, "folderNameField")).setText("taken");
		((TextFieldAPI) getField(editor, "nameField")).setText("Copy");
		assertFalse(editor.save());
		assertEquals(2, editor.getSaveErrors().size());
		assertTrue(editor.getSaveErrors().get(0).contains(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_folder_name")));
		assertTrue(editor.getSaveErrors().get(0).endsWith(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_duplicate_folder")));
		assertTrue(editor.getSaveErrors().get(1).endsWith(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_duplicate_id")));
		assertEquals("Existing", store.get("taken").getName());
		((TextFieldAPI) getField(editor, "idField")).setText("copy");
		((TextFieldAPI) getField(editor, "folderNameField")).setText("copy");
		assertTrue(editor.save(), editor.getStatusText());
		assertEquals("Copy", store.get("copy").getName());
		assertTrue(editor.getSaveErrors().isEmpty());
		editor.close();
	}

	@Test
	void saveCollectsIdentityFolderAndMemberErrorsAndPreservesOrdinaryReferenceWarnings() throws Exception {
		var store = new BattleNext_FleetStore();
		store.reload();
		var fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), List.of(
				new BattleNext_MemberModel("parent_variant", "Alpha", "", List.of(), "", "", List.of()),
				new BattleNext_MemberModel("parent_variant", "Beta", "", List.of(), "", "", List.of())));
		var editor = new BattleNext_FleetEditor(store);
		editor.open(fleet, false);
		attachEditor(editor, panel(1000f, 900f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:0");
		editor.buttonPressed("toggle_member:1");
		((TextFieldAPI) getField(editor, "idField")).setText("123");
		((TextFieldAPI) getField(editor, "folderNameField")).setText("bad?folder");
		List<?> fields = (List<?>) getField(editor, "memberFields");
		((TextFieldAPI) getField(fields.get(0), "variantField")).setText("");
		((TextFieldAPI) getField(fields.get(1), "variantField")).setText("pinned:missing");
		assertFalse(editor.save());
		List<String> errors = editor.getSaveErrors();
		assertEquals(4, errors.size());
		assertTrue(errors.get(0).contains("123"));
		assertTrue(errors.get(0).contains(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_numeric")));
		assertTrue(errors.get(1).contains("bad?folder"));
		assertTrue(errors.get(1).contains("U+003F"));
		assertTrue(errors.get(2).contains(BattleNext_TestTextCatalog.text("Member", "BattleNext_member_label") + " 1 / Alpha"));
		assertTrue(errors.get(3).contains(BattleNext_TestTextCatalog.text("Member", "BattleNext_member_label") + " 2 / Beta"));
		assertTrue(errors.get(3).contains("pinned:missing"));
		assertTrue(store.getAll().isEmpty());
		assertTrue(editor.hasUnsavedChanges());
		((TextFieldAPI) getField(editor, "idField")).setText("fleet");
		((TextFieldAPI) getField(editor, "folderNameField")).setText("fleet");
		for (Object member : fields) {
			((TextFieldAPI) getField(member, "variantField")).setText("ordinary_missing");
		}
		assertTrue(editor.save(), editor.getStatusText());
		assertTrue(editor.getSaveErrors().isEmpty());
		store.reload();
		assertEquals(List.of("ordinary_missing", "ordinary_missing"), store.get("fleet").getMembers().stream().map(BattleNext_MemberModel::getVariantId).toList());
		editor.close();
	}

	@Test
	void addMemberButtonSelectsUpTo64CopiesAndCommitsOnlyOnConfirmation() throws Exception {

		ShipVariantAPI variant = source().getApi();
		registeredVariants.put("parent_variant", variant);
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
					if (method.getName().equals("getAllVariantIds")) return List.of("parent_variant");
					try {
						return method.invoke(delegate, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}));
		var editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		editor.open(null, false);
		attachEditor(editor, panel(1000f, 900f, new BaseCustomUIPanelPlugin()));
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_add_member"), buttonLabels.get("add_member"));
		editor.buttonPressed("add_member");
		var draft = (BattleNext_FleetModel) getField(editor, "draft");
		assertTrue(draft.getMembers().isEmpty());
		var picker = (BattleNext_OptionPicker) getField(editor, "memberPicker");
		for (int index = 0; index < 64; index++) {
			clickPickerOption(picker, "parent_variant");
		}
		assertEquals(Map.of("parent_variant", 64), getField(picker, "selection"));
		clickPickerOption(picker, "parent_variant");
		assertEquals(Map.of(), getField(picker, "selection"));
		((CustomPanelAPI) getField(picker, "popup")).getPlugin().buttonPressed(buttonIds.get("CANCEL"));
		assertTrue(draft.getMembers().isEmpty());
		addFleetMembers(editor, Collections.nCopies(64, "parent_variant"));
		assertEquals(64, draft.getMembers().size());
		assertTrue(draft.getMembers().stream().allMatch(member -> member.getVariantId().equals("parent_variant")));
		Set<BattleNext_MemberModel> members = Collections.newSetFromMap(new IdentityHashMap<>());
		members.addAll(draft.getMembers());
		assertEquals(64, members.size());
		editor.close();
		assertTrue(((BattleNext_UiInputRouter) getField(editor, "inputRouter")).isEmpty());
	}

	@Test
	void addMemberButtonAppendsMixedVariantsPreservesDraftAndSavesPinnedCopies() throws Exception {

		var store = new BattleNext_FleetStore();
		store.reload();
		var pins = new BattleNext_PinnedVariantStore();
		pins.reload();
		var source = source().getApi();
		registeredVariants.put("parent_variant", source);
		registeredVariants.put("child_variant", source.getModuleVariant("module"));
		var pin = pins.saveVariant("Pinned ship", source);
		var editor = new BattleNext_FleetEditor(store);
		editor.open(null, false);
		attachEditor(editor, panel(1000f, 900f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("batch_fleet");
		((TextFieldAPI) getField(editor, "nameField")).setText("Batch fleet");
		addFleetMembers(editor, List.of("parent_variant"));
		List<?> fields = (List<?>) getField(editor, "memberFields");
		((TextFieldAPI) getField(fields.get(0), "nameField")).setText("Existing ship");
		((TextFieldAPI) getField(fields.get(0), "ordersField")).setText("existing_order");
		addFleetMembers(editor, List.of("child_variant", "child_variant", "pinned:" + pin.getPinId(), "pinned:" + pin.getPinId()));
		var draft = (BattleNext_FleetModel) getField(editor, "draft");
		assertEquals(5, draft.getMembers().size());
		assertEquals("Existing ship", draft.getMembers().get(0).getName());
		assertEquals("existing_order", draft.getMembers().get(0).getOrders());
		assertTrue(editor.save(), editor.getStatusText());
		store.reload();
		var saved = store.get("batch_fleet");
		assertEquals(2, saved.getMembers().stream().filter(member -> member.getVariantId().equals("child_variant")).count());
		assertEquals(2, saved.getMembers().stream().filter(member -> member.getVariantId().equals(pin.getPinId())).count());
		assertEquals("Existing ship", saved.getMembers().get(0).getName());
		assertEquals("existing_order", saved.getMembers().get(0).getOrders());
		assertEquals("child_variant", saved.getVariant("child_variant").build().getHullVariantId());
		assertEquals(7, saved.getVariant(pin.getPinId()).build().getModuleVariant("module").getNumFluxVents());
		editor.close();
	}

	@ParameterizedTest
	@CsvSource({"1", "3"})
	void copyMemberCapturesInputsInsertsIndependentCopyAndSavesOrder(int copied) throws Exception {

		registeredVariants.put("parent_variant", source().getApi());
		registeredVariants.put("child_variant", source().getApi().getModuleVariant("module"));
		var store = new BattleNext_FleetStore();
		store.reload();
		List<String> names = List.of("first", "second", "third", "fourth");
		var fleet = new BattleNext_FleetModel("copied_fleet", "Copied fleet", List.of(), List.of(),
				names.stream().map(name -> new BattleNext_MemberModel("parent_variant", name, "", List.of(), "", "", List.of())).toList());
		var editor = new BattleNext_FleetEditor(store);
		editor.open(fleet, false);
		attachEditor(editor, panel(1000f, 900f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:1");
		editor.buttonPressed("toggle_member:3");
		List<?> fields = (List<?>) getField(editor, "memberFields");
		Object sourceFields = fields.get(copied);
		((TextFieldAPI) getField(sourceFields, "variantField")).setText("child_variant");
		((TextFieldAPI) getField(sourceFields, "nameField")).setText("Edited ship");
		((TextFieldAPI) getField(sourceFields, "officerPersonalityField")).setText("aggressive");
		((TextFieldAPI) getField(sourceFields, "officerSkillsField")).setText("helmsmanship,target_analysis");
		((TextFieldAPI) getField(sourceFields, "officerPortraitField")).setText("graphics/portraits/officer.png");
		((TextFieldAPI) getField(sourceFields, "ordersField")).setText("full_assault");
		((TextFieldAPI) getField(sourceFields, "tagsField")).setText("frontline,escort");
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_copy_member"), buttonLabels.get("copy_member:" + copied));
		BattleNext_OptionField variant = optionFields(editor).get(copied == 1 ? 1 : 5);
		CustomPanelAPI body = (CustomPanelAPI) getField(variant, "panelOwner");
		body.getPlugin().buttonPressed("copy_member:" + copied);
		var draft = (BattleNext_FleetModel) getField(editor, "draft");
		var expected = new BattleNext_MemberModel("child_variant", "Edited ship", "aggressive",
				List.of("helmsmanship", "target_analysis"), "graphics/portraits/officer.png", "full_assault", List.of("frontline", "escort"));
		assertEquals(5, draft.getMembers().size());
		assertJsonEquals(expected.toJson(), draft.getMembers().get(copied).toJson());
		assertJsonEquals(expected.toJson(), draft.getMembers().get(copied + 1).toJson());
		assertNotSame(draft.getMembers().get(copied), draft.getMembers().get(copied + 1));
		assertEquals(copied == 1 ? Set.of(1, 2, 4) : Set.of(1, 3, 4), getField(editor, "expandedMembers"));
		fields = (List<?>) getField(editor, "memberFields");
		((TextFieldAPI) getField(fields.get(copied + 1), "nameField")).setText("Independent copy");
		((TextFieldAPI) getField(fields.get(copied + 1), "tagsField")).setText("reserve");
		editor.refreshContent();
		assertTrue(editor.hasUnsavedChanges());
		assertTrue(editor.save(), editor.getStatusText());
		store.reload();
		var saved = store.get("copied_fleet");
		assertEquals(copied == 1 ? List.of("first", "Edited ship", "Independent copy", "third", "fourth")
				: List.of("first", "second", "third", "Edited ship", "Independent copy"),
				saved.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		assertJsonEquals(expected.toJson(), saved.getMembers().get(copied).toJson());
		expected.setName("Independent copy");
		expected.setTags(List.of("reserve"));
		assertJsonEquals(expected.toJson(), saved.getMembers().get(copied + 1).toJson());
		assertEquals("child_variant", saved.getVariant("child_variant").build().getHullVariantId());
		editor.close();
	}

	@ParameterizedTest
	@CsvSource({"up,0,false", "up,0,true", "down,2,false", "down,2,true"})
	void moveMemberPreservesInputsAndExpansionRenumbersAndSavesOrder(String direction, int target, boolean collapsed) throws Exception {

		registeredVariants.put("parent_variant", source().getApi());
		var store = new BattleNext_FleetStore();
		store.reload();
		List<String> names = List.of("first", "second", "third");
		var fleet = new BattleNext_FleetModel("moving_fleet", "Moving fleet", List.of(), List.of(),
				names.stream().map(name -> new BattleNext_MemberModel("parent_variant", name, "aggressive",
						List.of("helmsmanship"), "graphics/portraits/officer.png", "orders_" + name, List.of(name))).toList());
		var editor = new BattleNext_FleetEditor(store);
		editor.open(fleet, false);
		attachEditor(editor, panel(1000f, 900f, new BaseCustomUIPanelPlugin()));
		for (int index = 0; index < 3; index++) {
			editor.buttonPressed("toggle_member:" + index);
			assertEquals(index > 0, buttons.get("move_member_up:" + index).get());
			assertEquals(index < 2, buttons.get("move_member_down:" + index).get());
		}
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_move_up"), buttonLabels.get("move_member_up:1"));
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_move_down"), buttonLabels.get("move_member_down:1"));
		List<?> fields = (List<?>) getField(editor, "memberFields");
		((TextFieldAPI) getField(fields.get(1), "nameField")).setText("edited");
		((TextFieldAPI) getField(fields.get(1), "ordersField")).setText("edited_order");
		if (collapsed) editor.buttonPressed("toggle_member:" + target);
		fields = (List<?>) getField(editor, "memberFields");
		TextFieldAPI variantField = (TextFieldAPI) getField(fields.get(1), "variantField");
		BattleNext_OptionField variant = optionFields(editor).stream().filter(option -> option.getField() == variantField).findFirst().orElseThrow();
		CustomPanelAPI body = (CustomPanelAPI) getField(variant, "panelOwner");
		body.getPlugin().buttonPressed("move_member_" + direction + ":1");
		var draft = (BattleNext_FleetModel) getField(editor, "draft");
		List<String> expectedNames = target == 0 ? List.of("edited", "first", "third") : List.of("first", "third", "edited");
		assertEquals(expectedNames, draft.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		assertEquals(collapsed ? Set.of(0, 2) : Set.of(0, 1, 2), getField(editor, "expandedMembers"));
		assertTrue(editor.hasUnsavedChanges());
		fields = (List<?>) getField(editor, "memberFields");
		assertEquals("edited", ((TextFieldAPI) getField(fields.get(target), "nameField")).getText());
		for (int index : collapsed ? List.of(0, 2) : List.of(0, 1, 2)) {
			TextFieldAPI field = (TextFieldAPI) getField(fields.get(index), "variantField");
			BattleNext_OptionField option = optionFields(editor).stream().filter(entry -> entry.getField() == field).findFirst().orElseThrow();
			TooltipMakerAPI content = (TooltipMakerAPI) getField(option, "host");
			assertEquals(BattleNext_TestTextCatalog.text("Member", "BattleNext_member_label") + " " + (index + 1),
					((LabelAPI) tooltips.get(content).getComponents().get(0)).getText());
			assertEquals(index > 0, buttons.get("move_member_up:" + index).get());
			assertEquals(index < 2, buttons.get("move_member_down:" + index).get());
		}
		editor.refreshContent();
		assertTrue(editor.save(), editor.getStatusText());
		store.reload();
		var saved = store.get("moving_fleet");
		assertEquals(expectedNames, saved.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		for (int index = 0; index < expectedNames.size(); index++) {
			String name = expectedNames.get(index);
			var expected = new BattleNext_MemberModel("parent_variant", name, "aggressive", List.of("helmsmanship"),
					"graphics/portraits/officer.png", name.equals("edited") ? "edited_order" : "orders_" + name,
					List.of(name.equals("edited") ? "second" : name));
			assertJsonEquals(expected.toJson(), saved.getMembers().get(index).toJson());
		}
		editor.close();
	}

	@ParameterizedTest
	@CsvSource({"0,false", "1,false", "0,true", "1,true"})
	void memberRemovalKeepsSurvivorFieldsBoundToTheirOwnData(int removed, boolean collapsed) throws Exception {

		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		attachEditor(editor, root);
		for (int index = 0; index < 3; index++) {
			addFleetMembers(editor, List.of("parent_variant"));
		}
		List<?> fields = (List<?>) getField(editor, "memberFields");
		List<String> names = List.of("first", "second", "third");
		for (int index = 0; index < names.size(); index++) {
			((TextFieldAPI) getField(fields.get(index), "nameField")).setText(names.get(index));
			((TextFieldAPI) getField(fields.get(index), "ordersField")).setText("orders_" + names.get(index));
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
		editor.closeOverlays();
		assertEquals(expected, draft.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		assertTrue(router.isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"1000, 12", "1000, 20", "320, 17"})
	void memberFieldsShareMetadataSpacingAndMeasureTheirExpandedHeight(int width, int lineHeight) throws Exception {
		paragraphLineHeight = lineHeight;
		deferTooltipWidth = true;
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(width, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		attachEditor(editor, root);
		addFleetMembers(editor, List.of("parent_variant"));
		TooltipMakerAPI metadata = (TooltipMakerAPI) getField(getField(editor, "idField"), "host");
		assertFieldFlow(metadata, 1, Set.of(5));
		List<BattleNext_OptionField> pickers = optionFields(editor).subList(1, optionFields(editor).size());
		BattleNext_OptionField variant = pickers.get(0);
		TooltipMakerAPI content = (TooltipMakerAPI) getField(variant, "host");
		CustomPanelAPI body = (CustomPanelAPI) getField(variant, "panelOwner");
		assertSame(body.getPosition(), geometry.get(tooltips.get(content).getViewport()).getParent());
		assertFieldFlow(content, 3, Set.of(0, 2, 3, 4));
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
		PositionAPI up = components.get(1).getPosition();
		PositionAPI down = components.get(2).getPosition();
		assertEquals(BattleNext_PageLayout.EDITOR_BUTTON_GAP, heading.getY() - up.getY() - up.getHeight());
		assertEquals(up.getY(), down.getY());
		assertEquals(up.getHeight(), down.getHeight());
		assertEquals(BattleNext_PageLayout.EDITOR_BUTTON_GAP, down.getX() - up.getX() - up.getWidth());
		assertFalse(((ButtonAPI) components.get(1)).isEnabled());
		assertFalse(((ButtonAPI) components.get(2)).isEnabled());
		PositionAPI remove = components.get(17).getPosition();
		PositionAPI copy = components.get(18).getPosition();
		assertEquals(body.getPosition().getY() + 8f, copy.getY());
		assertEquals(remove.getY(), copy.getY());
		assertEquals(remove.getHeight(), copy.getHeight());
		assertEquals(BattleNext_PageLayout.EDITOR_BUTTON_GAP, copy.getX() - remove.getX() - remove.getWidth());
		TooltipMakerAPI viewport = ((TooltipMakerAPI[]) getField(variant, "viewports"))[1];
		List<UIComponentAPI> memberRows = tooltips.get(viewport).getComponents();
		assertEquals(memberRows.get(0).getPosition().getHeight() + BattleNext_ExpandablePanel.ROW_GAP
				+ member.getHeight() + BattleNext_PageLayout.EDITOR_BUTTON_GAP + BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT,
				tooltips.get(viewport).getMountedHeight());
		List<?> members = (List<?>) getField(editor, "memberFields");
		TextFieldAPI memberName = (TextFieldAPI) getField(members.get(0), "nameField");
		memberName.setText("compact ship");
		editor.buttonPressed("toggle_member:0");
		Map<?, ?> registrations = (Map<?, ?>) getField(router.getSession(), "registrations");
		List<String> metadataFields = List.of("idField", "folderNameField", "nameField", "authorField", "descriptionField", "commanderSkillsField", "tagsField");
		Set<Object> expected = new HashSet<>();
		for (String name : metadataFields) {
			expected.add(getField(editor, name));
		}
		assertEquals(expected, registrations.keySet());
		assertFalse(registrations.containsKey(memberName));
		editor.buttonPressed("toggle_member:0");
		members = (List<?>) getField(editor, "memberFields");
		assertEquals("compact ship", ((TextFieldAPI) getField(members.get(0), "nameField")).getText());
		expected.clear();
		for (String name : metadataFields) {
			expected.add(getField(editor, name));
		}
		for (String name : List.of("variantField", "nameField", "officerPersonalityField", "officerSkillsField", "officerPortraitField", "ordersField", "tagsField")) {
			expected.add(getField(members.get(0), name));
		}
		assertEquals(expected, ((Map<?, ?>) getField(router.getSession(), "registrations")).keySet());
		pickers = optionFields(editor).subList(1, optionFields(editor).size());
		content = (TooltipMakerAPI) getField(pickers.get(0), "host");
		assertFieldFlow(content, 3, Set.of(0, 2, 3, 4));
		assertEquals(tooltips.get(content).getUsedHeight(), tooltips.get(content).getHeightAtMount());
		editor.close();
		assertFalse(router.getSession().hasInputState());
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
	void allFiveFleetSelectorsUseExplicitWidthsRootHostAndTheirViewport() throws Exception {
		deferTooltipWidth = true;
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		editor.open(null, false);
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		attachEditor(editor, root);
		((TextFieldAPI) getField(editor, "idField")).setText("draft_id");
		addFleetMembers(editor, List.of("parent_variant"));
		float usableWidth = 1000f - BattleNext_PageLayout.EDITOR_COLUMN_GAP;
		float metaWidth = (float) Math.floor(usableWidth * BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		BattleNext_OptionField skills = optionFields(editor).get(0);
		assertEquals(BattleNext_PageLayout.editorFieldWidth(metaWidth), getField(skills, "fieldWidth"));
		assertSame(root, getField(skills, "overlayHost"));
		List<BattleNext_OptionField> members = List.copyOf(optionFields(editor).subList(1, optionFields(editor).size()));
		assertEquals(4, members.size());
		for (BattleNext_OptionField member : members) {
			assertEquals(BattleNext_PageLayout.editorFieldWidth(usableWidth - metaWidth - 16f), getField(member, "fieldWidth"));
			assertSame(root, getField(member, "overlayHost"));
			assertEquals(2, ((TooltipMakerAPI[]) getField(member, "viewports")).length);
		}
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		editor.closeOverlays();
		assertEquals("draft_id", editor.getDraftId());
		editor.refreshContent();
		assertEquals("draft_id", editor.getDraftId());
		assertEquals(14, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		assertTrue(((List<?>) getField(skills, "ownedPanels")).isEmpty());
		for (BattleNext_OptionField member : members) {
			assertTrue(((List<?>) getField(member, "ownedPanels")).isEmpty());
		}
	}
}
