package data.scripts.ui.editor;

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
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import org.json.JSONArray;

import java.util.*;

public class BattleNext_TournamentEditor extends BattleNext_EditorView {

	private static final float[] COLUMN_SHARES = {0.3f, 0.34f, 0.16f, 0.2f};
	private static final int FLEET_COLUMN = 0;
	private static final int AUTHOR_COLUMN = 1;
	private static final int VALID_COLUMN = 2;
	private static final int ACTIONS_COLUMN = 3;

	private static final float VALIDATION_REASON_GAP = 5f;

	private final BattleNext_TournamentStore store;
	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_RuleStore ruleStore;
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private TooltipMakerAPI metadataContent;
	private TooltipMakerAPI rowsContent;
	private BattleNext_TournamentModel baseline;
	private BattleNext_TournamentModel draft;
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI bestOfField;
	private TextFieldAPI maxFleetsField;
	private TextFieldAPI ruleField;
	private TextFieldAPI mapField;
	private BattleNext_OptionPicker fleetPicker;
	private BattleNext_TableWidget tableWidget;
	private final BattleNext_FleetSourceProvider fleetSource;
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
			beginSession();
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

	@Override
	protected void buildEditorContent() {
		validationSnapshot = "";
		createColumns("metadata", "fleets", (metadataContent, columnWidth) -> {
			this.metadataContent = metadataContent;
			addDetails(metadataContent, columnWidth);
		}, (rowsContent, columnWidth) -> {
			this.rowsContent = rowsContent;
			addFleets(rowsContent, columnWidth);
		});
	}

	@Override
	protected void afterRefresh() {
		bracketGroup.updateCheck();
		refreshDraftValidation();
		bindViewport("rows", tableWidget.getContent());
		restoreState();
	}

	public boolean isReadOnly() {
		return draft != null && draft.isStarted();
	}

	private void addDetails(TooltipMakerAPI metadataContent, float columnWidth) {
		metadataContent.addTitle(isReadOnly() ? tournamentStrings.get("locked_message") : tournamentStrings.get("editor_title"));
		idField = createTextField("id", panel, metadataContent, columnWidth, tournamentStrings.get("id"), draft.getId(), !isReadOnly(), metadataContent);
		nameField = createTextField("name", panel, metadataContent, columnWidth, tournamentStrings.get("name"), draft.getName(), !isReadOnly(), metadataContent);
		metadataContent.addPara(tournamentStrings.get("bracket"), BattleNext_PageLayout.EDITOR_LABEL_GAP);
		bracketGroup = new CheckBoxGroup();
		if (isReadOnly()) {
			metadataContent.addPara(bracketLabel(draft.getBracketType()), BattleNext_PageLayout.EDITOR_READ_ONLY_VALUE_GAP);
		} else {
			for (TournamentBracketType type : TournamentBracketType.values()) {
				ButtonAPI bracketCheckbox = createCheckbox(type, metadataContent, columnWidth,
						bracketLabel(type), "bracket:" + type.name(), type == draft.getBracketType(), true);
				bracketGroup.addCheckBox(bracketCheckbox, type);
			}
			bracketGroup.tryCheckValue(draft.getBracketType());
			bracketGroup.updateCheck();
		}
		bestOfField = createTextField("best_of", panel, metadataContent, columnWidth, tournamentStrings.get("best_of_count"), Integer.toString(draft.getBoCount()), !isReadOnly(), metadataContent);
		maxFleetsField = createTextField("max_fleets", panel, metadataContent, columnWidth, tournamentStrings.get("max_fleets"), Integer.toString(draft.getMaxFleet()), !isReadOnly(), metadataContent);
		ruleField = createTextField("rule", panel, metadataContent, columnWidth, tournamentStrings.get("rules"), draft.getRuleId(), !isReadOnly(), metadataContent);
		mapField = createTextField("map", panel, metadataContent, columnWidth, battleStrings.get("map_number_label"), Integer.toString(draft.getMapId()), !isReadOnly(), metadataContent);
	}

