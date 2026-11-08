package data.scripts.ui.page;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.ui.BattleNext_ManagementSession.Catalogs;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.simulator.BattleNext_SimulatorService;
import data.simulator.BattleNext_SimulationTask;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TournamentPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private final BattleNext_BattleSourceProvider provider;
	private final BattleNext_TournamentStore tournamentStore;
	private final BattleNext_RuleStore ruleStore;
	private final BattleNext_StartedTournamentStore instances;
	private final BattleNext_TournamentEditor editor;

	public BattleNext_TournamentPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this(backAction, navigation, deletion, Catalogs.create());
	}

	public BattleNext_TournamentPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, Catalogs data) {
		super(backAction, navigation, deletion, "tournament:select:");
		provider = data.source();
		tournamentStore = data.tournaments();
		ruleStore = data.rules();
		instances = data.instances();
		editor = new BattleNext_TournamentEditor(tournamentStore, ruleStore, data.instances(), data.reports());
		editor.setInstanceDeletion(this::deleteInstance);
		editor.setInvalidInstanceDeletion(entry -> deletion.accept(entry.id(), () -> persistMutation(() -> {
			instances.delete(entry);
			return true;
		}, () -> {
			try {
				BattleNext_SimulatorService.discardInvalidTask(entry, instances);
			} catch (Exception exception) {
				setStatus(exception.toString());
			}
			refreshInstances();
		})));
		editor.setRoundRetry(id -> persistMutation(() -> {
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(id);
			if (task == null) BattleNext_BattleResultRecorder.retryTournamentRound(id);
			else task.retryImport();
			instances.reload();
			return true;
		}, this::refreshInstances));
	}

	@Override
	protected void loadPageState() {
		super.loadPageState();
		status = String.join(" | ", provider.reloadTournamentDefinitions());
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_tournaments");
	}

	@Override
	protected String promptText() {
		return tournamentStrings.get("select_prompt");
	}

	@Override
	protected String emptyListText() {
		return tournamentStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		addCatalogDiagnostics(listContent, tournamentStore.getErrors());
		for (BattleNext_TournamentModel tournament : tournamentStore.getAllCopy()) {
			addListItem(listContent, tournament.getName(), "tournament:select:" + tournament.getId());
		}
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
	protected BattleNext_TournamentEditor editorView() {
		return editor;
	}

	@Override
	protected void afterEditorCommit() {
		selectedId = editor.getDraftId();
	}

	@Override
	public void advance(float amount) {
		if (draftOpen) editor.advanceInstances();
		updatePageControls();
	}

	private void deleteInstance(String id) {
		try {
			BattleNext_StartedTournament instance = instances.get(id);
			if (instance == null) instance = BattleNext_SimulatorService.tournament(id).getStartedTournament();

			deletion.accept(instance.getName(tournamentStore.get(selectedId)), () -> {
				if (BattleNext_SimulatorService.tournament(id) != null) {
					try {
						BattleNext_SimulatorService.deleteTournament(id);
						refreshInstances();
					} catch (Exception exception) {
						setStatus(exception.toString());
					}
				} else {
					persistMutation(() -> {
						instances.delete(id);
						return true;
					}, this::refreshInstances);
				}
			});
		} catch (Exception exception) {
			setStatus(exception.toString());
		}
	}

	@Override
	protected boolean openEditor(String id) {
		return editor.open(tournamentStore.get(id));
	}

	private void refreshInstances() {
		editor.refreshInstances();
		updatePageControls();
	}

	@Override
	protected String deleteDescription() {
		return tournamentStore.get(selectedId).getName();
	}

	@Override
	protected BattleNext_TournamentStore catalog() {
		return tournamentStore;
	}
}
