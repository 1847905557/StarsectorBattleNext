package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.simulator.BattleNext_TournamentSnapshotStore;
import data.simulator.BattleNext_SimulatorService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TournamentPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_TournamentStore tournamentStore = provider.getTournamentStore();
	private final BattleNext_FleetStore fleetStore = provider.getFleetStore();
	private final BattleNext_RuleStore ruleStore = provider.getRuleStore();
	private final BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournamentStore, fleetStore, ruleStore);
	private final BattleNext_TournamentSnapshotStore snapshotStore = new BattleNext_TournamentSnapshotStore();
	private BattleNext_TournamentDisplay tournamentDisplay;

	private String selectedId = "";
	private String selectedSnapshotId = "";
	private boolean draftOpen;

	public BattleNext_TournamentPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	protected void bindPageInput() {
		editor.setInputRouter(inputRouter);
	}

	@Override
	protected void loadPageState() {
		selectedId = "";
		selectedSnapshotId = "";
		draftOpen = false;
		status = String.join(" | ", provider.reloadTournamentCatalog());
		try {
			snapshotStore.reload();
		} catch (Exception exception) {
			status += (status.isEmpty() ? "" : " | ") + exception.getMessage();
		}
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_tournaments");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"add", "save", "back"};
	}

	@Override
	protected String promptText() {
		return tournamentStrings.get("select_prompt");
	}

	@Override
	protected boolean isListLoaded() {
		return tournamentStore.isLoaded();
	}

	@Override
	protected String listError() {
		return tournamentStore.getLoadError();
	}

	@Override
	protected boolean isListEmpty() {
		return tournamentStore.getAll().isEmpty() && snapshotStore.getAll().isEmpty();
	}

	@Override
	protected String emptyListText() {
		return tournamentStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			addListItem(listContent, tournament.getName(), "tournament:select:" + tournament.getId());
		}
		for (var snapshot : snapshotStore.getAll()) {
			addListItem(listContent, snapshot.getTournament().getName(), "simulation:select:" + snapshot.getId());
		}
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {

		if (!selectedSnapshotId.isEmpty()) {
			var snapshot = snapshotStore.get(selectedSnapshotId);
			var model = snapshot.getTournament();
			var details = new BattleNext_TournamentDisplay.Details(snapshot.getFleetNames(), snapshot.getRequest().source().has("rule"),
					model.getNextPendingMatch(), snapshot.getDiagnostic(), List.of());
			tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, model, details, List.of(), this::buttonPressed);
			tournamentDisplay.attach(contentPanel);
			return;
		}
		BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
		if (tournament == null || !tournament.isStarted()) {
			attachEditor(contentPanel);
			return;
		}
		BattleNext_Match next = tournament.getNextPendingMatch();
		List<String> warnings = new ArrayList<>();
		if (selectedId.equals(tournamentStore.getActiveId()) && next != null) {
			provider.load();
			warnings.addAll(provider.getWarnings());
			warnings.addAll(provider.getRuleWarnings());
		}
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(tournament, fleetStore, ruleStore, status, warnings);
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, details, List.of(), this::buttonPressed);
		tournamentDisplay.attach(contentPanel);
	}

	@Override
	protected boolean isDetailOpen() {
		return draftOpen;
	}

	@Override
	protected boolean hasSelection() {
		return !selectedId.isEmpty() || !selectedSnapshotId.isEmpty();
	}

	@Override
	protected boolean isDetailEditable() {
		return selectedSnapshotId.isEmpty() && !editor.isReadOnly();
	}

	@Override
	protected boolean isSaveDataLoaded() {
		return tournamentStore.isLoaded() && fleetStore.isLoaded() && ruleStore.isLoaded();
	}

	@Override
	protected boolean canDelete() {
		return !selectedSnapshotId.isEmpty() ? snapshotStore.isLoaded() : tournamentStore.isLoaded() && tournamentStore.isManaged(selectedId);
	}

	@Override
	protected String sourceNote() {
		return !selectedId.isEmpty() && !tournamentStore.isManaged(selectedId) ? strings.get("source_unavailable_short") : "";
	}

	@Override
	protected void clearPageOverlays() {
		editor.closeOverlays();
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return editor;
	}

	@Override
	protected void clearPageResources() {
		if (tournamentDisplay != null) {
			tournamentDisplay.close();
			tournamentDisplay = null;
		}
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if (buttonId instanceof String action && action.startsWith("simulation:select:")) {
			navigation.accept(() -> {
				selectedSnapshotId = action.substring("simulation:select:".length());
				selectedId = "";
				draftOpen = true;
				refreshContent();
			});
		} else if ("delete".equals(buttonId) && !selectedSnapshotId.isEmpty()) {
			String id = selectedSnapshotId;
			deletion.accept(snapshotStore.get(id).getTournament().getName(), () -> {
				try {
					BattleNext_SimulatorService.deleteTournament(id);
					snapshotStore.reload();
					selectedSnapshotId = "";
					draftOpen = false;
					refreshContent();
				} catch (Exception exception) {
					setStatus(strings.get("save_failed") + ": " + exception.getMessage());
				}
			});
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("tournament:select:")) {
			String id = ((String) buttonId).substring("tournament:select:".length());
			navigation.accept(() -> {
				if (!editor.open(tournamentStore.get(id))) {
					setStatus(editor.getStatusText());
					return;
				}
				selectedId = id;
				selectedSnapshotId = "";
				draftOpen = true;
				refreshContent();
			});
		} else if ("add".equals(buttonId)) {
			navigation.accept(() -> {
				if (!editor.open(null)) {
					setStatus(editor.getStatusText());
					return;
				}
				selectedId = "";
				selectedSnapshotId = "";
				draftOpen = true;
				refreshContent();
			});
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty()
				&& tournamentStore.isLoaded() && tournamentStore.isManaged(selectedId)) {
			BattleNext_TournamentModel selected = tournamentStore.get(selectedId);
			deletion.accept(selected.getName(), () -> {
				try {
					tournamentStore.delete(selectedId);
					selectedId = "";
					draftOpen = false;
					setStatus(strings.get("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(strings.get("save_failed") + ": " + exception.getMessage());
				}
			});
		} else if ("back".equals(buttonId)) {
			if (draftOpen) navigation.accept(() -> { selectedId = ""; selectedSnapshotId = ""; draftOpen = false; refreshContent(); });
			else backAction.run();
		} else if (draftOpen) {
			editor.buttonPressed(buttonId);
		}
	}

	@Override
	protected boolean isPageDirty() {
		return selectedSnapshotId.isEmpty() && draftOpen && editor.hasUnsavedChanges();
	}

	@Override
	protected void afterEditorCommit() {
		selectedId = editor.getDraftId();
	}

	@Override
	protected void reloadSavedData() throws Exception {
		tournamentStore.reload();
	}

	@Override
	protected void discardPage() {
		if (selectedId.isEmpty()) {
			draftOpen = false;
			refreshContent();
		} else {
			editor.deserialize();
		}
	}

	@Override
	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (tournamentDisplay != null) {
			tournamentDisplay.handleGlobalInput(events);
			return false;
		}
		return draftOpen && editor.handleGlobalInput(events);
	}

	@Override
	public void advance(float amount) {
		if (draftOpen && selectedSnapshotId.isEmpty()) {
			editor.refreshDraftValidation();
		}
	}
}
