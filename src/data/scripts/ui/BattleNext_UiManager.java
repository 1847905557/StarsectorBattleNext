package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_BattleSignals;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.page.*;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_StatusBar;
import data.scripts.ui.widget.BattleNext_TextField;
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
	private UIRect bodyRect;
	private final ButtonAPI[] tabButtons = new ButtonAPI[6];
	private Page currentPage = Page.BATTLE_SELECTOR;
	private String activeTournamentId = "";
	private boolean pendingResult;

	private BattleNext_BattleSelectorPage battleSelectorPage;
	private BattleNext_FleetLibraryPage fleetPage;
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
		if (currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null
				&& !confirmationDialog.isActive()) {
			battleSelectorPage.refreshRowStatuses();
		}
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
			requestSource(request.getId());
			return;
		}
		if ("close".equals(buttonId)) {
			requestClose();
			return;
		}
		activeView().buttonPressed(buttonId);
		if (currentPage == Page.TOURNAMENTS || "tournament:start".equals(buttonId)) refreshState();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (BattleNext_TextField.handleGlobalInput(events)) return true;
		if (!confirmationDialog.isActive() && currentPage == Page.FLEETS && fleetPage != null
				&& fleetPage.handleGlobalInput(events)) return true;
		if (!confirmationDialog.isActive() && currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null
				&& battleSelectorPage.handleGlobalInput(events)) return true;
		if (!confirmationDialog.isActive() && currentPage == Page.TOURNAMENTS && tournamentPage != null
				&& tournamentPage.handleGlobalInput(events)) return true;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				if (confirmationDialog.isActive()) {
					confirmationDialog.handle("confirm_cancel");
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

	public String getActiveTournamentId() {
		return activeTournamentId;
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
		UIRect headingRect = new UIRect(8f, 8f, width - 16f, BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect tabsRect = new UIRect(8f, 46f, width - 16f, 30f);
		bodyRect = new UIRect(8f, 86f, width - 16f, height - 134f);
		UIRect footerRect = new UIRect(8f, height - 38f, width - 16f, 30f);

		BattleNext_PageLayout.heading(headingRect, panel, text("panel_heading"));

		float tabWidth = (tabsRect.getWidth() - 25f) / tabButtons.length;
		for (int index = 0; index < 6; index++) {
			Page page = Page.values()[index];
			UIRect tabRect = new UIRect(tabsRect.getX() + index * (tabWidth + 5f), tabsRect.getY(), tabWidth, 30f);
			tabButtons[index] = BattleNext_PageLayout.fixedButton(panel, tabRect,
					text(page.titleKey), page, CutStyle.TOP, false);
		}

		float closeWidth = Math.min(180f, footerRect.getWidth() * 0.3f);
		statusBar.attach(panel, new UIRect(footerRect.getX(), footerRect.getY(),
				footerRect.getWidth() - closeWidth - 10f, footerRect.getHeight()));
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + footerRect.getWidth() - closeWidth, footerRect.getY(), closeWidth, 30f),
				text("close"), "close", CutStyle.BOTTOM, false);
	}

	private void showPage() {
		if (panel == null) return;
		BattleNext_TextField.clearRegisteredFields();
		if (fleetPage != null) fleetPage.closePicker();
		if (tournamentPage != null) tournamentPage.closePicker();
		for (int index = 0; index < tabButtons.length; index++) {
			tabButtons[index].setEnabled(Page.values()[index] != currentPage);
		}
		bodyRect.removeLatestSubPanel();
		CustomPanelAPI body = bodyRect.createSubPanel(panel, this).getPanel();
		BattleNext_PageView view = activeView();
		if (currentPage == Page.FLEETS) fleetPage.setOverlayHost(panel);
		view.attach(body);
	}

	private BattleNext_PageView activeView() {
		return viewFor(currentPage);
	}

	private BattleNext_PageView viewFor(Page page) {
		if (page == Page.BATTLE_SELECTOR) {
			if (battleSelectorPage == null) battleSelectorPage = new BattleNext_BattleSelectorPage(this::returnToBattleSelector, this);
			return battleSelectorPage;
		}
		if (page == Page.FLEETS) {
			if (fleetPage == null) fleetPage = new BattleNext_FleetLibraryPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
			return fleetPage;
		}
		if (page == Page.REPORTS) {
			if (reportPage == null) reportPage = new BattleNext_BattleReportPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
			return reportPage;
		}
		if (page == Page.TOURNAMENTS) {
			if (tournamentPage == null) tournamentPage = new BattleNext_TournamentPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
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
		if (page == currentPage) return;
		if (fleetPage != null) fleetPage.closePicker();
		if (tournamentPage != null) tournamentPage.closePicker();
		if (hasUnsavedChanges()) {
			confirmationDialog.request(() -> {
				currentPage = page;
				refreshState();
				showPage();
			});
			return;
		}
		currentPage = page;
		refreshState();
		showPage();
	}

	private void requestClose() {
		BattleNext_TextField.releaseFocusedField();
		if (fleetPage != null) fleetPage.closePicker();
		if (tournamentPage != null) tournamentPage.closePicker();
		if (hasUnsavedChanges()) {
			confirmationDialog.request(closeAction);
			return;
		}
		closeAction.run();
	}

	private boolean hasUnsavedChanges() {
		return activeView().hasUnsavedChanges();
	}

	private void requestSource(String id) {

		battleSelectorPage.closeDropdown();
		if (id.equals(activeTournamentId)) return;
		if (hasUnsavedChanges()) {
			confirmationDialog.request(() -> {
				if (applySource(id)) showPage();
			});
			return;
		}
		if (applySource(id)) showPage();
	}

	private void confirmNavigation(Runnable action) {
		if (hasUnsavedChanges()) {
			confirmationDialog.request(action);
			return;
		}
		action.run();
	}

	private void confirmDelete(String description, Runnable action) {
		confirmationDialog.requestDelete(description, () -> {
			action.run();
			refreshState();
		});
	}

	private boolean applySource(String id) {

		try {
			tournamentStore.reload();
			if (!id.isEmpty() && tournamentStore.get(id) == null) {
				throw new IllegalArgumentException(text("tournament_missing"));
			}
			tournamentStore.setActiveId(id);
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
			activeTournamentId = tournamentStore.getActiveId();
		} catch (Exception exception) {
			error = text("load_failed") + ": " + exception.getMessage();
		}
		statusBar.update(error, activeTournamentId, pendingResult);
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
