package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_TournamentEditorTest extends BattleNext_TestPageFixture {

	@Test
	void saveAggregatesConfigurationFieldsBeforeSubmittingAndRebuildsErrorsOnRetry() throws Exception {
		var store = new BattleNext_TournamentStore();
		store.reload();
		var rules = new BattleNext_RuleStore();
		rules.reload();
		var editor = new BattleNext_TournamentEditor(store, rules);
		editor.open(null);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		for (String field : List.of("idField", "nameField", "bestOfField", "maxFleetsField", "mapField")) {
			((TextFieldAPI) getField(editor, field)).setText("");
		}
		assertFalse(editor.save());
		assertEquals(5, editor.getSaveErrors().size());
		List<String> keys = List.of("name", "id", "best_of_count", "max_fleets");
		for (int index = 0; index < keys.size(); index++) {
			String category = index == 0 ? "Common" : "Tournament";
			String prefix = index == 0 ? "BattleNext_common_" : "BattleNext_tournament_";
			assertTrue(editor.getSaveErrors().get(index).contains(BattleNext_TestTextCatalog.text(category, prefix + keys.get(index))));
		}
		assertTrue(editor.getSaveErrors().get(4).contains(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_number_label")));
		assertTrue(store.getAll().isEmpty());
		((TextFieldAPI) getField(editor, "idField")).setText("cup");
		((TextFieldAPI) getField(editor, "nameField")).setText("Cup");
		((TextFieldAPI) getField(editor, "bestOfField")).setText("1");
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("1");
		((TextFieldAPI) getField(editor, "mapField")).setText("-1");
		assertFalse(editor.save());
		assertEquals(1, editor.getSaveErrors().size());
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("2");
		assertTrue(editor.save(), editor.getStatusText());
		assertTrue(editor.getSaveErrors().isEmpty());
		editor.close();
	}

	@Test
	void configurationFieldsUseLeftColumnAndPreserveUnfinishedInputsAcrossRefresh() throws Exception {
		deferTooltipWidth = true;
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournaments, rules);
		editor.open(new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 3, 4));
		attachEditor(editor, panel(1000f, 300f, new BaseCustomUIPanelPlugin()));
		TooltipMakerAPI left = (TooltipMakerAPI) getField(editor, "metadataContent");
		CustomPanelAPI contentPanel = (CustomPanelAPI) getField(editor, "panel");
		TooltipMakerAPI reserved = (TooltipMakerAPI) children.get(contentPanel).get(1);
		assertTrue(tooltips.get(reserved).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).anyMatch(label -> label.getText().equals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_tournament_instance"))));
		float leftWidth = BattleNext_PageLayout.editorColumns(1000f, 300f)[0].getWidth();
		for (String name : List.of("idField", "nameField", "bestOfField", "maxFleetsField", "mapField")) {
			TextFieldAPI field = (TextFieldAPI) getField(editor, name);
			assertEquals(leftWidth - 12f, field.getPosition().getWidth());
			assertEquals(List.of(left), ((Map<?, ?>) getField(router(editor).getSession(), "registrations")).get(field));
		}
		CustomPanelAPI ruleRow = (CustomPanelAPI) getField(optionFields(editor).get(0), "row");
		assertEquals(leftWidth - 12f, ruleRow.getPosition().getWidth());
		assertEquals(ruleRow.getPosition().getWidth() - 32f, ((TextFieldAPI) getField(editor, "ruleField")).getPosition().getWidth());
		((TextFieldAPI) getField(editor, "bestOfField")).setText("pending");
		left.getExternalScroller().setYOffset(95f);
		editor.refreshContent();
		assertEquals("pending", ((TextFieldAPI) getField(editor, "bestOfField")).getText());
		assertEquals(95f, ((TooltipMakerAPI) getField(editor, "metadataContent")).getExternalScroller().getYOffset());
		assertFalse(editor.save());
		assertEquals("pending", ((TextFieldAPI) getField(editor, "bestOfField")).getText());
		editor.discard();
		assertEquals("3", ((TextFieldAPI) getField(editor, "bestOfField")).getText());
		assertFalse(editor.hasUnsavedChanges());
		editor.close();
		assertFalse(router(editor).getSession().hasInputState());
	}

	@Test
	void configurationSavesWithoutParticipantsAndFailureKeepsCompleteInput() throws Exception {
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournaments, rules);
		editor.open(null);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(editor, "idField")).setText("cup");
		((TextFieldAPI) getField(editor, "nameField")).setText("Cup 中文 50%");
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("7");
		((TextFieldAPI) getField(editor, "bestOfField")).setText("3");
		((TextFieldAPI) getField(editor, "mapField")).setText("-1");
		assertTrue(editor.save(), editor.getStatusText());
		tournaments.reload();
		assertEquals(7, tournaments.get("cup").getMaxFleet());
		assertEquals("Cup 中文 50%", tournaments.get("cup").getName());
		assertEquals(-1, tournaments.get("cup").getMapId());
		assertFalse(editor.hasUnsavedChanges());
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("1");
		assertFalse(editor.save());
		assertEquals("1", ((TextFieldAPI) getField(editor, "maxFleetsField")).getText());
		assertEquals(7, tournaments.get("cup").getMaxFleet());
		editor.discard();
		assertEquals("7", ((TextFieldAPI) getField(editor, "maxFleetsField")).getText());
		editor.close();
	}

	@Test
	void ruleSelectionSearchCancelAndCommitPreserveInputsAndSelectedIdentity() throws Exception {
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		rules.save(new BattleNext_RuleModel("rule_a", "Rule A"));
		rules.save(new BattleNext_RuleModel("rule_b", "Rule B 50%"));
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		BattleNext_TournamentModel model = new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 3, 2);
		model.setRuleId("rule_a");
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournaments, rules);
		editor.open(model);
		attachEditor(editor, panel(1000f, 900f, new BaseCustomUIPanelPlugin()));
		TextFieldAPI ruleField = (TextFieldAPI) getField(editor, "ruleField");
		((TextFieldAPI) getField(editor, "bestOfField")).setText("pending");
		String before = editor.serializeState();
		chooseRule(editor, "Rule B", "CANCEL");
		assertEquals(before, editor.serializeState());
		chooseRule(editor, "Rule B", "CONFIRM");
		assertEquals("rule_b", ruleField.getText());
		assertFalse(editor.save());
		editor.refreshContent();
		assertEquals("rule_b", ((TextFieldAPI) getField(editor, "ruleField")).getText());
		assertEquals("pending", ((TextFieldAPI) getField(editor, "bestOfField")).getText());
		((TextFieldAPI) getField(editor, "bestOfField")).setText("3");
		assertTrue(editor.save(), editor.getStatusText());
		tournaments.reload();
		assertEquals("rule_b", tournaments.get("cup").getRuleId());
		ruleField = (TextFieldAPI) getField(editor, "ruleField");
		ruleField.setText("unknown");
		chooseRule(editor, "", "CONFIRM");
		assertEquals("unknown", ruleField.getText());
		ruleField.setText("");
		chooseRule(editor, "", "CONFIRM");
		assertEquals("", ruleField.getText());
		assertTrue(editor.save(), editor.getStatusText());
		rules.save(new BattleNext_RuleModel("rule_c", "New rule"));
		chooseRule(editor, "New rule", "CONFIRM");
		assertEquals("rule_c", ruleField.getText());
		editor.discard();
		assertEquals("", ((TextFieldAPI) getField(editor, "ruleField")).getText());
		editor.close();
		assertTrue(router(editor).isEmpty());
		assertFalse(router(editor).getSession().hasInputState());
	}

	private void chooseRule(BattleNext_TournamentEditor editor, String searchText, String action) throws Exception {

		BattleNext_OptionField optionField = optionFields(editor).get(0);
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(optionField, "picker");
		CustomPanelAPI row = (CustomPanelAPI) getField(picker, "row");
		plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
		CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
		((TextFieldAPI) getField(picker, "search")).setText(searchText);
		plugins.get(popup).advance(0f);
		if (!searchText.isEmpty()) {
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			assertEquals(1, tooltips.get(grid).getComponents().size());
			CustomPanelAPI card = (CustomPanelAPI) tooltips.get(grid).getComponents().get(0);
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

	private BattleNext_UiInputRouter router(BattleNext_TournamentEditor editor) throws Exception {
		return (BattleNext_UiInputRouter) getField(editor, "inputRouter");
	}
}
