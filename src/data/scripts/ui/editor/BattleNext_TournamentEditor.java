package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.report.BattleNext_BattleReportStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.CheckBoxGroup;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_StartedTournamentStore.CatalogEntry;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.provider.BattleNext_RuleSourceProvider;
import data.simulator.BattleNext_SimulationTask;
import data.simulator.BattleNext_SimulatorService;

import java.util.*;
import java.util.function.Consumer;

public class BattleNext_TournamentEditor extends BattleNext_EditorView {

	private final BattleNext_TournamentStore store;
	private final BattleNext_RuleStore ruleStore;
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private TooltipMakerAPI metadataContent;
	private BattleNext_TournamentModel baseline;
	private boolean locked;
	private BattleNext_TournamentModel draft;
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI bestOfField;
	private TextFieldAPI maxFleetsField;
	private TextFieldAPI ruleField;
	private TextFieldAPI mapField;
	private final BattleNext_RuleSourceProvider ruleSource;
	private CheckBoxGroup bracketGroup;
	private final BattleNext_StartedTournamentStore startedStore = new BattleNext_StartedTournamentStore();
	private final BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
	private final Set<String> expandedInstances = new HashSet<>();
	private Consumer<String> deleteInstance;
	private Consumer<CatalogEntry> deleteInvalidInstance;
	private Consumer<String> retryRound;
	private final Map<String, Long> instanceVersions = new LinkedHashMap<>();

	public BattleNext_TournamentEditor(BattleNext_TournamentStore store, BattleNext_RuleStore ruleStore) {
		this.store = store;
		this.ruleStore = ruleStore;
		ruleSource = new BattleNext_RuleSourceProvider(ruleStore);
	}

	public boolean open(BattleNext_TournamentModel source) {
		try {
			beginSession();
			expandedInstances.clear();
			BattleNext_TournamentModel next = new BattleNext_TournamentModel();
			if (source != null) {
				next.fromJson(source.toJson());
			}
			baseline = source;
			statusText = "";
			locked = store.isLocked(source == null ? "" : source.getId());
			draft = next;
			idField = null;
			return true;
		} catch (Exception exception) {
			statusText = strings.get("load_failed") + ": " + exception.getMessage();
			return false;
		}
	}

	@Override
	protected void buildEditorContent() {
		createColumns("metadata", "instances", (metadataContent, columnWidth) -> {
			this.metadataContent = metadataContent;
			addDetails(metadataContent, columnWidth);
		}, this::addInstances);
	}

	public void setInstanceDeletion(Consumer<String> deleteInstance) {
		this.deleteInstance = deleteInstance;
	}

	public void setInvalidInstanceDeletion(Consumer<CatalogEntry> deletion) {
		deleteInvalidInstance = deletion;
	}

	public void setRoundRetry(Consumer<String> retryRound) {
		this.retryRound = retryRound;
	}

	public void refreshInstances() {
		try {
			boolean wasLocked = locked;
			locked = store.isLocked(baseline == null ? "" : baseline.getId());
			if (wasLocked != locked) {
				deserialize();
			} else {
				refreshStructure();
			}
		} catch (Exception exception) {
			statusText = exception.toString();
		}
	}

	public void advanceInstances() {
		for (var entry : instanceVersions.entrySet()) {
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(entry.getKey());
			long version = task == null ? -1L : task.getVersion();
			if (version == entry.getValue()) continue;
			refreshInstances();
			return;
		}
	}

