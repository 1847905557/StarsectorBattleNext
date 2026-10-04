package data.scripts.ui.editor;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.CheckBoxGroup;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_RulesCheck;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;

import org.json.JSONArray;

import java.util.*;

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
	private static final float FLEET_TITLE_HEIGHT = 24f;
	private static final float[] COLUMN_SHARES = {0.3f, 0.34f, 0.16f, 0.2f};
	private static final int FLEET_COLUMN = 0;
	private static final int AUTHOR_COLUMN = 1;
	private static final int VALID_COLUMN = 2;
	private static final int ACTIONS_COLUMN = 3;
	private static final int ACTION_BUTTON_COUNT = 3;
	private static final float ACTION_BUTTON_MIN_WIDTH = 22f;
	private static final float ACTION_BUTTON_SIDE_PADDING = 8f;
	private static final float READ_ONLY_BRACKET_GAP = 2f;
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
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private TooltipMakerAPI detailsContent;
	private TooltipMakerAPI fleetsContent;
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
	private BattleNext_OptionPicker fleetPicker;
	private BattleNext_TableWidget tableWidget;
	private float tableScrollOffset;
	private final BattleNext_FleetSourceProvider fleetSource;
	private final List<BattleNext_TextField> textFields = new ArrayList<>();
	private CheckBoxGroup bracketGroup;
	private final BattleNext_RulesCheck rulesCheck = new BattleNext_RulesCheck();
	private final BattleNext_FleetBuilder fleetBuilder = new BattleNext_FleetBuilder();
	private final Map<String, List<String>> validationReasons = new LinkedHashMap<>();
	private String validationSnapshot = "";

	public BattleNext_TournamentEditor(BattleNext_TournamentStore store, BattleNext_FleetStore fleetStore,
			BattleNext_RuleStore ruleStore) {
		this.store = store;
		this.fleetStore = fleetStore;
		this.ruleStore = ruleStore;
		fleetSource = new BattleNext_FleetSourceProvider(fleetStore);
	}

	public boolean open(BattleNext_TournamentModel source) {
		try {
			BattleNext_TournamentModel next = new BattleNext_TournamentModel();
			if (source != null) next.fromJson(source.toJson());
			baseline = source;
			draft = next;
			idField = null;
			tableScrollOffset = 0f;
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

	public void releaseResources() {
		if (fleetPicker != null) fleetPicker.close();
		fleetPicker = null;
		if (tableWidget != null) {
			tableScrollOffset = tableWidget.getContent().getExternalScroller().getYOffset();
			tableWidget.close();
		}
		tableWidget = null;
		for (BattleNext_TextField field : textFields) {
			field.close();
		}
		textFields.clear();
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
				boField.getText(), maxFleetField.getText(), ruleField.getText(), mapField.getText()};
		Object bracket = idField == null ? null : bracketGroup.getCheckedValue();
		float detailsOffset = values == null ? 0f : detailsContent.getExternalScroller().getYOffset();
		float fleetsOffset = values == null ? 0f : fleetsContent.getExternalScroller().getYOffset();
		releaseResources();
		float tableOffset = values == null ? 0f : tableScrollOffset;
		validationSnapshot = "";
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		float usableWidth = Math.max(BattleNext_PageLayout.EDITOR_MIN_SPLIT_WIDTH,
				contentRect.getWidth() - BattleNext_PageLayout.EDITOR_COLUMN_GAP);
		float leftWidth = (float) Math.floor(usableWidth * BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		float rightWidth = usableWidth - leftWidth;
		detailsContent = contentPanel.createUIElement(leftWidth, contentRect.getHeight(), true);
		addDetails(detailsContent, leftWidth);
		fleetsContent = contentPanel.createUIElement(rightWidth, contentRect.getHeight(), true);
		if (values != null) {
			TextFieldAPI[] fields = {idField, nameField, boField, maxFleetField, ruleField, mapField};
			for (int index = 0; index < fields.length; index++) {
				fields[index].setText(values[index]);
			}
			bracketGroup.tryCheckValue(bracket);
			bracketGroup.updateCheck();
		}
		addFleets(fleetsContent, rightWidth);
		contentPanel.addUIElement(detailsContent).inTL(0f, 0f);
		contentPanel.addUIElement(fleetsContent).inTL(leftWidth + BattleNext_PageLayout.EDITOR_COLUMN_GAP, 0f);
		refreshDraftValidation();
		detailsContent.getExternalScroller().setYOffset(Math.min(detailsOffset,
				Math.max(0f, detailsContent.getHeightSoFar() - contentRect.getHeight())));
		fleetsContent.getExternalScroller().setYOffset(Math.min(fleetsOffset,
				Math.max(0f, fleetsContent.getHeightSoFar() - contentRect.getHeight())));
		tableWidget.getContent().getExternalScroller().setYOffset(Math.min(tableOffset,
				Math.max(0f, tableWidget.getContent().getHeightSoFar()
						- tableWidget.getContent().getExternalScroller().getPosition().getHeight())));
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (isReadOnly() || !(buttonId instanceof String action)) return;
		if (action.startsWith("bracket:")) {
			bracketGroup.updateCheck();
			return;
		}
		if (!action.startsWith("fleet:up:") && !action.startsWith("fleet:down:")
				&& !action.startsWith("fleet:remove:")) return;
		List<String> fleets = new ArrayList<>(draft.getFleetIds());
		int index = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
		if (action.startsWith("fleet:remove:")) {
			fleets.remove(index);
		} else {
			int target = action.startsWith("fleet:up:") ? index - 1 : index + 1;
			if (index < 0 || target < 0 || index >= fleets.size() || target >= fleets.size()) return;
			Collections.swap(fleets, index, target);
		}
		draft.setFleetIds(fleets);
		refreshDraftValidation();
	}

	private void addDetails(TooltipMakerAPI content, float width) {

		content.addTitle(isReadOnly() ? tournamentStrings.get("locked_message") : tournamentStrings.get("editor_title"));
		idField = field(content, tournamentStrings.get("id"), draft.getId(), width, content);
		nameField = field(content, tournamentStrings.get("name"), draft.getName(), width, content);
		content.addPara(tournamentStrings.get("bracket"), BRACKET_LABEL_GAP);
		bracketGroup = new CheckBoxGroup();
		if (isReadOnly()) {
			content.addPara(bracketLabel(draft.getBracketType()), READ_ONLY_BRACKET_GAP);
		} else {
			for (TournamentBracketType type : TournamentBracketType.values()) {
				ButtonAPI button = content.addCheckbox(Math.max(BRACKET_CHECKBOX_MIN_WIDTH,
						width - EDITOR_FIELD_SIDE_RESERVE),
						BattleNext_PageLayout.FORM_FIELD_HEIGHT,
						bracketLabel(type), "bracket:" + type.name(), ButtonAPI.UICheckboxSize.SMALL,
						BRACKET_CHECKBOX_GAP);
				bracketGroup.addCheckBox(button, type);
			}
			bracketGroup.tryCheckValue(draft.getBracketType());
			bracketGroup.updateCheck();
		}
		boField = field(content, tournamentStrings.get("best_of_count"), Integer.toString(draft.getBoCount()), width, content);
		maxFleetField = field(content, tournamentStrings.get("max_fleets"), Integer.toString(draft.getMaxFleet()), width, content);
		ruleField = field(content, tournamentStrings.get("rules"), draft.getRuleId(), width, content);
		mapField = field(content, battleStrings.get("map_number_label"), Integer.toString(draft.getMapId()), width, content);
	}

	private void addFleets(TooltipMakerAPI content, float width) {

		content.addTitle(tournamentStrings.get("participant_fleets"));
		float tableHeight = Math.max(1f, contentRect.getHeight() - FLEET_TITLE_HEIGHT);
		CustomPanelAPI tablePanel = panel.createCustomPanel(width, tableHeight, panel.getPlugin());
		content.addComponent(tablePanel).inTL(0f, FLEET_TITLE_HEIGHT);
		String[] headers = {tournamentStrings.get("fleet"), fleetStrings.get("author"),
				tournamentStrings.get("valid"), strings.get("actions")};
		tableWidget = new BattleNext_TableWidget(panel.getPlugin(), headers, COLUMN_SHARES,
				0, this::addFleetRow, (rowWidth, rows) -> {
			if (isReadOnly()) return;
			fleetPicker = new BattleNext_OptionPicker(inputRouter, panel, Math.min(190f, rowWidth),
					BattleNext_PageLayout.FORM_FIELD_HEIGHT, tournamentStrings.get("add_fleet"), 2f,
					fleetSource, BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
					List::of, this::addFleets, rows, content);
			fleetPicker.attach(rows, panel);
		});
		tableWidget.attach(tablePanel);
	}

	public void refreshDraftValidation() {

		if (tableWidget == null) return;
		String ruleInput = isReadOnly() ? draft.getRuleId() : ruleField.getText();
		String snapshot = ruleInput.length() + ":" + ruleInput + new JSONArray(draft.getFleetIds());
		if (snapshot.equals(validationSnapshot)) return;
		validationSnapshot = snapshot;
		validationReasons.clear();
		String ruleId = ruleInput.trim();
		BattleNext_RuleModel rule = ruleStore.get(ruleId);
		for (String fleetId : draft.getFleetIds()) {
			List<String> reasons = new ArrayList<>();
			try {
				if (!ruleId.isEmpty()) {
					if (rule == null) {
						reasons.add(tournamentStrings.get("rules_unavailable") + ": " + ruleId);
					} else {
						reasons.addAll(rulesCheck.missingRestrictions(rule));
					}
				}
				BattleNext_FleetModel fleet = fleetStore.get(fleetId);
				if (fleet == null) {
					reasons.add(tournamentStrings.get("invalid_participant_fleet") + ": " + fleetId);
				} else {
					reasons.addAll(fleet.getLoadErrors());
					BattleNext_AssembledFleet assembled = fleetBuilder.build(fleet);
					reasons.addAll(assembled.getMemberErrors());
					if (assembled.isComplete() && rule != null) reasons.addAll(rulesCheck.validateFleet(assembled, rule));
				}
			} catch (RuntimeException exception) {
				reasons.add(tournamentStrings.get("validation_failed") + ": " + exception);
				BattleNext_Logger.warn(BattleNext_TournamentEditor.class, "tournament.fleet.validation.failed",
						BattleNext_Logger.fields().put("tournament_id", getDraftId()).put("fleet_id", fleetId)
								.put("rule_id", ruleId).put("reasons", reasons).build(), exception);
			}
			validationReasons.put(fleetId, List.copyOf(new LinkedHashSet<>(reasons)));
			if (!reasons.isEmpty()) {
				BattleNext_Logger.warn(BattleNext_TournamentEditor.class, "tournament.fleet.validation.rejected",
						BattleNext_Logger.fields().put("tournament_id", getDraftId()).put("fleet_id", fleetId)
								.put("rule_id", ruleId).put("reasons", validationReasons.get(fleetId)).build());
			}
		}
		if (fleetPicker != null) fleetPicker.close();
		tableWidget.setRowCount(draft.getFleetIds().size());
		tableWidget.refresh();
	}

	private void addFleets(List<String> ids) {
		if (ids.isEmpty()) return;
		List<String> fleets = new ArrayList<>(draft.getFleetIds());
		for (String id : ids) {
			if (!fleets.contains(id)) fleets.add(id);
		}
		draft.setFleetIds(fleets);
		refreshDraftValidation();
	}

	private void addFleetRow(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI content) {

		String id = draft.getFleetIds().get(index);
		BattleNext_FleetModel fleet = fleetStore.get(id);
		String name = fleet == null ? id : fleet.getName();
		String author = fleet == null || fleet.getAuthor().isEmpty() ? "—" : fleet.getAuthor();
		List<String> reasons = validationReasons.get(id);
		CustomPanelAPI row = panel.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, panel.getPlugin());
		new UIRect(0f, 0f, rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBorder(row,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		addCell(row, cells[FLEET_COLUMN], name, new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return fleetSource.getTooltipWidth();
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				fleetSource.createTooltip(tooltip, new BattleNext_OptionPicker.OptionChoice(id, name));
			}
		});
		addCell(row, cells[AUTHOR_COLUMN], author, new BaseTooltipCreator() {
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addPara(author, 0f);
			}
		});
		float checkboxWidth = Math.min(BattleNext_PageLayout.TABLE_CHECKBOX_MAX_WIDTH, cells[VALID_COLUMN].getWidth());
		TooltipMakerAPI valid = row.createUIElement(checkboxWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
		ButtonAPI checkbox = valid.addCheckbox(checkboxWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, "",
				"fleet:valid:" + index, ButtonAPI.UICheckboxSize.SMALL, 0f);
		checkbox.setChecked(reasons.isEmpty());
		checkbox.setEnabled(false);
		valid.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 360f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				BattleNext_UiTheme.configure(tooltip);
				tooltip.addTitle(name);
				BattleNext_PageLayout.addMutedIdLine(tooltip, id, 5f);
				if (reasons.isEmpty()) tooltip.addPara(ruleStrings.get("validation_clear"), VALIDATION_REASON_GAP);
				for (String reason : reasons) {
					tooltip.addPara(reason, VALIDATION_REASON_GAP);
				}
			}
		}, TooltipMakerAPI.TooltipLocation.RIGHT);
		valid.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		row.addUIElement(valid).inTL(cells[VALID_COLUMN].getX()
				+ (cells[VALID_COLUMN].getWidth() - checkboxWidth) * 0.5f, 0f);
		float buttonWidth = Math.max(ACTION_BUTTON_MIN_WIDTH,
				(cells[ACTIONS_COLUMN].getWidth() - ACTION_BUTTON_SIDE_PADDING) / ACTION_BUTTON_COUNT);
		float buttonsX = cells[ACTIONS_COLUMN].getX()
				+ (cells[ACTIONS_COLUMN].getWidth() - buttonWidth * ACTION_BUTTON_COUNT) * 0.5f;
		float buttonY = (BattleNext_PageLayout.TABLE_ROW_HEIGHT - BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT) * 0.5f;
		BattleNext_PageLayout.fixedButton(row, new UIRect(buttonsX, buttonY, buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT),
				"^", "fleet:up:" + index, CutStyle.ALL, false).setEnabled(!isReadOnly() && index > 0);
		BattleNext_PageLayout.fixedButton(row, new UIRect(buttonsX + buttonWidth, buttonY, buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT),
				"V", "fleet:down:" + index, CutStyle.ALL, false).setEnabled(!isReadOnly() && index + 1 < draft.getFleetIds().size());
		BattleNext_PageLayout.fixedButton(row, new UIRect(buttonsX + buttonWidth * (ACTION_BUTTON_COUNT - 1), buttonY,
				buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT), "X", "fleet:remove:" + index, CutStyle.ALL, false).setEnabled(!isReadOnly());
		content.addCustom(row, 0f);
	}

	private void addCell(CustomPanelAPI row, UIRect cell, String text, TooltipMakerAPI.TooltipCreator tooltip) {
		float width = Math.max(1f, cell.getWidth() - BattleNext_PageLayout.CELL_LABEL_SIDE_INSET * 2f);
		TooltipMakerAPI host = row.createUIElement(width, BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
		host.setTextWidthOverride(width);
		LabelAPI label = host.addPara(host.shortenString(text,
				Math.max(1f, cell.getWidth() - BattleNext_PageLayout.CELL_LABEL_TRUNCATION_PADDING)), 0f);
		label.setAlignment(Alignment.MID);
		label.getPosition().inTL(0f, Math.max(0f,
				(BattleNext_PageLayout.TABLE_ROW_HEIGHT - label.getPosition().getHeight()) * 0.5f));
		host.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.RIGHT);
		host.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		row.addUIElement(host).inTL(cell.getX() + BattleNext_PageLayout.CELL_LABEL_SIDE_INSET, 0f);
	}

	private String bracketLabel(TournamentBracketType type) {
		return tournamentStrings.get("bracket_" + type.name().toLowerCase(Locale.ROOT));
	}

	private TextFieldAPI field(TooltipMakerAPI host, String label, String value, float columnWidth,
			TooltipMakerAPI... viewports) {
		host.addPara(label, FIELD_LABEL_GAP);
		if (isReadOnly()) {
			host.addPara(value, READ_ONLY_FIELD_VALUE_GAP);
			return null;
		}
		float width = Math.min(EDITOR_FIELD_MAX_WIDTH, columnWidth - EDITOR_FIELD_SIDE_RESERVE);
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

	@Override
	public String serializeState() {
		if (idField == null || isReadOnly()) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, boField,
				maxFleetField, ruleField, mapField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		value.append(new JSONArray(draft.getFleetIds()));
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
		List<String> fleets = draft.getFleetIds();
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
