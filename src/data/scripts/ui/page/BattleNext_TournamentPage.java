package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
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
			BattleNext_PageLayout.buttonWithTooltip(listContent, tournament.getName(),
					"tournament:select:" + tournament.getId(),
					BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {
		editor.attach(contentPanel);
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
		editor.closePicker();
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
		if (!tournamentStore.isLoaded() || !fleetStore.isLoaded() || !ruleStore.isLoaded()) {
			setStatus(strings.get("load_failed") + ": " + (!tournamentStore.isLoaded() ? tournamentStore.getLoadError()
					: !fleetStore.isLoaded() ? fleetStore.getLoadError() : ruleStore.getLoadError()));
			return false;
		}
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValidPathSegment(id)) {
			setStatus(tournamentStrings.get("invalid_id"));
			return false;
		}
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, tournament.getId()) && !tournament.getId().equals(selectedId)) {
				setStatus(strings.get("duplicate_id")); return false;
			}
		}
		if (!selectedId.isEmpty() && BattleNext_EntryId.sameFileName(id, selectedId)
				&& !tournamentStore.isManaged(selectedId)) {
			setStatus(strings.get("source_unavailable")); return false;
		}
		if (!editor.serialize()) {
			setStatus(editor.getStatusText());
			return false;
		}
		try {
			tournamentStore.reload();
			selectedId = id;
			if (!editor.open(tournamentStore.get(id))) {
				setStatus(editor.getStatusText());
				return false;
			}
			setStatus(strings.get("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(strings.get("save_failed") + ": " + exception.getMessage());
			return false;
		}
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
		if (draftOpen) {
			editor.refreshDraftValidation();
		}
	}
}
