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
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TournamentPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RuleStore ruleStore = new BattleNext_RuleStore();
	private final BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournamentStore, fleetStore, ruleStore);
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private BattleNext_TournamentDisplay tournamentDisplay;

	private String selectedId = "";
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
		draftOpen = false;
		status = "";
		try {
			fleetStore.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			status = exception.getMessage();
		}
		try {
			tournamentStore.reload();
		} catch (Exception exception) {
			status += (status.isEmpty() ? "" : " | ") + exception.getMessage();
		}
		try {
			ruleStore.reload();
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
		return tournamentStore.getAll().isEmpty();
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
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {

		BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
		if (tournament == null || !tournament.isStarted()) {
			attachEditor(contentPanel);
			return;
		}
		Map<String, String> fleetNames = new LinkedHashMap<>();
		for (String fleetId : tournament.getFleetIds()) {
			if (fleetStore.get(fleetId) != null) {
				fleetNames.put(fleetId, fleetStore.get(fleetId).getName());
			}
		}
		BattleNext_Match next = tournament.getNextPendingMatch();
		List<String> warnings = new ArrayList<>();
		if (selectedId.equals(tournamentStore.getActiveId()) && next != null) {
			provider.load();
			warnings.addAll(provider.getWarnings());
			warnings.addAll(provider.getRuleWarnings());
		}
		BattleNext_TournamentDisplay.Details details = new BattleNext_TournamentDisplay.Details(fleetNames,
				ruleStore.get(tournament.getRuleId()) != null, next, status, warnings, false, false);
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, details, this::buttonPressed);
		tournamentDisplay.attach(contentPanel);
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
		return tournamentStore.isLoaded() && fleetStore.isLoaded() && ruleStore.isLoaded();
	}

	@Override
	protected boolean canDelete() {
		return tournamentStore.isLoaded() && tournamentStore.isManaged(selectedId);
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
			if (draftOpen) navigation.accept(() -> { selectedId = ""; draftOpen = false; refreshContent(); });
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
	protected boolean savePage() {
		if (!draftOpen || editor.isReadOnly()) return true;
		if (!editor.save()) {
			setStatus(editor.getStatusText());
			return false;
		}
		selectedId = editor.getDraftId();
		setStatus(strings.get("saved"));
		try {
			tournamentStore.reload();
			refreshContent();
		} catch (Exception exception) {
			setStatus(strings.get("saved") + " / " + strings.get("load_failed") + ": " + exception);
		}
		return true;
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
		if (draftOpen) {
			editor.refreshDraftValidation();
		}
	}
}