	private void addFleets(TooltipMakerAPI rowsContent, float columnWidth) {
		String[] headers = {tournamentStrings.get("fleet"), fleetStrings.get("author"),
				tournamentStrings.get("valid"), strings.get("actions")};
		tableWidget = createTable("rows", rowsContent, columnWidth, tournamentStrings.get("participant_fleets"), headers, COLUMN_SHARES,
				0, this::addFleetRow, (rowWidth, content) -> {
			if (isReadOnly()) return;
			fleetPicker = createPicker(panel, content, rowWidth, tournamentStrings.get("add_fleet"),
					fleetSource, BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
					List::of, this::addFleets, content, rowsContent);
		});
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

	private void addFleetRow(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI rowsContent) {

		String id = draft.getFleetIds().get(index);
		BattleNext_FleetModel fleet = fleetStore.get(id);
		String name = fleet == null ? id : fleet.getName();
		String author = fleet == null || fleet.getAuthor().isEmpty() ? "—" : fleet.getAuthor();
		List<String> reasons = validationReasons.get(id);
		CustomPanelAPI rowPanel = panel.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, panel.getPlugin());
		new UIRect(0f, 0f, rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBorder(rowPanel,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		createTextCell(rowPanel, cells[FLEET_COLUMN], name, false, new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return fleetSource.getTooltipWidth();
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				fleetSource.createTooltip(tooltip, new BattleNext_OptionPicker.OptionChoice(id, name));
			}
		}, TooltipMakerAPI.TooltipLocation.RIGHT, false);
		createTextCell(rowPanel, cells[AUTHOR_COLUMN], author, false, new BaseTooltipCreator() {
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addPara(author, 0f);
			}
		}, TooltipMakerAPI.TooltipLocation.RIGHT, false);
		createCellCheckbox(id, rowPanel, cells[VALID_COLUMN], "fleet:valid:" + index,
				reasons.isEmpty(), false, new BaseTooltipCreator() {
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
		});
		createActions(rowPanel, cells[ACTIONS_COLUMN],
				new EditorAction("^", "fleet:up:" + index, !isReadOnly() && index > 0),
				new EditorAction("V", "fleet:down:" + index, !isReadOnly() && index + 1 < draft.getFleetIds().size()),
				new EditorAction("X", "fleet:remove:" + index, !isReadOnly()));
		rowsContent.addCustom(rowPanel, 0f);
	}


	private String bracketLabel(TournamentBracketType type) {
		return tournamentStrings.get("bracket_" + type.name().toLowerCase(Locale.ROOT));
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
		if (fleetPicker != null) release(fleetPicker);
		tableWidget.setRowCount(draft.getFleetIds().size());
		tableWidget.refresh();
		bindViewport("rows", tableWidget.getContent());
	}

	@Override
	protected String draftState() {
		if (idField == null || isReadOnly()) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, bestOfField,
				maxFleetsField, ruleField, mapField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
		value.append(new JSONArray(draft.getFleetIds()));
		value.append(bracketGroup.getCheckedValue());
		return value.toString();
	}

	public String getDraftId() {
		return idField == null ? "" : idField.getText().trim();
	}

	@Override
	protected boolean submitDraft() {
		if (isReadOnly()) return true;
		if (!validateName(nameField.getText(), strings.get("name_required"))) return false;
		if (!BattleNext_EntryId.isValidPathSegment(getDraftId())) {
			statusText = tournamentStrings.get("invalid_id");
			return false;
		}
		if (!store.isLoaded() || !fleetStore.isLoaded() || !ruleStore.isLoaded()) {
			statusText = strings.get("load_failed");
			return false;
		}
		int boCount;
		int maxFleet;
		int map;
		try {
			boCount = Integer.parseInt(bestOfField.getText().trim());
			maxFleet = Integer.parseInt(maxFleetsField.getText().trim());
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
				|| map < BattleNext_MissionBattleMap.RANDOM_MAP_ID || map > BattleNext_MissionBattleMap.LAST_MAP_ID) {
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
			store.saveEdited(draft, baseline == null ? "" : baseline.getId());
			baseline = nextBaseline;
			statusText = strings.get("saved");
			return true;
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_TournamentEditor.class, "tournament.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).build(), exception);
			statusText = failureStatus(exception);
			return false;
		}
	}

	@Override
	protected void captureDraftInputs() {
		if (idField != null && !isReadOnly()) captureTextFields();
	}

	private void captureTextFields() {
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		draft.setBracketType((TournamentBracketType) bracketGroup.getCheckedValue());
		draft.setRuleId(ruleField.getText());
	}

	@Override
	protected void restoreDraft() {
		try {
			draft = new BattleNext_TournamentModel();
			if (baseline != null) draft.fromJson(baseline.toJson());
			idField = null;
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
