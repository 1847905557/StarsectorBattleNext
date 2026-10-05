package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.report.*;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.battle.BattleNext_Round;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_EditorStandardizationTest extends BattleNext_TestVariantFixture {

	@ParameterizedTest
	@CsvSource({"fleet,180", "rule,1000", "tournament,1000", "quick,1000", "pinned,1000", "report,1000", "settings,1000"})
	void everySessionPreservesLongTextFocusSelectionAndScrollAcrossRefreshAndReattach(String kind, int width) throws Exception {

		BattleNext_EditorView editor = editor(kind);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(width, 350f, new BaseCustomUIPanelPlugin());
		editor.setInputRouter(router);
		editor.setOverlayHost(root);
		editor.attach(root);
		CustomPanelAPI editorPanel = (CustomPanelAPI) getField(editor, "panel");
		assertEquals(root.getPosition().getX(), editorPanel.getPosition().getX());
		assertEquals(root.getPosition().getY(), editorPanel.getPosition().getY());
		assertEquals(root.getPosition().getWidth(), editorPanel.getPosition().getWidth());
		assertEquals(root.getPosition().getHeight(), editorPanel.getPosition().getHeight());
		Map<Object, BattleNext_TextField> fields = (Map<Object, BattleNext_TextField>) getField(editor, "fields");
		if (fields.isEmpty()) editor.buttonPressed("add");
		Object identity = fields.keySet().iterator().next();
		BattleNext_TextField field = fields.get(identity);
		String value = "中文😀50% " + "Long text ".repeat(40);
		field.restoreEditState(new BattleNext_TextField.EditState(value, 12, 4, 0f, "Previous value", true));
		BattleNext_TextField.EditState state = field.captureEditState();
		assertEquals(value, state.text());
		assertEquals(-1, field.getMaxChars());
		assertTrue(editor.hasUnsavedChanges());
		int registered = ((Map<?, ?>) getField(router.getSession(), "registrations")).size();
		for (int iteration = 0; iteration < 3; iteration++) {
			editor.refreshContent();
			BattleNext_TextField next = ((Map<Object, BattleNext_TextField>) getField(editor, "fields")).get(identity);
			assertNotSame(field, next);
			assertEquals(state, next.captureEditState());
			assertTrue(editor.hasUnsavedChanges());
			assertEquals(1, children.get(root).size());
			assertEquals(registered, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
			field = next;
		}
		String draft = editor.serializeState();
		editor.detach();
		assertTrue(children.get(root).isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
		editor.attach(root);
		assertEquals(draft, editor.serializeState());
		assertEquals(state, ((Map<Object, BattleNext_TextField>) getField(editor, "fields")).get(identity).captureEditState());
		editor.close();
		assertTrue(children.get(root).isEmpty());
		assertTrue(((Map<?, ?>) getField(router.getSession(), "registrations")).isEmpty());
		editor.close();
	}

	@Test
	void plainTournamentRefreshKeepsTheCompleteLoadedNameAndCleanBaseline() throws Exception {
		String name = "名字 " + "N".repeat(300);
		BattleNext_TournamentModel source = new BattleNext_TournamentModel("cup", name,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(), new BattleNext_FleetStore(), new BattleNext_RuleStore());
		editor.open(source);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		for (int iteration = 0; iteration < 3; iteration++) {
			editor.refreshContent();
			assertEquals(name, ((TextFieldAPI) getField(editor, "nameField")).getText());
			assertFalse(editor.hasUnsavedChanges());
		}
		editor.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"fleet", "rule", "tournament", "pinned", "report"})
	void namedEditorsRejectBlankInputPreserveStoredDataAndAllowCorrection(String kind) throws Exception {

		BattleNext_EditorView editor = editor(kind);
		if (kind.equals("fleet")) ((BattleNext_FleetStore) getField(editor, "store")).reload();
		if (kind.equals("rule")) ((BattleNext_RuleStore) getField(editor, "store")).reload();
		if (kind.equals("report")) ((BattleNext_BattleReportStore) getField(editor, "reports")).reload();
		if (kind.equals("tournament")) {
			((BattleNext_TournamentStore) getField(editor, "store")).reload();
			((BattleNext_RuleStore) getField(editor, "ruleStore")).reload();
			BattleNext_FleetStore fleets = (BattleNext_FleetStore) getField(editor, "fleetStore");
			fleets.reload();
			for (String id : List.of("first", "second")) {
				fleets.save(new BattleNext_FleetModel(id, id, List.of(), List.of(), List.of()));
			}
		}
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		if (editor instanceof BattleNext_TournamentEditor tournament) {
			((TextFieldAPI) getField(editor, "idField")).setText("cup");
			confirmTournamentFleets(tournament, List.of("first", "second"));
		}
		((TextFieldAPI) getField(editor, "nameField")).setText("Original name");
		assertTrue(editor.save(), editor.getStatusText());
		Map<Path, String> stored = storedFiles();
		String expectedStatus = kind.equals("pinned") ? "BattleNext_variant_pin_name_required"
				: kind.equals("report") ? "BattleNext_report_name_required" : "BattleNext_common_name_required";
		for (String blank : List.of("", " \u3000 ")) {
			TextFieldAPI nameField = (TextFieldAPI) getField(editor, "nameField");
			nameField.setText(blank);
			assertFalse(editor.save());
			assertEquals(expectedStatus, editor.getStatusText());
			assertEquals(blank, nameField.getText());
			assertTrue(editor.hasUnsavedChanges());
			assertEquals(stored, storedFiles());
			editor.refreshContent();
			assertEquals(blank, ((TextFieldAPI) getField(editor, "nameField")).getText());
		}
		((TextFieldAPI) getField(editor, "nameField")).setText("Corrected 名称 50%");
		assertTrue(editor.save(), editor.getStatusText());
		assertFalse(editor.hasUnsavedChanges());
		assertNotEquals(stored, storedFiles());
		editor.close();
	}

	private Map<Path, String> storedFiles() throws Exception {
		Map<Path, String> stored = new LinkedHashMap<>();
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				stored.put(path, Files.readString(path, StandardCharsets.UTF_8));
			}
		}
		return stored;
	}

	@ParameterizedTest
	@CsvSource({"8,1", "300,5"})
	void sharedCreationOwnsCompleteTextGeometryViewportsAndActions(int width, int actionCount) throws Exception {

		deferTooltipWidth = true;
		shortenLabels = true;
		LayoutProbeEditor editor = new LayoutProbeEditor(width, actionCount);
		CustomPanelAPI root = panel(1000f, 900f, new BaseCustomUIPanelPlugin());
		attachEditor(editor, root);
		UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(width);
		float fieldWidth = fieldArea.getWidth();
		assertEquals(fieldWidth, editor.nameField.getPosition().getWidth());
		assertEquals(BattleNext_PageLayout.FORM_FIELD_HEIGHT, editor.nameField.getPosition().getHeight());
		assertEquals(editor.fieldContent.getPosition().getX() + fieldArea.getX(),
				editor.nameField.getPosition().getX());
		List<UIComponentAPI> contentComponents = tooltips.get(editor.fieldContent).getComponents();
		assertEquals(editor.nameField.getPosition().getX(), contentComponents.get(0).getPosition().getX());
		assertEquals(editor.value, editor.nameField.getText());
		assertEquals(-1, editor.nameField.getMaxChars());
		List<BattleNext_OptionField> options = optionFields(editor);
		assertEquals(fieldWidth, getField(options.get(0), "fieldWidth"));
		float buttonWidth = Math.min(32f, Math.max(0f, fieldWidth - 1f));
		assertEquals(fieldWidth - buttonWidth, editor.ruleField.getPosition().getWidth());
		assertEquals(editor.value, editor.ruleField.getText());
		assertEquals(-1, editor.ruleField.getMaxChars());
		assertSame(root, getField(editor.addPicker, "overlayHost"));
		assertEquals(Math.min(BattleNext_PageLayout.EDITOR_BUTTON_WIDTH, width), getField(editor.addPicker, "buttonWidth"));
		assertEquals(BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT, getField(editor.addPicker, "buttonHeight"));
		assertEquals(BattleNext_PageLayout.EDITOR_BUTTON_GAP, getField(editor.addPicker, "topGap"));
		assertEquals(Math.min(BattleNext_PageLayout.EDITOR_BUTTON_WIDTH, width), editor.addButton.getPosition().getWidth());
		UIRect cell = new UIRect(0f, 0f, width, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		UIRect expected = BattleNext_PageLayout.editorFieldCell(cell);
		assertEquals(expected.getWidth(), editor.cellField.getPosition().getWidth());
		assertEquals(editor.rowPanel.getPosition().getCenterY(), editor.cellField.getPosition().getCenterY(), 0.001f);
		assertEquals(editor.value, editor.cellField.getText());
		assertTrue(editor.enabledCheckbox.isEnabled());
		assertFalse(editor.validCheckbox.isEnabled());
		assertTrue(editor.validCheckbox.isChecked());
		assertEquals(BattleNext_PageLayout.TABLE_ROW_HEIGHT, editor.validCheckbox.getPosition().getHeight());
		assertEquals(Alignment.MID, labelAlignments.get(editor.nameLabel.getPosition()));
		assertEquals(editor.labelPanel.getPosition().getCenterY(), editor.nameLabel.getPosition().getCenterY(), 0.001f);
		TooltipMakerAPI hover = tooltip(360f, 0f, false);
		attachedTooltips.get(editor.nameLabel).createTooltip(hover, false, null);
		assertTrue(tooltips.get(hover).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).anyMatch(label -> label.getText().equals(editor.value)));
		UIRect[] actions = BattleNext_PageLayout.editorActions(cell, actionCount);
		for (int index = 0; index < actions.length; index++) {
			PositionAPI action = buttonPositions.get("probe:" + index);
			assertEquals(actions[index].getWidth(), action.getWidth());
			assertEquals(BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT, action.getHeight());
			assertEquals(editor.actionsPanel.getPosition().getX() + actions[index].getX(), action.getX());
			assertEquals(editor.actionsPanel.getPosition().getCenterY(), action.getCenterY(), 0.001f);
			assertEquals(index != 0, buttons.get("probe:" + index).get());
		}
		Map<?, ?> registrations = (Map<?, ?>) getField(editor.inputRouter.getSession(), "registrations");
		for (TextFieldAPI field : List.of(editor.nameField, editor.ruleField, editor.cellField)) {
			assertEquals(List.of(editor.fieldContent), registrations.get(field));
		}
		editor.refreshContent();
		assertEquals(3, ((Map<?, ?>) getField(editor.inputRouter.getSession(), "registrations")).size());
		editor.close();
		assertTrue(((Map<?, ?>) getField(editor.inputRouter.getSession(), "registrations")).isEmpty());
		assertTrue(children.get(root).isEmpty());
	}

	private class LayoutProbeEditor extends BattleNext_EditorView {
		private final float areaWidth;
		private final int actionCount;
		private final String value = "Unicode 名称 50% " + "Long content ".repeat(30);
		private TooltipMakerAPI fieldContent;
		private CustomPanelAPI rowPanel;
		private CustomPanelAPI labelPanel;
		private CustomPanelAPI actionsPanel;
		private TextFieldAPI nameField;
		private TextFieldAPI ruleField;
		private TextFieldAPI cellField;
		private ButtonAPI enabledCheckbox;
		private ButtonAPI validCheckbox;
		private ButtonAPI addButton;
		private LabelAPI nameLabel;
		private BattleNext_OptionPicker addPicker;

		private LayoutProbeEditor(float areaWidth, int actionCount) {
			this.areaWidth = areaWidth;
			this.actionCount = actionCount;
		}

		@Override
		protected void buildEditorContent() {
			fieldContent = panel.createUIElement(areaWidth, 700f, true);
			UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(areaWidth);
			nameField = createTextField("name", panel, fieldContent, fieldArea, "Name", value, true, fieldContent);
			ruleField = createOptionField("rule", panel, fieldContent, fieldArea, "Rule", value, optionSource(),
					BattleNext_OptionField.OptionSelectionMode.MULTIPLE, fieldContent);
			enabledCheckbox = createCheckbox("enabled", fieldContent, fieldArea, "Enabled", "enabled", true, true);
			createTextField("readonly", panel, fieldContent, fieldArea, "Read only", value, false, fieldContent);
			addPicker = createPicker(panel, fieldContent, areaWidth, "Select", optionSource(),
					BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {}, fieldContent);
			addButton = createButton(fieldContent, areaWidth, "Add", "add");
			UIRect cell = new UIRect(0f, 0f, areaWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
			rowPanel = panel.createCustomPanel(areaWidth, cell.getHeight(), panel.getPlugin());
			cellField = createCellField("cell", rowPanel, cell, value, fieldContent);
			fieldContent.addCustom(rowPanel, 0f);
			CustomPanelAPI statusPanel = panel.createCustomPanel(areaWidth, cell.getHeight(), panel.getPlugin());
			validCheckbox = createCellCheckbox("valid", statusPanel, cell, "valid", true, false, null);
			fieldContent.addCustom(statusPanel, 0f);
			labelPanel = panel.createCustomPanel(areaWidth, cell.getHeight(), panel.getPlugin());
			nameLabel = createTextCell(labelPanel, cell, value, false, null, TooltipMakerAPI.TooltipLocation.RIGHT, false);
			fieldContent.addCustom(labelPanel, 0f);
			actionsPanel = panel.createCustomPanel(areaWidth, cell.getHeight(), panel.getPlugin());
			EditorAction[] actions = new EditorAction[actionCount];
			for (int index = 0; index < actionCount; index++) {
				actions[index] = new EditorAction(Integer.toString(index), "probe:" + index, index != 0);
			}
			createActions(actionsPanel, cell, actions);
			fieldContent.addCustom(actionsPanel, 0f);
			panel.addUIElement(fieldContent).inTL(0f, 0f);
			bindViewport("content", fieldContent);
		}

		@Override
		public void buttonPressed(Object action) {

		}

		@Override
		protected String draftState() {
			return nameField.getText();
		}

		@Override
		protected boolean submitDraft() {
			return true;
		}

		@Override
		protected void restoreDraft() {
			nameField.setText(value);
		}
	}

	@Test
	void savedPinnedNameStaysCommittedWhenCatalogRefreshFails() throws Exception {
		FailingStore store = new FailingStore();
		store.getWriter().setFail(false);
		store.reload();
		var pin = store.saveVariant("Original", source().getApi());
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> {}, () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("pin:" + pin.getSourcePath());
		BattleNext_PinnedVariantEditor editor = (BattleNext_PinnedVariantEditor) getField(page, "editor");
		((TextFieldAPI) getField(editor, "nameField")).setText("Saved name");
		store.setFailLoad(true);
		assertTrue(page.save());
		assertFalse(page.hasUnsavedChanges());
		assertTrue(page.getStatusText().startsWith("BattleNext_common_saved"));
		store.setFailLoad(false);
		store.reload();
		assertEquals("Saved name", store.get(pin.getPinId()).getDisplayName());
		page.close();
	}

	@Test
	void reportSourceIdentityAndPersistenceDistinguishStandaloneAndEmbeddedRecords() throws Exception {
		BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		reports.reload();
		tournaments.reload();
		BattleNext_BattleReportModel standalone = new BattleNext_BattleReportModel();
		standalone.setId("same");
		standalone.setName("Standalone");
		reports.save(standalone);
		BattleNext_BattleReportModel embedded = new BattleNext_BattleReportModel();
		embedded.setId("same");
		embedded.setName("Embedded");
		BattleNext_TournamentModel cup = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		cup.setFleetIds(List.of("first", "second"));
		new BattleNext_TournamentEngine().start(cup);
		cup.getNextPendingMatch().addRound(new BattleNext_Round("first", "second"));
		cup.getNextPendingMatch().getRounds().get(0).setReport(embedded);
		tournaments.save(cup);
		BattleNext_ReportTarget first = new BattleNext_ReportTarget(standalone, "", null, "", -1);
		BattleNext_ReportTarget second = new BattleNext_ReportTarget(embedded, "", cup, cup.getPendingMatchId(), 0);
		assertNotEquals(first.identity(), second.identity());
		second.rename(reports, tournaments, "Changed embedded");
		reports.reload();
		tournaments.reload();
		assertEquals("Standalone", reports.get("same").getName());
		assertEquals("Changed embedded", tournaments.get("cup").getMatches().get(0).getRounds().get(0).getReport().getName());
		second.delete(reports, tournaments);
		tournaments.reload();
		assertEquals("", tournaments.get("cup").getMatches().get(0).getRounds().get(0).getReport().getId());
		assertNotNull(reports.get("same"));
	}

	@Test
	void failedContentBuildReleasesItsPartialComponentsAndRetainsTheCapturedDraft() throws Exception {
		ProbeEditor editor = new ProbeEditor();
		CustomPanelAPI host = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		attachEditor(editor, host);
		editor.field.setText("Draft 50% " + "X".repeat(300));
		String draft = editor.serializeState();
		editor.fail = true;
		assertThrows(IllegalStateException.class, editor::refreshContent);
		assertTrue(children.get(host).isEmpty());
		assertTrue(((Map<?, ?>) getField(getField(editor, "inputRouter"), "modalFields")).isEmpty());
		assertTrue(((Map<?, ?>) getField(((BattleNext_UiInputRouter) getField(editor, "inputRouter")).getSession(), "registrations")).isEmpty());
		editor.fail = false;
		editor.refreshContent();
		assertEquals(draft, editor.serializeState());
		assertTrue(editor.hasUnsavedChanges());
		editor.close();
	}

	private class ProbeEditor extends BattleNext_EditorView {
		private boolean fail;
		private BattleNext_TextField field;

		@Override
		protected void buildEditorContent() {
			TooltipMakerAPI host = panel.createUIElement(300f, 100f, false);
			field = own(new BattleNext_TextField(inputRouter, panel, 288f, 28f, host));
			field.attach(host, panel);
			bindField("value", field);
			panel.addUIElement(host).inTL(0f, 0f);
			if (fail) throw new IllegalStateException("Injected content failure");
		}

		@Override
		public void buttonPressed(Object action) {

		}

		@Override
		protected String draftState() {
			return field.getText();
		}

		@Override
		protected boolean submitDraft() {
			return true;
		}

		@Override
		protected void restoreDraft() {
			field.setText("");
		}
	}

	private BattleNext_EditorView editor(String kind) throws Exception {
		return switch (kind) {
			case "fleet" -> {
				BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
				editor.open(new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), List.of()), false);
				yield editor;
			}
			case "rule" -> {
				BattleNext_RuleEditor editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
				editor.open(new BattleNext_RuleModel("rule", "Rule"));
				yield editor;
			}
			case "tournament" -> {
				BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(), new BattleNext_FleetStore(), new BattleNext_RuleStore());
				editor.open(null);
				yield editor;
			}
			case "quick" -> new BattleNext_QuickBattleEditor(() -> {}, new BaseCustomUIPanelPlugin());
			case "pinned" -> {
				BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
				store.reload();
				store.saveVariant("Pin", source().getApi());
				store.reload();
				BattleNext_PinnedVariantEditor editor = new BattleNext_PinnedVariantEditor(store);
				editor.open(store.getCatalog().get(0));
				yield editor;
			}
			case "report" -> {
				BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
				report.setId("report");
				report.setName("Report");
				BattleNext_ReportEditor editor = new BattleNext_ReportEditor(new BattleNext_BattleReportStore(), new BattleNext_TournamentStore());
				editor.open(new BattleNext_ReportTarget(report, "", null, "", -1));
				yield editor;
			}
			default -> {
				BattleNext_Settings.load();
				BattleNext_SettingsEditor editor = new BattleNext_SettingsEditor();
				editor.open();
				yield editor;
			}
		};
	}
}
