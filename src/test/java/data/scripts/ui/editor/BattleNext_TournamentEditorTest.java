package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.battlesnext.util.ui.CheckBoxGroup;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.member.BattleNext_MemberModel;
import data.test.fixture.BattleNext_TestPageFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_TournamentEditorTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {1400})
	void columnsAndTableUseRulesGeometryAndNativeReadonlyStatus(int width) throws Exception {

		deferTooltipWidth = true;
		shortenLabels = true;
		BattleNext_FleetStore fleets = fleets(List.of("first", "second"));
		fleets.get("first").setAuthor("A full author name 50% " + "long ".repeat(30));
		BattleNext_TournamentEditor editor = editor(fleets, new BattleNext_RuleStore(),
				tournament(List.of("first", "second")), width, 700f);
		TooltipMakerAPI left = (TooltipMakerAPI) getField(editor, "metadataContent");
		TooltipMakerAPI right = (TooltipMakerAPI) getField(editor, "rowsContent");
		float leftWidth = (float) Math.floor((width - BattleNext_PageLayout.EDITOR_COLUMN_GAP)
				* BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		float rightWidth = width - BattleNext_PageLayout.EDITOR_COLUMN_GAP - leftWidth;
		assertEquals(leftWidth, tooltips.get(left).getViewport().getWidth());
		assertEquals(rightWidth, tooltips.get(right).getViewport().getWidth());
		assertEquals(left.getPosition().getX() + leftWidth + 10f, right.getPosition().getX());
		assertEquals(tooltips.get(left).getViewport().getY(), tooltips.get(right).getViewport().getY());
		BattleNext_TableWidget table = table(editor);
		assertArrayEquals(new String[]{"BattleNext_tournament_fleet", "BattleNext_fleet_author",
				"BattleNext_tournament_valid", "BattleNext_common_actions"}, (String[]) getField(table, "headers"));
		assertArrayEquals(new float[]{0.3f, 0.34f, 0.16f, 0.2f}, (float[]) getField(table, "columnShares"));
		UIRect tableArea = BattleNext_PageLayout.pageContentRect(new UIRect(0f,
				BattleNext_PageLayout.EDITOR_TABLE_TITLE_HEIGHT, rightWidth,
				700f - BattleNext_PageLayout.EDITOR_TABLE_TITLE_HEIGHT));
		assertEquals(tableArea.getWidth(), table.getPanel().getPosition().getWidth());
		assertEquals(tableArea.getHeight(), table.getPanel().getPosition().getHeight());
		assertEquals(right.getPosition().getX() + tableArea.getX(), table.getPanel().getPosition().getX());
		assertSame(right.getPosition(), geometry.get(table.getPanel().getPosition()).getParent());
		for (String name : List.of("idField", "nameField", "bestOfField", "maxFleetsField", "mapField")) {
			TextFieldAPI field = (TextFieldAPI) getField(editor, name);
			assertEquals(leftWidth - 12f, field.getPosition().getWidth());
			assertEquals(List.of(left), ((Map<?, ?>) getField(router(editor).getSession(), "registrations")).get(field));
		}
		TextFieldAPI ruleField = (TextFieldAPI) getField(editor, "ruleField");
		CustomPanelAPI ruleRow = (CustomPanelAPI) getField(optionFields(editor).get(0), "row");
		assertEquals(leftWidth - 12f, ruleRow.getPosition().getWidth());
		assertEquals(ruleRow.getPosition().getWidth() - 32f, ruleField.getPosition().getWidth());
		assertEquals(List.of(left), ((Map<?, ?>) getField(router(editor).getSession(), "registrations")).get(ruleField));
		float rowWidth = table.getPanel().getPosition().getWidth();
		for (int index = 0; index < 2; index++) {
			CustomPanelAPI row = row(editor, index);
			assertEquals(rowWidth, row.getPosition().getWidth());
			assertEquals(BattleNext_PageLayout.TABLE_ROW_HEIGHT, row.getPosition().getHeight());
			List<LabelAPI> cells = rowLabels(row);
			assertEquals(2, cells.size());
			for (LabelAPI cell : cells) {
				assertEquals(Alignment.MID, labelAlignments.get(cell.getPosition()));
				assertEquals(row.getPosition().getCenterY(), cell.getPosition().getCenterY(), 0.001f);
			}
			ButtonAPI valid = valid(editor, index);
			assertTrue(valid.isChecked());
			assertFalse(valid.isEnabled());
			assertEquals(38f, valid.getPosition().getHeight());
			assertTrue(valid.getPosition().getWidth() <= 22f);
			float[] shares = {0.3f, 0.34f, 0.16f, 0.2f};
			float available = rowWidth - BattleNext_PageLayout.TABLE_COLUMN_GAP * 3f;
			float actionsX = 0f;
			for (int column = 0; column < 3; column++) {
				actionsX += available * shares[column] + BattleNext_PageLayout.TABLE_COLUMN_GAP;
			}
			float actionsWidth = (int) (rowWidth - actionsX);
			float buttonWidth = (int) (Math.max(1f, actionsWidth - 8f) / 3f);
			for (String action : List.of("up", "down", "remove")) {
				PositionAPI button = buttonPositions.get("fleet:" + action + ":" + index);
				assertEquals(buttonWidth, button.getWidth());
				assertEquals(30f, button.getHeight());
				assertEquals(row.getPosition().getY() + 4f, button.getY());
			}
		}
		assertFalse(buttons.get("fleet:up:0").get());
		assertFalse(buttons.get("fleet:down:1").get());
		assertTrue(buttons.get("fleet:remove:0").get());
		assertEquals("—", rowLabels(row(editor, 1)).get(1).getText());
		LabelAPI author = rowLabels(row(editor, 0)).get(1);
		assertTrue(author.getText().endsWith("..."));
		assertTrue(hover(author).contains(fleets.get("first").getAuthor()));
		LabelAPI name = rowLabels(row(editor, 0)).get(0);
		assertTrue(hover(name).contains("first"));
		BattleNext_OptionPicker picker = picker(editor);
		assertTablePickerAlignment(table, picker);
		assertEquals(Math.min(190f, rowWidth), getField(picker, "buttonWidth"));
		assertEquals(30f, getField(picker, "buttonHeight"));
		assertEquals(4f, getField(picker, "topGap"));
		assertEquals("BattleNext_tournament_add_fleet", getField(picker, "title"));
		assertSame(getField(editor, "overlayHost"), getField(picker, "overlayHost"));
		assertArrayEquals(new TooltipMakerAPI[]{table.getContent(), right},
				(TooltipMakerAPI[]) getField(picker, "viewports"));
		CustomPanelAPI add = (CustomPanelAPI) getField(picker, "row");
		assertEquals(row(editor, 1).getPosition().getY() - 4f,
				add.getPosition().getY() + add.getPosition().getHeight(), 0.001f);
		editor.close();
		assertTrue(((Map<?, ?>) getField(router(editor).getSession(), "registrations")).isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"180,0", "1000,2"})
	void fleetButtonAlignsWithTableEdgesAndRowsAcrossScrollAndRefresh(int width, int count) throws Exception {
		deferTooltipWidth = true;
		List<String> ids = IntStream.range(0, count).mapToObj(index -> "fleet_" + index).toList();
		BattleNext_TournamentEditor editor = editor(fleets(ids), new BattleNext_RuleStore(), tournament(ids), width, 100f);
		for (int iteration = 0; iteration < 3; iteration++) {
			BattleNext_TableWidget oldTable = table(editor);
			BattleNext_OptionPicker oldPicker = picker(editor);
			assertTablePickerAlignment(oldTable, oldPicker);
			oldTable.getContent().getExternalScroller().setYOffset(oldTable.getContent().getHeightSoFar());
			float offset = oldTable.getContent().getExternalScroller().getYOffset();
			assertTablePickerAlignment(oldTable, oldPicker);
			editor.refreshContent();
			assertTrue(((List<?>) getField(oldTable, "ownedPanels")).isEmpty());
			assertTrue(((List<?>) getField(oldPicker, "ownedPanels")).isEmpty());
			assertEquals(offset, table(editor).getContent().getExternalScroller().getYOffset());
		}
		assertTablePickerAlignment(table(editor), picker(editor));
		editor.close();
		assertTrue(((Map<?, ?>) getField(router(editor).getSession(), "registrations")).isEmpty());
	}

	@Test
	void confirmedAddReorderRemoveAndSaveUseTheDraftWhilePreservingUnfinishedInputs() throws Exception {

		BattleNext_FleetStore fleets = fleets(List.of("first", "second", "third"));
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournaments, fleets, rules);
		editor.open(null);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("cup");
		((TextFieldAPI) getField(editor, "nameField")).setText("Cup");
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("3");
		TextFieldAPI bo = (TextFieldAPI) getField(editor, "bestOfField");
		bo.setText("pending");
		confirmTournamentFleets(editor, List.of("second", "first"));
		assertEquals(List.of("first", "second"), draft(editor).getFleetIds());
		confirmTournamentFleets(editor, List.of("second", "third"));
		assertEquals(List.of("first", "second", "third"), draft(editor).getFleetIds());
		String state = editor.serializeState();
		confirmTournamentFleets(editor, List.of("first", "third"));
		assertEquals(state, editor.serializeState());
		assertEquals("pending", bo.getText());
		editor.buttonPressed("fleet:up:2");
		assertEquals(List.of("first", "third", "second"), draft(editor).getFleetIds());
		editor.buttonPressed("fleet:remove:0");
		assertEquals(List.of("third", "second"), draft(editor).getFleetIds());
		editor.buttonPressed("fleet:down:0");
		assertEquals(List.of("second", "third"), draft(editor).getFleetIds());
		assertSame(bo, getField(editor, "bestOfField"));
		assertEquals("pending", bo.getText());
		assertTrue(editor.hasUnsavedChanges());
		assertFalse(editor.save());
		assertEquals("pending", bo.getText());
		bo.setText("3");
		assertTrue(editor.save(), editor.getStatusText());
		tournaments.reload();
		assertEquals(List.of("second", "third"), tournaments.get("cup").getFleetIds());
		assertFalse(editor.hasUnsavedChanges());
		editor.buttonPressed("fleet:remove:0");
		assertTrue(editor.hasUnsavedChanges());
		editor.discard();
		assertFalse(editor.hasUnsavedChanges());
		assertEquals(List.of("second", "third"), draft(editor).getFleetIds());
		editor.close();
	}

	@Test
	void ruleSelectionSearchCancelAndCommitPreserveInputsAndPersistTheSelectedIdentity() throws Exception {

		BattleNext_FleetStore fleets = fleets(List.of("first", "second"));
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		rules.save(new BattleNext_RuleModel("rule_a", "Rule A"));
		rules.save(new BattleNext_RuleModel("rule_b", "Rule B 50%"));
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		BattleNext_TournamentModel model = tournament(List.of("first", "second"));
		model.setRuleId("rule_a");
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournaments, fleets, rules);
		editor.open(model);
		CustomPanelAPI root = panel(1000f, 900f, new BaseCustomUIPanelPlugin());
		attachEditor(editor, root);
		TextFieldAPI ruleField = (TextFieldAPI) getField(editor, "ruleField");
		TextFieldAPI bestOfField = (TextFieldAPI) getField(editor, "bestOfField");
		bestOfField.setText("pending");
		String before = editor.serializeState();
		chooseRule(editor, "Rule B", "CANCEL");
		assertEquals(before, editor.serializeState());
		chooseRule(editor, "Rule B", "CONFIRM");
		assertEquals("rule_b", ruleField.getText());
		assertTrue(editor.hasUnsavedChanges());
		editor.refreshDraftValidation();
		assertSame(ruleField, getField(editor, "ruleField"));
		assertSame(bestOfField, getField(editor, "bestOfField"));
		assertEquals("pending", bestOfField.getText());
		assertFalse(editor.save());
		assertNull(tournaments.get("cup"));
		editor.refreshContent();
		assertEquals("rule_b", ((TextFieldAPI) getField(editor, "ruleField")).getText());
		assertEquals("pending", ((TextFieldAPI) getField(editor, "bestOfField")).getText());
		((TextFieldAPI) getField(editor, "bestOfField")).setText("3");
		assertTrue(editor.save(), editor.getStatusText());
		tournaments.reload();
		assertEquals("rule_b", tournaments.get("cup").getRuleId());
		assertFalse(editor.hasUnsavedChanges());
		ruleField = (TextFieldAPI) getField(editor, "ruleField");
		ruleField.setText("unknown");
		chooseRule(editor, "", "CONFIRM");
		assertEquals("unknown", ruleField.getText());
		editor.refreshDraftValidation();
		assertFalse(valid(editor, 0).isChecked());
		ruleField.setText("");
		chooseRule(editor, "", "CONFIRM");
		assertEquals("", ruleField.getText());
		editor.refreshDraftValidation();
		assertTrue(valid(editor, 0).isChecked());
		assertTrue(editor.save(), editor.getStatusText());
		tournaments.reload();
		assertEquals("", tournaments.get("cup").getRuleId());
		rules.save(new BattleNext_RuleModel("rule_c", "New rule"));
		chooseRule(editor, "New rule", "CONFIRM");
		assertEquals("rule_c", ruleField.getText());
		editor.discard();
		assertEquals("", ((TextFieldAPI) getField(editor, "ruleField")).getText());
		assertFalse(editor.hasUnsavedChanges());
		editor.close();
		assertTrue(router(editor).isEmpty());
		assertFalse(router(editor).getSession().hasInputState());
	}

	@Test
	void ruleChangesUpdateStatusesAndTooltipReasonsWithoutRebuildingFocusedLeftFields() throws Exception {

		BattleNext_FleetStore fleets = fleets(List.of("first", "second"));
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		AtomicInteger evaluations = new AtomicInteger();
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		Object oldEntries = getField(registry, "entries");
		Object oldFactory = getField(registry, "scriptFactory");
		try {
			registry.load(new JSONArray().put(new JSONObject().put("id", "limit").put("type", "number")
					.put("script", "test").put("desc", "Limit")), name -> (BattleNext_RestrictionScript) (fleet, restriction) -> {
				evaluations.incrementAndGet();
				return fleet.getDefinition().getId().equals("first") ? List.of("Fleet exceeds 50% limit") : List.of();
			});
			BattleNext_RuleModel rule = new BattleNext_RuleModel("rule", "Rule");
			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction("limit", "number");
			restriction.setEnabled(true);
			rule.setRestrictions(List.of(restriction));
			rules.save(rule);
			BattleNext_TournamentEditor editor = editor(fleets, rules, tournament(List.of("first", "second")), 1000f, 700f);
			TextFieldAPI field = (TextFieldAPI) getField(editor, "ruleField");
			chooseRule(editor, "Rule", "CONFIRM");
			field.grabFocus(false);
			BattleNext_TableWidget table = table(editor);
			TooltipMakerAPI previous = table.getContent();
			editor.refreshDraftValidation();
			assertSame(field, getField(editor, "ruleField"));
			assertTrue(field.hasFocus());
			assertNotSame(previous, table.getContent());
			assertFalse(valid(editor, 0).isChecked());
			assertTrue(valid(editor, 1).isChecked());
			assertTrue(hover(valid(editor, 0)).contains("Fleet exceeds 50% limit"));
			assertTrue(hover(valid(editor, 1)).contains("BattleNext_rule_validation_clear"));
			int count = evaluations.get();
			for (int iteration = 0; iteration < 10; iteration++) editor.refreshDraftValidation();
			assertEquals(count, evaluations.get());
			field.setText("missing");
			editor.refreshDraftValidation();
			assertFalse(valid(editor, 0).isChecked());
			assertFalse(valid(editor, 1).isChecked());
			assertTrue(hover(valid(editor, 0)).stream().anyMatch(value -> value.contains("missing")));
			field.setText("");
			editor.refreshDraftValidation();
			assertTrue(valid(editor, 0).isChecked());
			assertTrue(valid(editor, 1).isChecked());
			editor.close();
		} finally {
			setField(registry, "entries", oldEntries);
			setField(registry, "scriptFactory", oldFactory);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing_script", "exception", "assembly", "reference", "load"})
	void invalidRowsKeepTheirIdentityAndDetailedReasonsAndOtherRowsContinue(String failure) throws Exception {

		BattleNext_FleetStore fleets = fleets(List.of("first", "second"));
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		BattleNext_TournamentModel source = tournament(List.of("first", "second"));
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		Object oldEntries = getField(registry, "entries");
		Object oldFactory = getField(registry, "scriptFactory");
		try {
			if (failure.equals("missing_script") || failure.equals("exception")) {
				registry.load(new JSONArray(), name -> null);
				if (failure.equals("exception")) {
					registry.load(new JSONArray().put(new JSONObject().put("id", "limit").put("type", "number")
							.put("script", "test").put("desc", "Limit")), name -> (BattleNext_RestrictionScript) (fleet, restriction) -> {
						if (fleet.getDefinition().getId().equals("first")) throw new IllegalStateException("Injected evaluation failure");
						return List.of();
					});
				}
				BattleNext_RuleModel rule = new BattleNext_RuleModel("rule", "Rule");
				BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction("limit", "number");
				restriction.setEnabled(true);
				rule.setRestrictions(List.of(restriction));
				rules.save(rule);
				source.setRuleId("rule");
			}
			if (failure.equals("assembly")) {
				BattleNext_MemberModel member = new BattleNext_MemberModel();
				member.setVariantId("missing_variant");
				fleets.get("first").setMembers(List.of(member));
			}
			if (failure.equals("reference")) source.setFleetIds(List.of("missing", "second"));
			if (failure.equals("load")) fleets.get("first").addLoadError("Missing nested variant: module");
			BattleNext_TournamentEditor editor = editor(fleets, rules, source, 1000f, 700f);
			assertFalse(valid(editor, 0).isChecked());
			assertEquals(!failure.equals("missing_script"), valid(editor, 1).isChecked());
			List<String> details = hover(valid(editor, 0));
			assertTrue(details.size() >= 3);
			String expected = switch (failure) {
				case "missing_script" -> "limit";
				case "exception" -> "Injected evaluation failure";
				case "assembly" -> "member[0]";
				case "load" -> "Missing nested variant";
				default -> "BattleNext_tournament_invalid_participant_fleet";
			};
			assertTrue(details.stream().anyMatch(value -> value.contains(expected)), details.toString());
			assertEquals(source.getFleetIds(), draft(editor).getFleetIds());
			editor.close();
		} finally {
			setField(registry, "entries", oldEntries);
			setField(registry, "scriptFactory", oldFactory);
		}
	}

	@Test
	void addFleetAcceptsInvalidAssemblyAndImmediatelyBuildsItsUncheckedRow() throws Exception {
		BattleNext_FleetStore fleets = fleets(List.of("first", "second"));
		BattleNext_MemberModel member = new BattleNext_MemberModel();
		member.setVariantId("missing_variant");
		fleets.get("first").setMembers(List.of(member));
		BattleNext_TournamentEditor editor = editor(fleets, new BattleNext_RuleStore(), tournament(List.of()), 1000f, 700f);
		confirmTournamentFleets(editor, List.of("first", "second"));
		assertEquals(List.of("first", "second"), draft(editor).getFleetIds());
		assertFalse(valid(editor, 0).isChecked());
		assertTrue(valid(editor, 1).isChecked());
		assertTrue(hover(valid(editor, 0)).stream().anyMatch(reason -> reason.contains("member[0]")));
		assertTrue(editor.hasUnsavedChanges());
		editor.close();
	}

	@Test
	void refreshReorderAndDeletePreserveScrollAndClampShrinkingRows() throws Exception {

		List<String> ids = IntStream.range(0, 16).mapToObj(index -> "missing_" + index).toList();
		BattleNext_TournamentEditor editor = editor(new BattleNext_FleetStore(), new BattleNext_RuleStore(),
				tournament(ids), 1000f, 300f);
		TextFieldAPI bo = (TextFieldAPI) getField(editor, "bestOfField");
		bo.setText("pending");
		CheckBoxGroup bracket = (CheckBoxGroup) getField(editor, "bracketGroup");
		bracket.tryCheckValue(TournamentBracketType.DOUBLE_ELIMINATION);
		bracket.updateCheck();
		TooltipMakerAPI left = (TooltipMakerAPI) getField(editor, "metadataContent");
		left.getExternalScroller().setYOffset(95f);
		table(editor).getContent().getExternalScroller().setYOffset(170f);
		for (int iteration = 0; iteration < 3; iteration++) {
			BattleNext_TableWidget old = table(editor);
			BattleNext_OptionPicker oldPicker = picker(editor);
			editor.refreshContent();
			assertTrue(((List<?>) getField(old, "ownedPanels")).isEmpty());
			assertTrue(((List<?>) getField(oldPicker, "ownedPanels")).isEmpty());
			assertEquals("pending", ((TextFieldAPI) getField(editor, "bestOfField")).getText());
			assertEquals(TournamentBracketType.DOUBLE_ELIMINATION, ((CheckBoxGroup) getField(editor, "bracketGroup")).getCheckedValue());
			assertEquals(95f, ((TooltipMakerAPI) getField(editor, "metadataContent")).getExternalScroller().getYOffset());
			assertEquals(170f, table(editor).getContent().getExternalScroller().getYOffset());
			assertEquals(6, ((Map<?, ?>) getField(router(editor).getSession(), "registrations")).size());
		}
		editor.buttonPressed("fleet:up:2");
		assertEquals(List.of("missing_0", "missing_2", "missing_1"), draft(editor).getFleetIds().subList(0, 3));
		assertEquals(170f, table(editor).getContent().getExternalScroller().getYOffset());
		while (draft(editor).getFleetIds().size() > 2) editor.buttonPressed("fleet:remove:2");
		assertEquals(0f, table(editor).getContent().getExternalScroller().getYOffset());
		editor.discard();
		assertFalse(editor.hasUnsavedChanges());
		assertEquals(ids, draft(editor).getFleetIds());
		assertEquals(0f, table(editor).getContent().getExternalScroller().getYOffset());
		editor.close();
	}

	@Test
	void pickerCancelConfirmationModalAndClippingRespectTheTwoTableViewports() throws Exception {

		BattleNext_FleetStore fleets = fleets(List.of("first", "second", "third"));
		List<String> ids = IntStream.range(0, 12).mapToObj(index -> "missing_" + index).toList();
		BattleNext_TournamentEditor editor = editor(fleets, new BattleNext_RuleStore(), tournament(ids), 1000f, 700f);
		BattleNext_UiInputRouter router = router(editor);
		String before = editor.serializeState();
		BattleNext_OptionPicker picker = picker(editor);
		CustomPanelAPI row = (CustomPanelAPI) getField(picker, "row");
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		assertSame(getField(editor, "overlayHost"), getField(picker, "overlayHost"));
		plugins.get(popup).buttonPressed(buttonIds.get("CANCEL"));
		assertEquals(before, editor.serializeState());
		TooltipMakerAPI rows = table(editor).getContent();
		rows.getExternalScroller().setYOffset(rows.getHeightSoFar());
		for (int iteration = 0; iteration < 3; iteration++) {
			plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
			popup = (CustomPanelAPI) getField(picker, "popup");
			assertNotNull(popup);
			assertSame(((CustomPanelAPI) getField(editor, "overlayHost")).getPosition(), geometry.get(popup.getPosition()).getParent());
			TooltipMakerAPI right = (TooltipMakerAPI) getField(editor, "rowsContent");
			PositionAPI content = right.getPosition();
			content.inTL(0f, 10000f);
			plugins.get(popup).advance(0f);
			assertNull(getField(picker, "popup"));
			assertTrue(router.isEmpty());
			assertEquals(6, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
			content.inTL(0f, 0f);
		}
		BattleNext_ConfirmationDialog dialog = new BattleNext_ConfirmationDialog(new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
			@Override
			public boolean saveCurrentPage() {
				return editor.save();
			}
			@Override
			public String getSaveError() {
				return editor.getStatusText();
			}
			@Override
			public void discardCurrentPage() {
				editor.discard();
			}
		}, router);
		dialog.attach((CustomPanelAPI) getField(editor, "panel"));
		dialog.request(() -> {});
		dialog.handle("confirm_cancel");
		((TextFieldAPI) getField(editor, "ruleField")).grabFocus(false);
		assertTrue(((TextFieldAPI) getField(editor, "ruleField")).hasFocus());
		dialog.close();
		BattleNext_TableWidget oldTable = table(editor);
		editor.close();
		assertTrue(((List<?>) getField(getField(editor, "fleetPicker"), "ownedPanels")).isEmpty());
		assertTrue(((List<?>) getField(oldTable, "ownedPanels")).isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
	}

	private BattleNext_FleetStore fleets(List<String> ids) throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		for (String id : ids) {
			store.save(new BattleNext_FleetModel(id, "Fleet " + id + " with its complete name", List.of(), List.of(), List.of()));
		}
		return store;
	}

	private void chooseRule(BattleNext_TournamentEditor editor, String searchText, String action) throws Exception {

		BattleNext_OptionField optionField = optionFields(editor).get(0);
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(optionField, "picker");
		CustomPanelAPI row = (CustomPanelAPI) getField(picker, "row");
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		assertSame(getField(editor, "overlayHost"), getField(picker, "overlayHost"));
		((TextFieldAPI) getField(picker, "search")).setText(searchText);
		plugins.get(popup).advance(0f);
		if (!searchText.isEmpty()) {
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			assertEquals(1, tooltips.get(grid).getComponents().size());
			CustomPanelAPI card = (CustomPanelAPI) tooltips.get(grid).getComponents().get(0);
			assertTrue(hover(card).stream().anyMatch(value -> value.contains(searchText)));
			AtomicBoolean consumed = new AtomicBoolean();
			InputEventAPI click = proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
				case "isLMBDownEvent" -> true;
				case "isConsumed" -> consumed.get();
				case "consume" -> {
					consumed.set(true);
					yield null;
				}
				case "getX" -> (int) card.getPosition().getCenterX();
				case "getY" -> (int) card.getPosition().getCenterY();
				default -> null;
			});
			plugins.get(card).processInput(List.of(click));
			assertTrue(consumed.get());
		}
		plugins.get(popup).buttonPressed(buttonIds.get(action));
		assertTrue(router(editor).isEmpty());
	}

	private BattleNext_TournamentModel tournament(List<String> ids) {
		BattleNext_TournamentModel model = new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 3, Math.max(2, ids.size()));
		model.setFleetIds(ids);
		return model;
	}

	private BattleNext_TournamentEditor editor(BattleNext_FleetStore fleets, BattleNext_RuleStore rules,
			BattleNext_TournamentModel model, float width, float height) {
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(), fleets, rules);
		editor.setInputRouter(new BattleNext_UiInputRouter());
		editor.open(model);
		attachEditor(editor, panel(width, height, new BaseCustomUIPanelPlugin()));
		return editor;
	}

	private BattleNext_TournamentModel draft(BattleNext_TournamentEditor editor) throws Exception {
		return (BattleNext_TournamentModel) getField(editor, "draft");
	}

	private BattleNext_UiInputRouter router(BattleNext_TournamentEditor editor) throws Exception {
		return (BattleNext_UiInputRouter) getField(editor, "inputRouter");
	}

	private BattleNext_TableWidget table(BattleNext_TournamentEditor editor) throws Exception {
		return (BattleNext_TableWidget) getField(editor, "tableWidget");
	}

	private BattleNext_OptionPicker picker(BattleNext_TournamentEditor editor) throws Exception {
		return (BattleNext_OptionPicker) getField(editor, "fleetPicker");
	}

	private CustomPanelAPI row(BattleNext_TournamentEditor editor, int index) throws Exception {
		return (CustomPanelAPI) tooltips.get(table(editor).getContent()).getComponents().get(index);
	}

	private List<LabelAPI> rowLabels(CustomPanelAPI row) {
		List<LabelAPI> labels = new ArrayList<>();
		for (Object child : children.get(row)) {
			TooltipState host = tooltips.get(child);
			for (UIComponentAPI component : host.getComponents()) {
				if (component instanceof LabelAPI label) labels.add(label);
			}
		}
		return labels;
	}

	private ButtonAPI valid(BattleNext_TournamentEditor editor, int index) throws Exception {
		CustomPanelAPI row = row(editor, index);
		for (Object child : children.get(row)) {
			for (UIComponentAPI component : tooltips.get(child).getComponents()) {
				if (component instanceof ButtonAPI button && button.getPosition() == buttonPositions.get("fleet:valid:" + index)) return button;
			}
		}
		throw new AssertionError("Status checkbox missing");
	}

	private List<String> hover(Object target) {
		TooltipMakerAPI host = tooltip(360f, 0f, false);
		attachedTooltips.get(target).createTooltip(host, false, null);
		return tooltips.get(host).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).map(LabelAPI::getText).toList();
	}

	private void setField(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