	private void addInstances(TooltipMakerAPI rowsContent, float columnWidth) {
		instanceVersions.clear();
		rowsContent.addTitle(battleStrings.get("tournament_instance"));
		try {
			startedStore.reload();
		} catch (Exception exception) {
			rowsContent.addPara(exception.toString(), 5f);
			return;
		}
		try {
			reports.reload();
		} catch (Exception exception) {
			rowsContent.addPara(exception.toString(), 5f);
		}
		if (startedStore.getCatalog().isEmpty()) {
			for (String error : startedStore.getLoadErrors()) {
				rowsContent.addPara(error, 5f);
			}
		}
		List<BattleNext_StartedTournament> instances = new ArrayList<>(startedStore.getAll().stream()
				.filter(instance -> baseline != null && instance.getSourceTournamentId().equals(baseline.getId()))
				.toList());
		if (baseline != null) {
			for (BattleNext_SimulationTask task : BattleNext_SimulatorService.pendingDeletions(baseline.getId())) {
				if (instances.stream().anyMatch(instance -> instance.getId().equals(task.getRequest().id()))) continue;
				instances.add(task.getStartedTournament());
			}
		}
		instances.sort(Comparator.comparingLong(BattleNext_StartedTournament::getStartedAt).reversed());
		for (CatalogEntry entry : startedStore.getCatalog()) {
			if (baseline == null || !entry.sourceTournamentId().equals(baseline.getId()) || entry.error().isEmpty()) continue;
			String key = "error:" + entry.index();
			String identity = entry.id().isEmpty() ? BattleNext_StartedTournamentStore.PATH + " / " + entry.index() : entry.id();
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(null, false, "", identity, "",
					strings.get("load_failed"), "", null);
			BattleNext_ExpandablePanel.add(rowsContent, panel, columnWidth, expandedInstances.contains(key), 0f, header,
					() -> buttonPressed("instance:toggle:" + key), this::buttonPressed, body -> {
				float areaWidth = columnWidth - 16f;
				TooltipMakerAPI details = body.createUIElement(areaWidth, 0f, false);
				details.setTextWidthOverride(areaWidth);
				details.addPara(BattleNext_StartedTournamentStore.PATH + " / " + entry.index(), 0f);
				details.addPara(entry.error(), 5f);
				details.addPara(entry.raw(), 5f);
				createButton(details, areaWidth, tournamentStrings.get("delete_instance"), "instance:delete_error:" + entry.index());
				body.getPosition().setSize(columnWidth, details.getHeightSoFar() + 16f);
				body.addUIElement(details).inTL(8f, 8f);
			});
		}
		for (BattleNext_StartedTournament instance : instances) {
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(instance.getId());
			instanceVersions.put(instance.getId(), task == null ? -1L : task.getVersion());
			String phase = task == null ? tournamentStrings.get(instance.isCompleted() ? "status_completed" : "status_in_progress")
					: battleStrings.get("simulator_" + task.getState().name().toLowerCase(Locale.ROOT));
			if (task != null && task.isDeletionRequested()) {
				phase = tournamentStrings.get("instance_deleting");
			}
			String kind = tournamentStrings.get(instance.getExecutionKind() == BattleNext_StartedTournament.ExecutionKind.MANUAL ? "instance_manual" : "instance_simulation");
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(null, false, "",
					instance.getName(draft), kind, phase,
					tournamentStrings.format("chart_finished_matches", BattleNext_TournamentDisplay.completedMatches(instance), instance.getPlayableMatchTotal(draft)), null);
			BattleNext_ExpandablePanel.add(rowsContent, panel, columnWidth, expandedInstances.contains(instance.getId()), 0f, header,
					() -> buttonPressed("instance:toggle:" + instance.getId()), this::buttonPressed, body -> {
				float areaWidth = columnWidth - 16f;
				TooltipMakerAPI details = body.createUIElement(areaWidth, 0f, false);
				details.addPara(instance.getId(), 0f);
				details.addPara(String.join(", ", instance.getFleetIds().stream().filter(id -> !id.isEmpty()).toList()), 5f);
				var next = instance.getNextPendingMatch();
				if (next != null) {
					details.addPara(battleStrings.get("next_match") + ": " + next.getId() + " / " + next.getPlayer() + " vs " + next.getEnemy(), 5f);
				}
				if (instance.isCompleted()) {
					details.addPara(strings.get("winner") + ": " + instance.getChampionFleetId(), 5f);
				}
				List<String> reportIds = instance.getMatches().stream().flatMap(match -> match.getRounds().stream()).map(round -> round.getReportId()).filter(id -> !id.isEmpty()).toList();
				details.addPara(reports.isLoaded() ? tournamentStrings.format("instance_reports", reportIds.stream().filter(id -> reports.get(id) != null).count(), reportIds.size())
						: reports.getLoadError(), 5f);
				if (task != null && !task.getImportError().isEmpty()) {
					details.addPara(task.getImportError(), 5f);
				}
				if (task != null) {
					var metrics = task.getMetrics();
					details.addPara(tournamentStrings.format("instance_metrics", metrics.simulationSeconds(), metrics.executionNanos() / 1000000000d,
							metrics.steps(), metrics.peakHeapBytes() / 1048576d), 5f);
				}
				try {
					if (BattleNext_ModFileManager.exists(BattleNext_StartedTournamentStore.pendingRoundPath(instance.getId()))) {
						createButton(details, areaWidth, tournamentStrings.get("retry_round_report"), "instance:retry_round:" + instance.getId());
					}
				} catch (Exception exception) {
					details.addPara(exception.toString(), 5f);
				}
				createButton(details, areaWidth, tournamentStrings.get(task != null && task.isDeletionRequested() ? "retry_delete_instance" : "delete_instance"), "instance:delete:" + instance.getId());
				body.getPosition().setSize(columnWidth, details.getHeightSoFar() + 16f);
				body.addUIElement(details).inTL(8f, 8f);
			});
		}
	}

	@Override
	protected void afterRefresh() {
		bracketGroup.updateCheck();
		restoreState();
	}

	public boolean isReadOnly() {
		return locked;
	}

