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

public class BattleNext_UiManager extends BaseCustomUIPanelPlugin implements BattleNext_ConfirmationDialog.ConfirmationDialogHost {

	private static final float SHELL_OUTER_INSET = 8f;
	private static final float SHELL_TABS_TOP = 46f;
	private static final float SHELL_TAB_HEIGHT = 30f;
	private static final float SHELL_BODY_TOP = 86f;
	private static final float SHELL_BODY_BOTTOM_RESERVE = 48f;
	private static final float SHELL_TAB_GAP = 5f;
	private static final float SHELL_CLOSE_MAX_WIDTH = 180f;
	private static final float SHELL_CLOSE_WIDTH_SHARE = 0.3f;
	private static final float SHELL_STATUS_CLOSE_GAP = 10f;

	private enum BattleManagementPage {
		BATTLE_SELECTOR("page_battle_selector"),
		FLEETS("page_fleets"),
		VARIANTS("page_pinned_variants"),
		REPORTS("page_reports"),
		TOURNAMENTS("page_tournaments"),
		RULES("page_rules"),
		SETTINGS("page_settings");

		private final String titleKey;

		BattleManagementPage(String titleKey) {
			this.titleKey = titleKey;
		}
	}

	private final Runnable closeAction;
	private final BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_ConfirmationDialog confirmationDialog = new BattleNext_ConfirmationDialog(this, inputRouter);
	private BattleNext_StatusBar statusBar;

	private CustomPanelAPI panel;
	private UIRect bodyRect;
	private BattleNext_PageView attachedView;
	private final ButtonAPI[] tabButtons = new ButtonAPI[BattleManagementPage.values().length];
	private BattleManagementPage currentPage = BattleManagementPage.BATTLE_SELECTOR;
	private String activeTournamentId = "";
	private boolean pendingResult;

	private BattleNext_BattleSelectorPage battleSelectorPage;
	private BattleNext_FleetLibraryPage fleetPage;
	private BattleNext_PinnedVariantPage variantPage;
	private BattleNext_BattleReportPage reportPage;
	private BattleNext_TournamentPage tournamentPage;
	private BattleNext_RulesPage rulesPage;
	private BattleNext_SettingsPage settingsPage;

	public BattleNext_UiManager(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		BattleNext_UiInputRouter.activate(inputRouter);
		confirmationDialog.attach(panel);
		buildShell();
	}

