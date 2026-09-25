package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.editor.BattleNext_FleetDraftEditor;
import data.scripts.ui.page.*;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_StatusBar;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class BattleNext_UiManager extends BaseCustomUIPanelPlugin implements BattleNext_ConfirmationDialog.Host {

	private enum Page {
		BATTLE_SELECTOR("page_battle_selector"),
		FLEETS("page_fleets"),
		REPORTS("page_reports"),
		TOURNAMENTS("page_tournaments"),
		RULES("page_rules"),
		SETTINGS("page_settings");

		private final String titleKey;

		Page(String titleKey) {
			this.titleKey = titleKey;
		}
	}

	private final Runnable closeAction;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_ConfirmationDialog confirmationDialog = new BattleNext_ConfirmationDialog(this);
	private final BattleNext_StatusBar statusBar = new BattleNext_StatusBar();

	private CustomPanelAPI panel;
	private UIRect headingRect;
	private UIRect tabsRect;
	private UIRect bodyRect;
	private UIRect footerRect;
	private final ButtonAPI[] tabButtons = new ButtonAPI[6];
	private Page currentPage = Page.BATTLE_SELECTOR;
	private boolean editorOpen;
	private int activeTournamentSlot = -1;
	private boolean pendingResult;

	private BattleNext_BattleSelectorPage battleSelectorPage;
	private BattleNext_FleetLibraryPage fleetPage;
	private BattleNext_FleetDraftEditor draftEditor;
	private BattleNext_BattleReportPage reportPage;
	private BattleNext_TournamentPage tournamentPage;
	private BattleNext_RulesPage rulesPage;
	private BattleNext_SettingsPage settingsPage;

	public BattleNext_UiManager(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		confirmationDialog.attach(panel);
		buildShell();
	}

	public void open() {
		currentPage = Page.BATTLE_SELECTOR;
		editorOpen = false;
		refreshState();
		showPage();
	}

	@Override
	public void positionChanged(PositionAPI position) {
	}

	@Override
	public void render(float alphaMult) {
	}

	@Override
	public void advance(float amount) {
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (confirmationDialog.isActive()) {
			confirmationDialog.handle(buttonId);
			return;
		}
		if (buttonId instanceof Page) {
			requestPage((Page) buttonId);
			return;
		}
		if (buttonId instanceof BattleNext_BattleSelectorPage.SourceRequest request) {
			requestSource(request.getSlot());
			return;
		}
		if ("close".equals(buttonId)) {
			requestClose();
			return;
		}
		if (buttonId instanceof BattleNext_FleetLibraryPage.DraftRequest request) {
			openEditor(request);
			return;
		}
		activeView().buttonPressed(buttonId);
		if (currentPage == Page.TOURNAMENTS) refreshState();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (!confirmationDialog.isActive() && currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null
				&& battleSelectorPage.handleGlobalInput(events)) return true;
		for (InputEventAPI event : events) {
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				if (confirmationDialog.isActive()) {
					confirmationDialog.handle("confirm_cancel");
				} else if (editorOpen) {
					requestPage(Page.FLEETS);
				} else if (currentPage == Page.FLEETS && fleetPage != null) {
					fleetPage.buttonPressed("back");
				} else if (currentPage == Page.REPORTS && reportPage != null) {
					reportPage.buttonPressed("back");
				} else if (currentPage == Page.TOURNAMENTS && tournamentPage != null) {
					tournamentPage.buttonPressed("back");
				} else {
					requestClose();
				}
				event.consume();
				return true;
			}
		}
		return false;
	}

	public int getActiveTournamentSlot() {
		return activeTournamentSlot;
	}

	public boolean hasPendingResult() {
		return pendingResult;
	}

	private void returnToBattleSelector() {
		if (currentPage == Page.BATTLE_SELECTOR) {
			requestClose();
			return;
		}
		requestPage(Page.BATTLE_SELECTOR);
	}

	private void buildShell() {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		headingRect = new UIRect(8f, 8f, width - 16f, BattleNext_PageLayout.HEADER_HEIGHT);
		tabsRect = new UIRect(8f, 46f, width - 16f, 30f);
		bodyRect = new UIRect(8f, 86f, width - 16f, height - 134f);
		footerRect = new UIRect(8f, height - 38f, width - 16f, 30f);

		BattleNext_PageLayout.heading(headingRect, panel, text("panel_heading"));

		TooltipMakerAPI tabs = tabsRect.beginTooltip(panel, false);
		tabs.setButtonFontOrbitron20();
		float tabWidth = (width - 16f - 25f) / 6f;
		for (int index = 0; index < 6; index++) {
			Page page = Page.values()[index];
			ButtonAPI button = tabs.addButton(text(page.titleKey), page, BattleNext_UiTheme.TEXT,
					BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.TOP, tabWidth, 30f, 0f);
			button.getPosition().inBL(index * (tabWidth + 5f), 0f);
			button.setMouseOverSound("BUtil_button_in");
			button.setButtonPressedSound("BUtil_button_down");
			tabButtons[index] = button;
		}
		// addButton accumulates row heights even after the buttons are positioned side by side.
		tabs.setHeightSoFar(30f);
		tabsRect.addTooltip();

		TooltipMakerAPI footer = footerRect.beginTooltip(panel, false);
		footer.setButtonFontOrbitron20();
		statusBar.attach(footer);
		ButtonAPI close = footer.addButton(text("close"), "close", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.BOTTOM, 180f, 30f, 0f);
		close.getPosition().inBR(0f, 0f);
		close.setMouseOverSound("BUtil_button_in");
		close.setButtonPressedSound("BUtil_button_down");
		footerRect.addTooltip();
	}

	private void showPage() {
		if (panel == null) return;
		for (int index = 0; index < tabButtons.length; index++) {
			tabButtons[index].setEnabled(editorOpen || Page.values()[index] != currentPage);
		}
		bodyRect.removeLatestSubPanel();
		CustomPanelAPI body = bodyRect.createSubPanel(panel, this).getPanel();
		activeView().attach(body);
	}

	private BattleNext_PageView activeView() {
		return editorOpen ? editorView() : viewFor(currentPage);
	}

	private BattleNext_FleetDraftEditor editorView() {
		if (draftEditor == null) draftEditor = new BattleNext_FleetDraftEditor(() -> requestPage(Page.FLEETS));
		return draftEditor;
	}

	private BattleNext_PageView viewFor(Page page) {
		if (page == Page.BATTLE_SELECTOR) {
			if (battleSelectorPage == null) battleSelectorPage = new BattleNext_BattleSelectorPage(this::returnToBattleSelector, this);
			return battleSelectorPage;
		}
		if (page == Page.FLEETS) {
			if (fleetPage == null) fleetPage = new BattleNext_FleetLibraryPage(this::returnToBattleSelector, this::buttonPressed);
			return fleetPage;
		}
		if (page == Page.REPORTS) {
			if (reportPage == null) reportPage = new BattleNext_BattleReportPage(this::returnToBattleSelector);
			return reportPage;
		}
		if (page == Page.TOURNAMENTS) {
			if (tournamentPage == null) tournamentPage = new BattleNext_TournamentPage(this::returnToBattleSelector);
			return tournamentPage;
		}
		if (page == Page.RULES) {
			if (rulesPage == null) rulesPage = new BattleNext_RulesPage();
			return rulesPage;
		}
		if (settingsPage == null) settingsPage = new BattleNext_SettingsPage();
		return settingsPage;
	}

	private void requestPage(Page page) {
		if (!editorOpen && page == currentPage) return;
		if (hasUnsavedChanges()) {
			confirmationDialog.request(() -> {
				editorOpen = false;
				currentPage = page;
				refreshState();
				showPage();
			});
			return;
		}
		editorOpen = false;
		currentPage = page;
		refreshState();
		showPage();
	}

	private void requestClose() {
		if (hasUnsavedChanges()) {
			confirmationDialog.request(closeAction);
			return;
		}
		closeAction.run();
	}

	private boolean hasUnsavedChanges() {
		return activeView().hasUnsavedChanges();
	}

	private void requestSource(int slot) {

		battleSelectorPage.closeDropdown();
		if (slot == activeTournamentSlot) return;
		if (hasUnsavedChanges()) {
			confirmationDialog.request(() -> {
				if (applySource(slot)) showPage();
			});
			return;
		}
		if (applySource(slot)) showPage();
	}

	private void openEditor(BattleNext_FleetLibraryPage.DraftRequest request) {
		BattleNext_FleetModel source = request.getFleet();
		boolean clone = "clone".equals(request.getAction());
		if (hasUnsavedChanges()) {
			confirmationDialog.request(() -> {
				editorView().open(source, clone);
				editorOpen = true;
				showPage();
			});
			return;
		}
		editorView().open(source, clone);
		editorOpen = true;
		showPage();
	}

	private boolean applySource(int slot) {

		try {
			tournamentStore.reload();
			if (slot >= 0 && tournamentStore.get(slot) == null) {
				throw new IllegalArgumentException(text("tournament_missing"));
			}
			tournamentStore.setActiveSlot(slot);
			refreshState();
			return true;
		} catch (Exception exception) {
			statusBar.showError(text("source_save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	private void refreshState() {
		pendingResult = BattleNext_BattleSignals.consumePendingResult();
		String error = "";
		try {
			tournamentStore.reload();
			activeTournamentSlot = tournamentStore.getActiveSlot();
		} catch (Exception exception) {
			activeTournamentSlot = -1;
			error = text("load_failed") + ": " + exception.getMessage();
		}
		statusBar.update(error, activeTournamentSlot, pendingResult);
	}

	@Override
	public boolean saveCurrentPage() {
		return activeView().save();
	}

	@Override
	public String getSaveError() {
		return activeView().getStatusText();
	}

	@Override
	public void discardCurrentPage() {
		activeView().discard();
	}

	private String text(String key) {
		return strings.get(key);
	}
}
