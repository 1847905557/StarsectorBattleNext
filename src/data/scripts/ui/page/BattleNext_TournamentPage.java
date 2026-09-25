package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_TournamentEditor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_TournamentPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RulesStore rulesStore = new BattleNext_RulesStore();
	private final BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(tournamentStore, fleetStore, rulesStore);
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
		} catch (RuntimeException exception) {
			status = exception.getMessage();
		}
		try {
			tournamentStore.reload();
		} catch (Exception exception) {
			status += (status.isEmpty() ? "" : " | ") + exception.getMessage();
		}
		try {
			rulesStore.reload();
		} catch (Exception exception) {
			status += (status.isEmpty() ? "" : " | ") + exception.getMessage();
		}
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), 30f),
				panel, text("page_tournaments"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER, 2f);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER, 2f);
		UIRect footerRect = regions[2];
		String[] actions = {"add", "save", "back"};
		float buttonWidth = Math.min(160f, (footerRect.getWidth() - 20f) / 3f);
		for (int index = 0; index < actions.length; index++) {
			ButtonAPI button = BattleNext_PageLayout.fixedButton(panel,
					new UIRect(footerRect.getX() + index * (buttonWidth + 10f), footerRect.getY(), buttonWidth, 30f),
					text(actions[index]), actions[index], CutStyle.ALL, true);
			if (index == 1) {
				saveButton = button;
				saveButton.setEnabled(tournamentStore.isLoaded() && fleetStore.isLoaded() && rulesStore.isLoaded());
			}
		}
		float statusX = 3f * buttonWidth + 30f;
		if (footerRect.getWidth() - statusX >= 60f) {
			TooltipMakerAPI footerStatus = panel.createUIElement(footerRect.getWidth() - statusX, 30f, false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, 5f);
			footerStatus.setHeightSoFar(30f);
			panel.addUIElement(footerStatus).inTL(footerRect.getX() + statusX, footerRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI listContent = listPanel.createUIElement(listRect.getWidth() - 18f, listRect.getHeight() - 16f, true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(8f, 8f);

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (!draftOpen) {
			TooltipMakerAPI prompt = detailPanel.createUIElement(detailRect.getWidth() - 18f, detailRect.getHeight() - 16f, false);
			prompt.addTitle(text("select_tournament"));
			detailPanel.addUIElement(prompt).inTL(8f, 8f);
			return;
		}
		BattleNext_Tournament selected = selectedId.isEmpty() ? null : tournamentStore.get(selectedId);
		CustomPanelAPI editorPanel = detailPanel.createCustomPanel(detailRect.getWidth() - 16f,
				detailRect.getHeight() - 50f, detailPanel.getPlugin());
		detailPanel.addComponent(editorPanel).inTL(8f, 8f);
		editor.attach(editorPanel);
		if (selected != null) {
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					new UIRect(8f, detailRect.getHeight() - 36f, 145f, 28f),
					text("delete"), "delete", CutStyle.ALL, true);
			delete.setEnabled(tournamentStore.isManaged(selectedId) && tournamentStore.isLoaded());
			if (!tournamentStore.isManaged(selectedId)) {
				TooltipMakerAPI note = detailPanel.createUIElement(detailRect.getWidth() - 170f, 28f, false);
				note.addPara(note.shortenString(text("source_unavailable_short"), detailRect.getWidth() - 180f),
						0f, BattleNext_UiTheme.MUTED);
				detailPanel.addUIElement(note).inTL(165f, detailRect.getHeight() - 34f);
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
			BattleNext_Tournament selected = tournamentStore.get(selectedId);
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
				+ tournamentStore.getLoadError(), 8f, BattleNext_UiTheme.MUTED);
		if (tournamentStore.getAll().isEmpty()) listContent.addPara(text("no_tournaments"), 10f, BattleNext_UiTheme.MUTED);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " [" + tournament.getId() + "]";
			BattleNext_PageLayout.buttonWithTooltip(listContent, label, "tournament:select:" + tournament.getId(),
					Math.max(80f, listRect.getWidth() - 30f));
		}
	}

	@Override
	public boolean hasUnsavedChanges() {
		return draftOpen && editor.hasUnsavedChanges();
	}

	@Override
	public boolean save() {
		if (!draftOpen || editor.isReadOnly()) return true;
		if (!tournamentStore.isLoaded() || !fleetStore.isLoaded() || !rulesStore.isLoaded()) {
			setStatus(text("load_failed") + ": " + (!tournamentStore.isLoaded() ? tournamentStore.getLoadError()
					: !fleetStore.isLoaded() ? fleetStore.getLoadError() : rulesStore.getLoadError()));
			return false;
		}
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValid(id)) {
			setStatus(text("invalid_tournament_id"));
			return false;
		}
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
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