	public void open() {
		currentPage = BattleManagementPage.BATTLE_SELECTOR;
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
		if (attachedView != null && !confirmationDialog.isActive()) {
			attachedView.advance(amount);
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
		if (buttonId instanceof BattleManagementPage) {
			requestPage((BattleManagementPage) buttonId);
			return;
		}
		if (buttonId instanceof BattleNext_BattleSelectorPage.BattleSourceSelectionRequest request) {
			requestSource(request.getId());
			return;
		}
		if ("close".equals(buttonId)) {
			requestClose();
			return;
		}
		activeView().buttonPressed(buttonId);
		if (currentPage == BattleManagementPage.TOURNAMENTS || "tournament:start".equals(buttonId)) refreshState();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {

		if (!confirmationDialog.isActive() && attachedView != null && attachedView.handleGlobalInput(events)) return true;
		if (inputRouter.handleGlobalInput(events)) return true;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				if (confirmationDialog.isActive()) {
					confirmationDialog.handle("confirm_cancel");
				} else if (attachedView == null || !attachedView.handleEscape()) {
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
		if (currentPage == BattleManagementPage.BATTLE_SELECTOR) {
			requestClose();
			return;
		}
		requestPage(BattleManagementPage.BATTLE_SELECTOR);
	}

	private void buildShell() {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		float shellWidth = width - SHELL_OUTER_INSET * 2f;
		UIRect headingRect = new UIRect(SHELL_OUTER_INSET, SHELL_OUTER_INSET,
				shellWidth, BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect tabsRect = new UIRect(SHELL_OUTER_INSET, SHELL_TABS_TOP,
				shellWidth, SHELL_TAB_HEIGHT);
		bodyRect = new UIRect(SHELL_OUTER_INSET, SHELL_BODY_TOP,
				shellWidth, height - SHELL_BODY_TOP - SHELL_BODY_BOTTOM_RESERVE);
		UIRect footerRect = new UIRect(SHELL_OUTER_INSET,
				height - SHELL_OUTER_INSET - BattleNext_PageLayout.FOOTER_HEIGHT,
				shellWidth, BattleNext_PageLayout.FOOTER_HEIGHT);

		BattleNext_PageLayout.heading(headingRect, panel, managementStrings.get("heading"));

		float tabWidth = (tabsRect.getWidth() - SHELL_TAB_GAP * (tabButtons.length - 1)) / tabButtons.length;
		for (int index = 0; index < tabButtons.length; index++) {
			BattleManagementPage page = BattleManagementPage.values()[index];
			UIRect tabRect = new UIRect(tabsRect.getX() + index * (tabWidth + SHELL_TAB_GAP),
					tabsRect.getY(), tabWidth, tabsRect.getHeight());
			tabButtons[index] = BattleNext_PageLayout.fixedButton(panel, tabRect,
					managementStrings.get(page.titleKey), page, CutStyle.TOP, false);
		}

		float closeWidth = Math.min(SHELL_CLOSE_MAX_WIDTH, footerRect.getWidth() * SHELL_CLOSE_WIDTH_SHARE);
		float statusWidth = footerRect.getWidth() - closeWidth - SHELL_STATUS_CLOSE_GAP;
		TooltipMakerAPI footer = panel.createUIElement(statusWidth, footerRect.getHeight(), false);
		statusBar = new BattleNext_StatusBar(statusWidth, footerRect.getHeight());
		statusBar.attach(footer, panel);
		panel.addUIElement(footer).inTL(footerRect.getX(), footerRect.getY());
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + footerRect.getWidth() - closeWidth,
						footerRect.getY(), closeWidth, footerRect.getHeight()),
				strings.get("close"), "close", CutStyle.BOTTOM, false);
	}

	private void showPage() {
		if (panel == null) return;
		BattleNext_UiInputRouter.activate(inputRouter);
		inputRouter.clear();
		if (attachedView != null) {
			attachedView.close();
		}
		for (int index = 0; index < tabButtons.length; index++) {
			tabButtons[index].setEnabled(BattleManagementPage.values()[index] != currentPage);
		}
		bodyRect.removeLatestSubPanel();
		CustomPanelAPI body = bodyRect.createSubPanel(panel, this).getPanel();
		BattleNext_PageView view = activeView();
		view.setInputRouter(inputRouter);
		if (currentPage == BattleManagementPage.FLEETS) fleetPage.setOverlayHost(panel);
		view.attach(body);
		attachedView = view;
	}

	private BattleNext_PageView activeView() {
		return viewFor(currentPage);
	}

	private BattleNext_PageView viewFor(BattleManagementPage page) {
		if (page == BattleManagementPage.BATTLE_SELECTOR) {
			if (battleSelectorPage == null) battleSelectorPage = new BattleNext_BattleSelectorPage(this::returnToBattleSelector, this);
			return battleSelectorPage;
		}
		if (page == BattleManagementPage.FLEETS) {
			if (fleetPage == null) fleetPage = new BattleNext_FleetLibraryPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
			return fleetPage;
		}
		if (page == BattleManagementPage.REPORTS) {
			if (reportPage == null) reportPage = new BattleNext_BattleReportPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
			return reportPage;
		}
		if (page == BattleManagementPage.VARIANTS) {
			if (variantPage == null) {
				variantPage = new BattleNext_PinnedVariantPage(this::returnToBattleSelector,
						this::confirmNavigation, this::confirmDelete);
			}
			return variantPage;
		}
		if (page == BattleManagementPage.TOURNAMENTS) {
			if (tournamentPage == null) tournamentPage = new BattleNext_TournamentPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
			return tournamentPage;
		}
		if (page == BattleManagementPage.RULES) {
			if (rulesPage == null) rulesPage = new BattleNext_RulesPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete);
			return rulesPage;
		}
		if (settingsPage == null) settingsPage = new BattleNext_SettingsPage();
		return settingsPage;
	}

	private void requestPage(BattleManagementPage page) {
		if (page == currentPage) return;
		if (attachedView != null) {
			attachedView.closeOverlays();
		}
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
		inputRouter.releaseFocus();
		if (attachedView != null) {
			attachedView.closeOverlays();
		}
		if (hasUnsavedChanges()) {
			confirmationDialog.request(this::close);
			return;
		}
		close();
	}

	private void close() {
		if (attachedView != null) {
			attachedView.close();
			attachedView = null;
		}
		closeAction.run();
	}

	private boolean hasUnsavedChanges() {
		return activeView().hasUnsavedChanges();
	}

	private void requestSource(String id) {

		battleSelectorPage.closeOverlays();
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
				throw new IllegalArgumentException(tournamentStrings.get("missing"));
			}
			tournamentStore.setActiveId(id);
			BattleNext_BattleSignals.clearAuthorization();
			refreshState();
			return true;
		} catch (Exception exception) {
			statusBar.showError(battleStrings.get("source_save_failed") + ": " + exception.getMessage());
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
			error = strings.get("load_failed") + ": " + exception.getMessage();
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

}
