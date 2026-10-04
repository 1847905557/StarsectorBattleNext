package data.scripts.ui.editor;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.CheckBoxGroup;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_RulesCheck;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class BattleNext_TournamentEditor implements BattleNext_EditorView {

	private BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();

	@Override
	public void setInputRouter(BattleNext_UiInputRouter router) {
		this.inputRouter = router;
	}

	private static final float EDITOR_FIELD_MAX_WIDTH = 360f;
	private static final float EDITOR_FIELD_SIDE_RESERVE = 18f;
	private static final float BRACKET_CHECKBOX_MIN_WIDTH = 100f;
	private static final float BRACKET_LABEL_GAP = 8f;
	private static final float BRACKET_CHECKBOX_GAP = 2f;
	private static final int FLEET_SELECTOR_MAX_CHARS = 8192;
	private static final float FLEET_APPLY_BUTTON_WIDTH = 180f;
	private static final float FLEET_ORDER_BUTTON_WIDTH = 90f;
	private static final float VALIDATION_PANEL_MIN_WIDTH = 100f;
	private static final float VALIDATION_PANEL_SIDE_RESERVE = 24f;
	private static final float VALIDATION_PANEL_HEIGHT = 180f;
	private static final float VALIDATION_PANEL_TOP_GAP = 10f;
	private static final float READ_ONLY_BRACKET_GAP = 2f;
	private static final float FLEET_SECTION_GAP = 10f;
	private static final float FLEET_ENTRY_GAP = 5f;
	private static final float MATCH_SECTION_GAP = 10f;
	private static final float MATCH_ENTRY_GAP = 6f;
	private static final float VALIDATION_REASON_GAP = 5f;
	private static final float FIELD_LABEL_GAP = 5f;
	private static final float READ_ONLY_FIELD_VALUE_GAP = 2f;
	private static final int RANDOM_MAP_ID = -1;
	private static final int LAST_MAP_ID = BattleNext_MissionBattleMap.MAP_COUNT - 1;

	private final BattleNext_TournamentStore store;
	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_RuleStore ruleStore;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private BattleNext_TournamentModel baseline;
	private BattleNext_TournamentModel draft;
	private String savedSnapshot = "";
	private String statusText = "";
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI boField;
	private TextFieldAPI maxFleetField;
	private TextFieldAPI ruleField;
	private TextFieldAPI mapField;
	private TextFieldAPI fleetField;
	private BattleNext_OptionField fleetPicker;
	private final List<BattleNext_TextField> textFields = new ArrayList<>();
	private CheckBoxGroup bracketGroup;
	private final BattleNext_RulesCheck rulesCheck = new BattleNext_RulesCheck();
	private final BattleNext_FleetBuilder fleetBuilder = new BattleNext_FleetBuilder();
	private CustomPanelAPI validationPanel;
	private TooltipMakerAPI validationContent;
	private String validationSnapshot = "";

	public BattleNext_TournamentEditor(BattleNext_TournamentStore store, BattleNext_FleetStore fleetStore,
			BattleNext_RuleStore ruleStore) {
		this.store = store;
		this.fleetStore = fleetStore;
		this.ruleStore = ruleStore;
	}

	public boolean open(BattleNext_TournamentModel source) {
		try {
			BattleNext_TournamentModel next = new BattleNext_TournamentModel();
			if (source != null) next.fromJson(source.toJson());
			baseline = source;
			draft = next;
			idField = null;
			statusText = "";
			return true;
		} catch (Exception exception) {
			statusText = strings.get("load_failed") + ": " + exception.getMessage();
			return false;
		}
	}

	public String getDraftId() {
		return idField == null ? "" : idField.getText().trim();
	}

	public boolean isReadOnly() {
		return draft != null && draft.isStarted();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return fleetPicker != null && fleetPicker.handleGlobalInput(events);
	}

	public void closePicker() {
		if (fleetPicker != null) fleetPicker.closeOverlays();
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		boolean opened = idField == null;
		this.panel = panel;
		createContents();
		if (opened) {
			savedSnapshot = serializeState();
		}
	}

	@Override
	public void createContents() {
		contentRect = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		refreshContent();
	}

	@Override
	public void refreshContent() {
		String[] values = idField == null ? null : new String[]{idField.getText(), nameField.getText(),
				boField.getText(), maxFleetField.getText(), ruleField.getText(), mapField.getText(), fleetField.getText()};
		Object bracket = idField == null ? null : bracketGroup.getCheckedValue();
		closePicker();
		if (fleetPicker != null) fleetPicker.close();
		fleetPicker = null;
		for (BattleNext_TextField field : textFields) {
			field.close();
		}
		textFields.clear();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		addDetails(content);
		if (values != null) {
			TextFieldAPI[] fields = {idField, nameField, boField, maxFleetField, ruleField, mapField, fleetField};
			for (int index = 0; index < fields.length; index++) {
				fields[index].setText(values[index]);
			}
			bracketGroup.tryCheckValue(bracket);
			bracketGroup.updateCheck();
		}
		contentPanel.addUIElement(content).inTL(0f, 0f);
		refreshDraftValidation();
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (isReadOnly() || !(buttonId instanceof String action)) return;
		if (action.startsWith("bracket:")) {
			bracketGroup.updateCheck();
			return;
		}
		if (!action.startsWith("fleet:")) return;
		String[] fields = {idField.getText(), nameField.getText(), boField.getText(),
				maxFleetField.getText(), ruleField.getText(), mapField.getText()};
		List<String> fleets = selectedFleets();
		if (!"fleet:apply".equals(action)) {
			int index = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
			int target = action.startsWith("fleet:up:") ? index - 1 : index + 1;
			if (index < 0 || target < 0 || index >= fleets.size() || target >= fleets.size()) return;
			String moved = fleets.remove(index);
			fleets.add(target, moved);
		}
		captureTextFields();
		draft.setFleetIds(fleets);
		fleetField.setText(String.join(",", fleets));
		refreshContent();
		TextFieldAPI[] current = {idField, nameField, boField, maxFleetField, ruleField, mapField};
		for (int field = 0; field < current.length; field++) current[field].setText(fields[field]);
	}

	private void addDetails(TooltipMakerAPI content) {
		content.addTitle(isReadOnly() ? tournamentStrings.get("locked_message") : tournamentStrings.get("editor_title"));
		idField = field(content, tournamentStrings.get("id"), draft.getId(), content);
		nameField = field(content, tournamentStrings.get("name"), draft.getName(), content);
		content.addPara(tournamentStrings.get("bracket"), BRACKET_LABEL_GAP);
		bracketGroup = new CheckBoxGroup();
		if (isReadOnly()) {
			content.addPara(bracketLabel(draft.getBracketType()), READ_ONLY_BRACKET_GAP);
		} else {
			for (TournamentBracketType type : TournamentBracketType.values()) {
				ButtonAPI button = content.addCheckbox(Math.max(BRACKET_CHECKBOX_MIN_WIDTH,
						contentRect.getWidth() - EDITOR_FIELD_SIDE_RESERVE),
						BattleNext_PageLayout.FORM_FIELD_HEIGHT,
						bracketLabel(type), "bracket:" + type.name(), ButtonAPI.UICheckboxSize.SMALL,
						BRACKET_CHECKBOX_GAP);
				bracketGroup.addCheckBox(button, type);
			}
			bracketGroup.tryCheckValue(draft.getBracketType());
			bracketGroup.updateCheck();
		}
		boField = field(content, tournamentStrings.get("best_of_count"), Integer.toString(draft.getBoCount()), content);
		maxFleetField = field(content, tournamentStrings.get("max_fleets"), Integer.toString(draft.getMaxFleet()), content);
		ruleField = field(content, tournamentStrings.get("rules"), draft.getRuleId(), content);
		mapField = field(content, battleStrings.get("map_number_label"), Integer.toString(draft.getMapId()), content);
		content.addPara(tournamentStrings.get("participant_fleets"), FLEET_SECTION_GAP);
		if (isReadOnly()) {
			for (String fleet : draft.getFleetIds()) content.addPara(fleet, FLEET_ENTRY_GAP);
		} else {
			fleetPicker = new BattleNext_OptionField(inputRouter, panel,
					Math.min(EDITOR_FIELD_MAX_WIDTH, contentRect.getWidth() - EDITOR_FIELD_SIDE_RESERVE),
					new BattleNext_FleetSourceProvider(fleetStore), BattleNext_OptionField.OptionSelectionMode.MULTIPLE, content);
			fleetPicker.attach(content, panel);
			fleetField = fleetPicker.getField();
			fleetField.setText(String.join(",", draft.getFleetIds()));
			fleetField.setMaxChars(FLEET_SELECTOR_MAX_CHARS);
			BattleNext_PageLayout.button(content, tournamentStrings.get("apply_participant_selection"), "fleet:apply",
					FLEET_APPLY_BUTTON_WIDTH);
			for (int index = 0; index < draft.getFleetIds().size(); index++) {
				String fleetId = draft.getFleetIds().get(index);
				BattleNext_FleetModel fleet = fleetStore.get(fleetId);
				String label = (index + 1) + ". " + (fleet == null ? fleetId : fleet.getName());
				content.addPara(label, FLEET_ENTRY_GAP);
				BattleNext_PageLayout.addIdTooltip(content, label, fleetId);
				if (index > 0) BattleNext_PageLayout.button(content, strings.get("move_up"), "fleet:up:" + index,
						FLEET_ORDER_BUTTON_WIDTH);
				if (index + 1 < draft.getFleetIds().size()) {
					BattleNext_PageLayout.button(content, strings.get("move_down"), "fleet:down:" + index,
							FLEET_ORDER_BUTTON_WIDTH);
				}
			}
		}
		if (isReadOnly()) {
			content.addPara(tournamentStrings.get("matches"), MATCH_SECTION_GAP);
			for (BattleNext_Match match : draft.getMatches()) {
				content.addPara(match.getId() + " " + match.getPlayer() + " / " + match.getEnemy()
						+ " [" + tournamentStrings.get("match_status_" + match.getStatus().name().toLowerCase(Locale.ROOT))
						+ "]", MATCH_ENTRY_GAP);
			}
		}
		if (!isReadOnly()) {
			validationPanel = panel.createCustomPanel(Math.max(VALIDATION_PANEL_MIN_WIDTH,
					contentRect.getWidth() - VALIDATION_PANEL_SIDE_RESERVE), VALIDATION_PANEL_HEIGHT, panel.getPlugin());
			content.addCustom(validationPanel, VALIDATION_PANEL_TOP_GAP);
			validationContent = null;
			validationSnapshot = "";
		}
	}

	public void refreshDraftValidation() {
		if (isReadOnly() || validationPanel == null || ruleField == null || fleetField == null) return;
		String snapshot = ruleField.getText() + "|" + fleetField.getText();
		if (snapshot.equals(validationSnapshot)) return;
		validationSnapshot = snapshot;
		List<String> reasons = new ArrayList<>();
		String ruleId = ruleField.getText().trim();
		if (!ruleId.isEmpty()) {
			BattleNext_RuleModel rule = ruleStore.get(ruleId);
			if (rule == null) {
				reasons.add(tournamentStrings.get("rules_unavailable") + ": " + ruleId);
			} else {
				reasons.addAll(rulesCheck.missingRestrictions(rule));
				for (String fleetId : selectedFleets()) {
					BattleNext_FleetModel fleet = fleetStore.get(fleetId);
					if (fleet == null) {
						reasons.add(tournamentStrings.get("invalid_participant_fleet") + ": " + fleetId);
						continue;
					}
					BattleNext_AssembledFleet assembledFleet = fleetBuilder.build(fleet);
					for (String error : assembledFleet.getMemberErrors()) reasons.add(fleetId + " " + error);
					if (assembledFleet.isComplete()) reasons.addAll(rulesCheck.validateFleet(assembledFleet, rule));
				}
			}
		}
		if (validationContent != null) validationPanel.removeComponent(validationContent);
		validationContent = validationPanel.createUIElement(validationPanel.getPosition().getWidth(),
				VALIDATION_PANEL_HEIGHT, true);
		validationContent.addTitle(ruleStrings.get("fleet_validation"));
		if (reasons.isEmpty()) validationContent.addPara(ruleStrings.get("validation_clear"), VALIDATION_REASON_GAP);
		for (String reason : reasons) validationContent.addPara(reason,
				VALIDATION_REASON_GAP);
		validationPanel.addUIElement(validationContent).inTL(0f, 0f);
	}

	private String bracketLabel(TournamentBracketType type) {
		return tournamentStrings.get("bracket_" + type.name().toLowerCase(Locale.ROOT));
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value, TooltipMakerAPI... viewports) {
		host.addPara(label, FIELD_LABEL_GAP);
		if (isReadOnly()) {
			host.addPara(value, READ_ONLY_FIELD_VALUE_GAP);
			return null;
		}
		float width = Math.min(EDITOR_FIELD_MAX_WIDTH, contentRect.getWidth() - EDITOR_FIELD_SIDE_RESERVE);
		BattleNext_TextField widget = new BattleNext_TextField(inputRouter, panel, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, viewports);
		textFields.add(widget);
		widget.attach(host, panel);
		widget.setText(value);
		TextFieldAPI result = widget;
		result.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		return result;
	}

	private void captureTextFields() {
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		draft.setBracketType((TournamentBracketType) bracketGroup.getCheckedValue());
		draft.setRuleId(ruleField.getText());
	}

	private List<String> selectedFleets() {
		List<String> result = new ArrayList<>();
		for (String value : fleetField.getText().split(",")) {
			if (!value.trim().isEmpty()) result.add(value.trim());
		}
		return result;
	}

	@Override
	public String serializeState() {
		if (idField == null || isReadOnly()) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, boField,
				maxFleetField, ruleField, mapField, fleetField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		value.append(bracketGroup.getCheckedValue());
		return value.toString();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return !isReadOnly() && !serializeState().equals(savedSnapshot);
	}

	@Override
	public boolean serialize() {
		if (isReadOnly()) return true;
		if (!store.isLoaded() || !fleetStore.isLoaded() || !ruleStore.isLoaded()) {
			statusText = strings.get("load_failed");
			return false;
		}
		int boCount;
		int maxFleet;
		int map;
		try {
			boCount = Integer.parseInt(boField.getText().trim());
			maxFleet = Integer.parseInt(maxFleetField.getText().trim());
			map = Integer.parseInt(mapField.getText().trim());
		} catch (NumberFormatException exception) {
			BattleNext_Logger.warn(BattleNext_TournamentEditor.class, "tournament.configuration.rejected",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).put("criterion", "Integer best-of, fleet limit and map").build(), exception);
			statusText = tournamentStrings.get("invalid_configuration");
			return false;
		}
		List<String> fleets = selectedFleets();
		if (bracketGroup.getCheckedValue() == null || boCount < 1
				|| maxFleet < BattleNext_TournamentModel.MIN_FLEET_COUNT
				|| maxFleet > BattleNext_TournamentModel.MAX_FLEET_COUNT
				|| fleets.size() < BattleNext_TournamentModel.MIN_FLEET_COUNT || fleets.size() > maxFleet
				|| new HashSet<>(fleets).size() != fleets.size()
				|| map < RANDOM_MAP_ID || map > LAST_MAP_ID) {
			BattleNext_Logger.warn(BattleNext_TournamentEditor.class, "tournament.configuration.rejected",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).put("criterion", "Bracket, positive best-of, unique participants within fleet limits and supported map").build());
			statusText = tournamentStrings.get("invalid_configuration");
			return false;
		}
		for (String fleet : fleets) {
			if (fleetStore.get(fleet) == null) {
				BattleNext_Logger.warn(BattleNext_TournamentEditor.class, "tournament.participant.rejected",
				BattleNext_Logger.fields().put("tournament", draft).put("fleet_id", fleet).put("input_state", serializeState()).build());
				statusText = tournamentStrings.get("invalid_participant_fleet") + ": " + fleet;
				return false;
			}
		}
		captureTextFields();
		draft.setBoCount(boCount);
		draft.setMaxFleet(maxFleet);
		draft.setMapId(map);
		draft.setFleetIds(fleets);
		try {
			BattleNext_TournamentModel nextBaseline = new BattleNext_TournamentModel();
			nextBaseline.fromJson(draft.toJson());
			store.save(draft);
			baseline = nextBaseline;
			savedSnapshot = serializeState();
			statusText = strings.get("saved");
			return true;
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_TournamentEditor.class, "tournament.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).build(), exception);
			statusText = strings.get("save_failed") + ": " + exception.getMessage();
			return false;
		}
	}

	@Override
	public void deserialize() {
		if (!open(baseline)) return;
		refreshContent();
		savedSnapshot = serializeState();
	}

	@Override
	public String getStatusText() {
		return statusText;
	}

}