	private void addDetails(TooltipMakerAPI metadataContent, float columnWidth) {
		metadataContent.addTitle(isReadOnly() ? tournamentStrings.get("locked_message") : tournamentStrings.get("editor_title"));
		UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(columnWidth);
		idField = createTextField("id", panel, metadataContent, fieldArea, tournamentStrings.get("id"), draft.getId(), !isReadOnly(), metadataContent);
		nameField = createTextField("name", panel, metadataContent, fieldArea, tournamentStrings.get("name"), draft.getName(), !isReadOnly(), metadataContent);
		metadataContent.addPara(tournamentStrings.get("bracket"), BattleNext_PageLayout.EDITOR_LABEL_GAP);
		bracketGroup = new CheckBoxGroup();
		if (isReadOnly()) {
			metadataContent.addPara(bracketLabel(draft.getBracketType()), BattleNext_PageLayout.EDITOR_READ_ONLY_VALUE_GAP);
		} else {
			for (TournamentBracketType type : TournamentBracketType.values()) {
				ButtonAPI bracketCheckbox = createCheckbox(type, metadataContent, fieldArea,
						bracketLabel(type), "bracket:" + type.name(), type == draft.getBracketType(), true);
				bracketGroup.addCheckBox(bracketCheckbox, type);
			}
			bracketGroup.tryCheckValue(draft.getBracketType());
			bracketGroup.updateCheck();
		}
		bestOfField = createTextField("best_of", panel, metadataContent, fieldArea, tournamentStrings.get("best_of_count"), Integer.toString(draft.getBoCount()), !isReadOnly(), metadataContent);
		maxFleetsField = createTextField("max_fleets", panel, metadataContent, fieldArea, tournamentStrings.get("max_fleets"), Integer.toString(draft.getMaxFleet()), !isReadOnly(), metadataContent);
		if (isReadOnly()) {
			ruleField = createTextField("rule", panel, metadataContent, fieldArea, tournamentStrings.get("rules"), draft.getRuleId(), false, metadataContent);
		} else {
			ruleField = createOptionField("rule", panel, metadataContent, fieldArea, tournamentStrings.get("rules"), draft.getRuleId(),
					ruleSource, BattleNext_OptionField.OptionSelectionMode.SINGLE, metadataContent);
		}
		mapField = createTextField("map", panel, metadataContent, fieldArea, battleStrings.get("map_number_label"), Integer.toString(draft.getMapId()), !isReadOnly(), metadataContent);
	}


	private String bracketLabel(TournamentBracketType type) {
		return tournamentStrings.get("bracket_" + type.name().toLowerCase(Locale.ROOT));
	}


	@Override
	public void buttonPressed(Object buttonId) {
		if (!(buttonId instanceof String action)) return;
		if (!inputRouter.isEmpty()) return;
		if (action.startsWith("instance:toggle:")) {
			String id = action.substring("instance:toggle:".length());
			if (!expandedInstances.add(id)) {
				expandedInstances.remove(id);
			}
			refreshStructure();
			return;
		}
		if (action.startsWith("instance:delete:")) {
			deleteInstance.accept(action.substring("instance:delete:".length()));
			return;
		}
		if (action.startsWith("instance:delete_error:")) {
			int index = Integer.parseInt(action.substring("instance:delete_error:".length()));
			deleteInvalidInstance.accept(startedStore.getCatalog().get(index));
			return;
		}
		if (action.startsWith("instance:retry_round:")) {
			retryRound.accept(action.substring("instance:retry_round:".length()));
			return;
		}
		if (isReadOnly()) return;
		if (action.startsWith("bracket:")) {
			bracketGroup.updateCheck();
		}
	}

	@Override
	protected String draftState() {
		if (idField == null || isReadOnly()) return "";
		StringBuilder value = new StringBuilder();
		for (TextFieldAPI field : new TextFieldAPI[]{idField, nameField, bestOfField,
				maxFleetsField, ruleField, mapField}) {
			value.append(field.getText().length()).append(':').append(field.getText());
		}
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
		if (!BattleNext_EntryId.isValidPathSegment(getDraftId()) || BattleNext_StartedTournamentStore.FILE_NAME_WITHOUT_FORMAT.equalsIgnoreCase(getDraftId())) {
			statusText = tournamentStrings.get("invalid_id");
			return false;
		}
		if (!store.isLoaded() || !ruleStore.isLoaded()) {
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
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel(getDraftId(), nameField.getText(),
				(TournamentBracketType) bracketGroup.getCheckedValue(), boCount, maxFleet);
		configuration.setMapId(map);
		try {
			new BattleNext_TournamentEngine().validateConfiguration(configuration);
		} catch (IllegalArgumentException exception) {
			statusText = tournamentStrings.get("invalid_configuration");
			BattleNext_Logger.warn(BattleNext_TournamentEditor.class, "tournament.configuration.rejected",
					BattleNext_Logger.fields().put("configuration", configuration).put("input_state", serializeState()).build(), exception);
			return false;
		}
		captureTextFields();
		draft.setBoCount(boCount);
		draft.setMaxFleet(maxFleet);
		draft.setMapId(map);
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
		if (idField != null && !isReadOnly()) {
			captureTextFields();
		}
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
			if (baseline != null) {
				draft.fromJson(baseline.toJson());
			}
			idField = null;
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
