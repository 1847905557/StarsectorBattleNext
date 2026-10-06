package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.simulator.BattleNext_SimulatorService;

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
	private final BattleNext_RuleStore ruleStore = provider.getRuleStore();
	private final BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournamentStore, ruleStore);

	private String selectedId = "";
	private boolean draftOpen;

	public BattleNext_TournamentPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
		editor.setInstanceDeletion(this::deleteInstance);
		editor.setInvalidInstanceDeletion(entry -> deletion.accept(entry.id(), () -> {
			try {
				BattleNext_SimulatorService.deleteInvalidTournament(entry);
				editor.refreshInstances();
				refreshContent();
			} catch (Exception exception) {
				setStatus(exception.toString());
			}
		}));
		editor.setRoundRetry(id -> {
			try {
				BattleNext_BattleResultRecorder.retryTournamentRound(id);
				editor.refreshInstances();
			} catch (Exception exception) {
				setStatus(exception.toString());
			}
		});
	}

	@Override
	protected void bindPageInput() {
		editor.setInputRouter(inputRouter);
	}

	@Override
	protected void loadPageState() {
		selectedId = "";
		draftOpen = false;
		status = String.join(" | ", provider.reloadTournamentDefinitions());

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
		return tournamentStore.getAll().isEmpty();
	}

	@Override
	protected String emptyListText() {
		return tournamentStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		addCatalogDiagnostics(listContent, tournamentStore.getErrors());
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			addListItem(listContent, tournament.getName(), "tournament:select:" + tournament.getId());
		}

	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {
		attachEditor(contentPanel);
	}

	@Override
	protected boolean isDetailOpen() {
		return draftOpen;
	}

	@Override
	protected boolean hasSelection() {
		return !selectedId.isEmpty();
	}

	@Override
	protected boolean isDetailEditable() {
		return !editor.isReadOnly();
	}

	@Override
	protected boolean isSaveDataLoaded() {
		return tournamentStore.isLoaded() && ruleStore.isLoaded();
	}

	@Override
	protected boolean canDelete() {
		return !editor.isReadOnly() && tournamentStore.isLoaded() && tournamentStore.isManaged(selectedId);
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

	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("tournament:select:")) {
			String id = ((String) buttonId).substring("tournament:select:".length());
			navigation.accept(() -> {
				if (!editor.open(tournamentStore.get(id))) {
					setStatus(editor.getStatusText());
					return;
				}
				selectedId = id;
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
			if (draftOpen) {
				navigation.accept(() -> { selectedId = ""; draftOpen = false; refreshContent(); });
			}
			else backAction.run();
		} else if (draftOpen) {
			editor.buttonPressed(buttonId);
		}
	}

	@Override
	protected boolean isPageDirty() {
		return draftOpen && editor.hasUnsavedChanges();
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
		return draftOpen && editor.handleGlobalInput(events);
	}

	@Override
	public void advance(float amount) {
		if (!draftOpen) return;
		editor.advanceInstances();
		updatePageControls();
	}

	private void deleteInstance(String id) {
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		try {
			instances.reload();
			BattleNext_StartedTournament instance = instances.get(id);
			if (instance == null) {
				instance = BattleNext_SimulatorService.tournament(id).getStartedTournament();
			}
			deletion.accept(instance.getName(tournamentStore.get(selectedId)), () -> {
				try {
					BattleNext_SimulatorService.deleteTournament(id);
					instances.reload();
					if (!instances.hasInstances(selectedId)) {
						editor.open(tournamentStore.get(selectedId));
					}
					editor.refreshInstances();
					refreshContent();
				} catch (Exception exception) {
					setStatus(exception.toString());
				}
			});
		} catch (Exception exception) {
			setStatus(exception.toString());
		}
	}

}
