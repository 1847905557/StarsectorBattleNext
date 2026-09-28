package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TournamentPage implements BattleNext_PageView {

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return draftOpen && editor.handleGlobalInput(events);
	}

	public void refreshDraftValidation() {
		if (draftOpen) editor.refreshDraftValidation();
	}

	public void closePicker() {
		editor.closePicker();
	}

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RuleStore ruleStore = new BattleNext_RuleStore();
	private final BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournamentStore, fleetStore, ruleStore);
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");

	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private String selectedId = "";
	private boolean draftOpen;
	private LabelAPI statusLabel;
	private ButtonAPI saveButton;
	private String status = "";

	public BattleNext_TournamentPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
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
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), BattleNext_PageLayout.HEADER_HEIGHT),
				panel, text("page_tournaments"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		UIRect footerRect = regions[2];
		String[] actions = {"add", "save", "back"};
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footerRect, actions.length);
		for (int index = 0; index < actions.length; index++) {
			ButtonAPI button = BattleNext_PageLayout.fixedButton(panel,
					BattleNext_PageLayout.managementFooterButtonRect(footerRect, buttonWidth, index),
					text(actions[index]), actions[index], CutStyle.ALL, true);
			if (index == 1) {
				saveButton = button;
				saveButton.setEnabled(tournamentStore.isLoaded() && fleetStore.isLoaded() && ruleStore.isLoaded());
			}
		}
		UIRect statusRect = BattleNext_PageLayout.managementFooterStatusRect(footerRect, buttonWidth, actions.length);
		if (statusRect.getWidth() >= BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_MIN_WIDTH) {
			TooltipMakerAPI footerStatus = panel.createUIElement(statusRect.getWidth(), statusRect.getHeight(), false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_TOP_INSET);
			footerStatus.setHeightSoFar(statusRect.getHeight());
			panel.addUIElement(footerStatus).inTL(statusRect.getX(), statusRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		UIRect listContentRect = BattleNext_PageLayout.managementContentRect(listRect);
		TooltipMakerAPI listContent = listPanel.createUIElement(listContentRect.getWidth(), listContentRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(listContentRect.getX() - listRect.getX(),
				listContentRect.getY() - listRect.getY());

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (!draftOpen) {
			UIRect promptRect = BattleNext_PageLayout.managementContentRect(detailRect);
			TooltipMakerAPI prompt = detailPanel.createUIElement(promptRect.getWidth(), promptRect.getHeight(), false);
			prompt.addTitle(text("select_tournament"));
			detailPanel.addUIElement(prompt).inTL(promptRect.getX() - detailRect.getX(),
					promptRect.getY() - detailRect.getY());
			return;
		}
		BattleNext_TournamentModel selected = selectedId.isEmpty() ? null : tournamentStore.get(selectedId);
		UIRect editorRect = BattleNext_PageLayout.managementEditorRect(detailRect);
		CustomPanelAPI editorPanel = detailPanel.createCustomPanel(editorRect.getWidth(),
				editorRect.getHeight(), detailPanel.getPlugin());
		detailPanel.addComponent(editorPanel).inTL(editorRect.getX() - detailRect.getX(),
				editorRect.getY() - detailRect.getY());
		editor.attach(editorPanel);
		if (selected != null) {
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect),
					text("delete"), "delete", CutStyle.ALL, true);
			delete.setEnabled(tournamentStore.isManaged(selectedId) && tournamentStore.isLoaded());
			if (!tournamentStore.isManaged(selectedId)) {
				UIRect noteRect = BattleNext_PageLayout.managementSourceNoteLocalRect(detailRect);
				TooltipMakerAPI note = detailPanel.createUIElement(noteRect.getWidth(), noteRect.getHeight(), false);
				note.addPara(note.shortenString(text("source_unavailable_short"),
						detailRect.getWidth() - BattleNext_PageLayout.MANAGEMENT_SOURCE_NOTE_TEXT_RESERVE),
						0f, BattleNext_UiTheme.MUTED);
				detailPanel.addUIElement(note).inTL(noteRect.getX(), noteRect.getY());
			}
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
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
			deletion.accept(selected.getName() + " [" + selectedId + "]", () -> {
				try {
					tournamentStore.delete(selectedId);
					selectedId = "";
					draftOpen = false;
					setStatus(text("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception.getMessage());
				}
			});
		} else if ("back".equals(buttonId)) {
			if (draftOpen) navigation.accept(() -> { selectedId = ""; draftOpen = false; refreshContent(); });
			else backAction.run();
		} else if (draftOpen) {
			editor.buttonPressed(buttonId);
		}
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_tournaments"));
		if (!tournamentStore.isLoaded()) listContent.addPara(text("load_failed") + ": "
				+ tournamentStore.getLoadError(), BattleNext_PageLayout.MANAGEMENT_LIST_ERROR_TOP_GAP,
				BattleNext_UiTheme.MUTED);
		if (tournamentStore.getAll().isEmpty()) listContent.addPara(text("no_tournaments"),
				BattleNext_PageLayout.MANAGEMENT_LIST_EMPTY_TOP_GAP, BattleNext_UiTheme.MUTED);
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, tournament.getName(),
					"tournament:select:" + tournament.getId(),
					BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	@Override
	public boolean hasUnsavedChanges() {
		return draftOpen && editor.hasUnsavedChanges();
	}

	@Override
	public boolean save() {
		if (!draftOpen || editor.isReadOnly()) return true;
		if (!tournamentStore.isLoaded() || !fleetStore.isLoaded() || !ruleStore.isLoaded()) {
			setStatus(text("load_failed") + ": " + (!tournamentStore.isLoaded() ? tournamentStore.getLoadError()
					: !fleetStore.isLoaded() ? fleetStore.getLoadError() : ruleStore.getLoadError()));
			return false;
		}
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValidPathSegment(id)) {
			setStatus(text("invalid_tournament_id"));
			return false;
		}
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, tournament.getId()) && !tournament.getId().equals(selectedId)) {
				setStatus(text("duplicate_id")); return false;
			}
		}
		if (!selectedId.isEmpty() && BattleNext_EntryId.sameFileName(id, selectedId)
				&& !tournamentStore.isManaged(selectedId)) {
			setStatus(text("source_unavailable")); return false;
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
			setStatus(text("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(text("save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override
	public void discard() {
		if (selectedId.isEmpty()) {
			draftOpen = false;
			refreshContent();
		} else {
			editor.deserialize();
		}
	}

	@Override
	public String getStatusText() {
		return status;
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}
}
