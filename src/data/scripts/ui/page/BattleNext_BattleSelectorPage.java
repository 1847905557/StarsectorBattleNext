package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.battle.BattleNext_BattleValidation.Failure;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_QuickBattleEditor;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import data.scripts.ui.widget.view.BattleNext_TournamentTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_BattleSelectorPage extends BattleNext_PageView {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private static final float SOURCE_HEADER_HEIGHT = 130f;
	private static final float SOURCE_CONTROL_TOP = 44f;
	private static final float SOURCE_CONTROL_HEIGHT = 30f;
	private static final float TOURNAMENT_MISSING_MESSAGE_GAP = 8f;

	public static class BattleSourceSelectionRequest {
		private final String id;

		public BattleSourceSelectionRequest(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}
	}

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private UIRect contentRect;
	private UIRect sourceRect;
	private UIRect instanceRect;
	private BattleNext_BattleSourceField sourceField;
	private BattleNext_BattleSourceField instanceField;
	private final Map<String, String> selectedInstances = new LinkedHashMap<>();
	private final Map<String, BattleNext_TournamentDisplay.ViewState> viewStates = new LinkedHashMap<>();
	private final Map<String, BattleNext_TournamentTemplate> templates = new LinkedHashMap<>();
	private String selectedInstanceId = "";
	private String displayedIdentity = "";
	private final BattleNext_StartedTournamentStore startedStore = new BattleNext_StartedTournamentStore();
	private BattleNext_QuickBattleEditor quickBattleEditor;
	private BattleNext_TournamentDisplay tournamentDisplay;
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_TournamentStore tournamentStore = provider.getTournamentStore();
	private final BattleNext_TournamentEngine tournamentEngine = new BattleNext_TournamentEngine();
	private final BattleNext_FleetStore fleetStore = provider.getFleetStore();
	private final BattleNext_RuleStore ruleStore = provider.getRuleStore();
	private String selectedId = "";
	private String loadError = "";
	private BattleNext_BattleValidation.Result checkResult;

	public BattleNext_BattleSelectorPage(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	protected void loadPageState() {
		quickBattleEditor = null;
		load();
	}

	@Override
	protected void buildPage() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] sections = root.splitVertically(SOURCE_HEADER_HEIGHT);
		contentRect = sections[1];
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT), panel,
				managementStrings.get("page_battle_selector"));

		sourceRect = new UIRect(0f, SOURCE_CONTROL_TOP, root.getWidth(), SOURCE_CONTROL_HEIGHT);
		instanceRect = new UIRect(0f, 82f, root.getWidth(), SOURCE_CONTROL_HEIGHT);
	}

	@Override
	protected void refreshPage() {
		instanceRect.removeLatestSubPanel();
		sourceRect.removeLatestSubPanel();
		CustomPanelAPI sourcePanel = sourceRect.createSubPanel(panel, inputOwner).getPanel();
		sourceField = new BattleNext_BattleSourceField(inputRouter, panel,
				id -> inputOwner.buttonPressed(new BattleSourceSelectionRequest(id)));
		sourceField.attach(sourcePanel);
		sourceField.configure(tournamentStore, selectedId);
		if (!selectedId.isEmpty()) {
			CustomPanelAPI instancePanel = instanceRect.createSubPanel(panel, inputOwner).getPanel();
			instanceField = new BattleNext_BattleSourceField(inputRouter, panel, id -> {
				saveErrors.clear();
				loadError = "";
				checkResult = null;
				selectedInstanceId = id;
				selectedInstances.put(selectedId, id);
				refreshContent();
			});
			instanceField.attach(instancePanel);
			instanceField.configureInstances(selectedId, tournamentStore.get(selectedId), startedStore,
					BattleNext_StartedTournament.ExecutionKind.MANUAL, selectedInstanceId);
		}
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, inputOwner).getPanel();
		if (selectedId.isEmpty()) {
			if (quickBattleEditor == null) {
				quickBattleEditor = new BattleNext_QuickBattleEditor(backAction, inputOwner);
			}
			attachEditor(contentPanel);
		} else {
			quickBattleEditor = null;
			buildTournamentDetails(contentPanel);
		}
	}

	@Override
	protected void clearPageOverlays() {
		if (tournamentDisplay != null) {
			tournamentDisplay.closeOverlays();
		}
		if (quickBattleEditor != null) {
			quickBattleEditor.closeOverlays();
		}
		if (instanceField != null) {
			instanceField.closeOverlays();
		}
		if (sourceField != null) {
			sourceField.closeOverlays();
		}
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return quickBattleEditor;
	}

	@Override
	protected void clearPageResources() {
		if (sourceField != null) {
			sourceField.close();
			sourceField = null;
		}
		if (instanceField != null) {
			instanceField.close();
			instanceField = null;
		}
		closeChart();
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if ("tournament:check".equals(buttonId) && !selectedId.isEmpty() && selectedInstanceId.isEmpty()) {
			saveErrors.clear();
			checkResult = provider.checkTournament(selectedId, template(tournamentStore.get(selectedId)).getFleetIds());
			loadError = "";
			updateTournamentDetails();
		} else if ("tournament:create".equals(buttonId) && !selectedId.isEmpty() && selectedInstanceId.isEmpty()) {
			createTournament();
		} else if ("tournament:active".equals(buttonId) && !selectedInstanceId.isEmpty()) {
			saveErrors.clear();
			loadError = "";
			boolean activated = false;
			try {
				startedStore.activate(selectedInstanceId);
				activated = true;
				refreshContent();
			} catch (Exception exception) {
				saveErrors.failure(strings.get(activated ? "load_failed" : "save_failed"), selectedInstanceId, exception);
				loadError = (activated ? strings.get("saved") + " / " : "") + saveErrors.summary();
				BattleNext_Logger.error(getClass(), "tournament.activation.failed",
						BattleNext_Logger.fields().put("instance_id", selectedInstanceId).build(), exception);
				if (!activated) refreshContent();
			}
		} else if (selectedId.isEmpty() && quickBattleEditor != null) {
			quickBattleEditor.buttonPressed(buttonId);
		}
	}

	@Override
	protected boolean isPageDirty() {
		return selectedId.isEmpty() && quickBattleEditor != null && quickBattleEditor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		return quickBattleEditor == null || quickBattleEditor.save();
	}

	@Override
	protected void discardPage() {
		if (quickBattleEditor != null) {
			quickBattleEditor.discard();
		}
	}

	@Override
	protected String pageStatus() {
		if (!saveErrors.isEmpty()) return loadError;
		if (checkResult != null) return String.join("; ", checkResult.messages());
		return quickBattleEditor == null ? loadError : quickBattleEditor.getStatusText();
	}

	@Override
	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (sourceField != null && sourceField.handleGlobalInput(events)) return true;
		if (instanceField != null && instanceField.handleGlobalInput(events)) return true;
		if (quickBattleEditor != null && quickBattleEditor.handleGlobalInput(events)) return true;

		if (tournamentDisplay != null) {
			tournamentDisplay.handleGlobalInput(events);
		}
		return false;
	}

	@Override
	public void advance(float amount) {
		if (quickBattleEditor != null) {
			quickBattleEditor.refreshRowStatuses();
		}
	}

	public void closeChart() {
		if (tournamentDisplay != null) {
			viewStates.put(displayedIdentity, tournamentDisplay.captureState());
			tournamentDisplay.close();
			tournamentDisplay = null;
		}
	}

	private void load() {
		List<String> errors = provider.reloadTournamentCatalog();
		loadError = errors.isEmpty() ? "" : strings.get("load_failed") + ": " + String.join(" | ", errors);
		try {
			startedStore.reload();
		} catch (Exception exception) {
			loadError = strings.get("load_failed");
			BattleNext_Logger.error(getClass(), "tournament.instances.load.failed", BattleNext_Logger.fields().build(), exception);
		}
		if (selectedId.isEmpty()) {
			BattleNext_StartedTournament active = startedStore.get(startedStore.getActiveId());
			if (active != null) {
				selectedId = active.getSourceTournamentId();
			}
		}
		selectInstance();
	}

	public void selectSource(String id) {
		saveErrors.clear();
		loadError = "";
		checkResult = null;
		selectedId = id;
		selectInstance();
	}

	private void selectInstance() {
		if (selectedInstances.containsKey(selectedId)) {
			selectedInstanceId = selectedInstances.get(selectedId);
			if (selectedInstanceId.isEmpty() || startedStore.get(selectedInstanceId) != null) return;
			selectedInstances.remove(selectedId);
		}
		List<BattleNext_StartedTournament> instances = startedStore.getForSource(selectedId, BattleNext_StartedTournament.ExecutionKind.MANUAL);
		BattleNext_StartedTournament active = startedStore.get(startedStore.getActiveId());
		selectedInstanceId = active != null && active.getSourceTournamentId().equals(selectedId) ? active.getId()
				: instances.isEmpty() ? "" : instances.get(0).getId();
		selectedInstances.put(selectedId, selectedInstanceId);
	}

	private void buildTournamentDetails(CustomPanelAPI detailPanel) {

		UIRect area = BattleNext_PageLayout.pageContentRect(new UIRect(0f, 0f,
				detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight()));
		detailPanel = area.createSubPanel(detailPanel, detailPanel.getPlugin()).getPanel();
		BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
		BattleNext_StartedTournament started = startedStore.get(selectedInstanceId);
		if (tournament == null && started == null) {
			UIRect messageRect = new UIRect(0f, 0f, detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight());
			TooltipMakerAPI message = messageRect.beginTooltip(detailPanel, false);
			BattleNext_UiTheme.configure(message);
			message.setTextWidthOverride(messageRect.getWidth());
			BattleNext_PageLayout.contentTitle(message, tournamentStrings.get("missing"), messageRect.getWidth(), 0f);
			message.addPara(tournamentStrings.get("missing_detail"), TOURNAMENT_MISSING_MESSAGE_GAP);
			messageRect.addTooltip();
			return;
		}
		List<String> fleetIds = started == null ? template(tournament).getFleetIds() : started.getFleetIds();
		List<String> reasons = new ArrayList<>(tournament == null ? List.of(tournamentStrings.get("missing_detail") + " " + selectedId)
				: started == null ? provider.getCatalogErrors() : provider.tournamentErrors(tournament, fleetIds));
		if (!startedStore.isLoaded()) {
			reasons.add(startedStore.getLoadError());
		}
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(tournament, started, fleetIds,
				fleetStore, ruleStore, loadError, reasons).withCheckResult(checkResult);
		List<BattleNext_TournamentDisplay.Action> actions = tournamentActions(tournament, started, reasons);
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, started, details, actions, inputOwner::buttonPressed);
		tournamentDisplay.setSaveErrors(this::getSaveErrors);
		if (started == null && selectedInstanceId.isEmpty()) {
			BattleNext_TournamentTemplate template = template(tournament);
			tournamentDisplay.enableTemplateEditing(overlayHost == null ? panel : overlayHost,
					new BattleNext_FleetSourceProvider(fleetStore), template::getFleetIds, (slot, id) -> {
						template.select(slot, id);
						checkResult = null;
						updateTournamentDetails();
					});
		}
		tournamentDisplay.attach(detailPanel);
		displayedIdentity = selectedId + "/" + selectedInstanceId;
		if (viewStates.containsKey(displayedIdentity)) {
			tournamentDisplay.restoreState(viewStates.get(displayedIdentity));
		}
	}

	private void createTournament() {
		saveErrors.clear();
		boolean saved = false;
		try {
			List<String> errors = provider.reloadTournamentCatalog();
			if (!errors.isEmpty()) throw new IllegalStateException(String.join("; ", errors));
			BattleNext_TournamentModel source = tournamentStore.get(selectedId);
			if (source == null) throw new IllegalArgumentException("Missing tournament configuration: " + selectedId);
			BattleNext_StartedTournament created = tournamentEngine.create(source, template(source).getFleetIds(), BattleNext_StartedTournament.ExecutionKind.MANUAL,
					startedStore, fleetStore, ruleStore);
			saved = true;
			selectedInstanceId = created.getId();
			selectedInstances.put(selectedId, selectedInstanceId);
			loadError = tournamentStrings.get("created");
			checkResult = null;
		} catch (BattleNext_BattleValidation.Rejection rejection) {
			checkResult = rejection.getResult();
			for (Failure failure : checkResult.failures()) {
				saveErrors.add(strings.format("operation_error", tournamentStrings.get("create"), failure.subject(), failure.message()));
			}
			loadError = saveErrors.summary();
			BattleNext_Logger.warn(getClass(), "tournament.creation.rejected", BattleNext_Logger.fields().put("tournament_id", selectedId).build(), rejection);
		} catch (Exception exception) {
			saveErrors.failure(strings.get("save_failed"), selectedId, exception);
			loadError = saveErrors.summary();
			BattleNext_Logger.error(getClass(), "tournament.creation.failed", BattleNext_Logger.fields().put("tournament_id", selectedId).build(), exception);
		}
		try {
			if (checkResult != null) {
				updateTournamentDetails();
			} else {
				refreshContent();
			}
		} catch (RuntimeException exception) {
			saveErrors.failure(strings.get("load_failed"), selectedId, exception);
			loadError = (saved ? strings.get("saved") + " / " : "") + saveErrors.summary();
			BattleNext_Logger.warn(getClass(), "tournament.display.after_create.failed",
					BattleNext_Logger.fields().put("tournament_id", selectedId).put("saved", saved).build(), exception);
		}
	}

	private void updateTournamentDetails() {
		BattleNext_TournamentModel configuration = tournamentStore.get(selectedId);
		if (configuration == null) {
			refreshContent();
			return;
		}
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(configuration, null,
				templates.get(selectedId).getFleetIds(), fleetStore, ruleStore, loadError, provider.getCatalogErrors()).withCheckResult(checkResult);
		tournamentDisplay.update(configuration, null, details, tournamentActions(configuration, null, provider.getCatalogErrors()));
	}

	private List<BattleNext_TournamentDisplay.Action> tournamentActions(BattleNext_TournamentModel configuration,
			BattleNext_StartedTournament started, List<String> reasons) {
		if (selectedInstanceId.isEmpty()) {
			return List.of(new BattleNext_TournamentDisplay.Action(tournamentStrings.get("create"), "tournament:create", reasons.isEmpty(), reasons, "create"),
					new BattleNext_TournamentDisplay.Action(tournamentStrings.get("check_fleets"), "tournament:check", configuration != null, List.of(), "create"));
		}
		return List.of(new BattleNext_TournamentDisplay.Action(tournamentStrings.get("active"), "tournament:active",
				started != null && startedStore.isLoaded() && reasons.isEmpty(), reasons));
	}

	private BattleNext_TournamentTemplate template(BattleNext_TournamentModel configuration) {
		BattleNext_TournamentTemplate template = templates.computeIfAbsent(configuration.getId(),
				id -> new BattleNext_TournamentTemplate(configuration.getMaxFleet()));
		template.resize(configuration.getMaxFleet());
		return template;
	}
}
