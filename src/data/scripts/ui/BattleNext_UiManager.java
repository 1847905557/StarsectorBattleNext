package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.page.*;
import data.scripts.ui.BattleNext_ManagementSession.Catalogs;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_StatusBar;
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
	private final BattleNext_I18nSection simulatorStrings = BattleNext_I18nSection.getInstance("Simulator", "BattleNext_simulator_");

	private Catalogs data;
	private BattleNext_ManagementSession catalogs;
	private boolean loading;
	private String refreshSelection = "";
	private LabelAPI loadingLabel;
	private ButtonAPI refreshButton;
	private final BattleNext_ConfirmationDialog confirmationDialog = new BattleNext_ConfirmationDialog(this, inputRouter);
	private BattleNext_StatusBar statusBar;
	private TooltipMakerAPI headingContent;
	private LabelAPI headingLabel;
	private ButtonAPI closeButton;

	private CustomPanelAPI panel;
	private UIRect bodyRect;
	private BattleNext_PageView attachedView;
	private final ButtonAPI[] tabButtons = new ButtonAPI[BattleManagementPage.values().length];
	private BattleManagementPage currentPage = BattleManagementPage.BATTLE_SELECTOR;
	private String activeTournamentId = "";
	private String reportId = "";

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
		clearReportNotification();
		currentPage = BattleManagementPage.BATTLE_SELECTOR;
		startLoading();
	}

	public boolean isLoading() {
		return loading;
	}

	public boolean isSaving() {
		return attachedView != null && attachedView.isSaving();
	}

	private void startLoading() {

		if (attachedView != null) attachedView.close();
		attachedView = null;
		if (catalogs != null) catalogs.close();
		catalogs = null;
		data = null;

		activeTournamentId = "";
		battleSelectorPage = null;
		fleetPage = null;
		variantPage = null;
		reportPage = null;
		tournamentPage = null;
		rulesPage = null;
		settingsPage = null;

		loading = true;
		refreshButton.setEnabled(false);
		showLoadingPage();

		catalogs = new BattleNext_ManagementSession();
		catalogs.activate();
		catalogs.start();
	}

	private void showLoadingPage() {
		bodyRect.removeLatestSubPanel();
		CustomPanelAPI body = bodyRect.createSubPanel(panel, this).getPanel();
		TooltipMakerAPI content = body.createUIElement(bodyRect.getWidth(), bodyRect.getHeight(), false);
		loadingLabel = content.addPara(strings.format("loading", loadingText(catalogs == null ? "" : catalogs.loading())), 0f);

		body.addUIElement(content).inTL(8f, 8f);
	}

	@Override
	public void positionChanged(PositionAPI position) {

	}

	@Override
	public void render(float alphaMult) {

	}

	@Override
	public void advance(float amount) {

		if (catalogs != null) {
			catalogs.activate();
			if (!loading && !isSaving() && catalogs.advance() && attachedView != null) attachedView.catalogChanged();

			if (loading) {
				loadingLabel.setText(strings.format("loading", loadingText(catalogs.loading())));
				if (catalogs.isReady()) {
					data = catalogs.data();
					loading = false;
					refreshButton.setEnabled(true);

					refreshState();
					showPage();
					attachedView.restoreSelection(refreshSelection);
					refreshSelection = "";

					if (!catalogs.errorsCopy().isEmpty()) statusBar.showError(String.join(" | ", catalogs.errorsCopy()));
				}
			}
		}

		if (attachedView != null) {
			Boolean saved = attachedView.completeSave();
			if (saved != null) {
				if (confirmationDialog.isActive()) confirmationDialog.saveCompleted(saved);
				else if (saved) attachedView.refreshAfterSave();
			}

			String savedId = BattleNext_BattleSignals.consumePendingResult();
			if (!savedId.isEmpty()) {
				if (catalogs != null) catalogs.refreshReports();
				reportId = savedId;
				statusBar.reportSaved(reportId);
			}
		}

		if (attachedView != null && !attachedView.isSaving() && !confirmationDialog.isActive()) {
			attachedView.advance(amount);
		}
	}

	@Override
	public void processInput(List<InputEventAPI> events) {

	}

	@Override
	public void buttonPressed(Object buttonId) {

		if (catalogs != null) catalogs.activate();
		if (attachedView != null && attachedView.isSaving()) return;

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

		if ("refresh".equals(buttonId)) {
			if (loading) return;
			confirmNavigation(() -> {
				refreshSelection = attachedView == null ? "" : attachedView.selection();
				startLoading();
			});
			return;
		}

		if (attachedView == null) return;

		boolean cnText = BattleNext_Settings.isCnTextEnabled();
		activeView().buttonPressed(buttonId);
		if (cnText != BattleNext_Settings.isCnTextEnabled()) {
			refreshShellText();
		}
		if (currentPage == BattleManagementPage.TOURNAMENTS || "tournament:create".equals(buttonId) || "tournament:active".equals(buttonId)) {
			refreshState();
		}
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (attachedView != null && attachedView.isSaving()) {
			for (InputEventAPI event : events) event.consume();
			return true;
		}

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

	public String getPendingReportId() {
		return reportId;
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

		headingContent = panel.createUIElement(headingRect.getWidth(), headingRect.getHeight(), false);
		headingLabel = BattleNext_PageLayout.heading(headingRect, panel, headingContent, managementStrings.get("heading"));
		headingContent.setButtonFontOrbitron20();

		float tabWidth = (tabsRect.getWidth() - SHELL_TAB_GAP * (tabButtons.length - 1)) / tabButtons.length;
		for (int index = 0; index < tabButtons.length; index++) {
			BattleManagementPage page = BattleManagementPage.values()[index];
			UIRect tabRect = new UIRect(tabsRect.getX() + index * (tabWidth + SHELL_TAB_GAP),
					tabsRect.getY(), tabWidth, tabsRect.getHeight());
			tabButtons[index] = BattleNext_PageLayout.fixedButton(panel, tabRect,
					managementStrings.get(page.titleKey), page, CutStyle.TOP, false);
		}

		float closeWidth = Math.min(SHELL_CLOSE_MAX_WIDTH, footerRect.getWidth() * SHELL_CLOSE_WIDTH_SHARE);
		float refreshWidth = Math.min(120f, closeWidth);
		float statusWidth = footerRect.getWidth() - closeWidth - refreshWidth - SHELL_STATUS_CLOSE_GAP * 2f;
		TooltipMakerAPI footer = panel.createUIElement(statusWidth, footerRect.getHeight(), false);
		statusBar = new BattleNext_StatusBar(statusWidth, footerRect.getHeight());
		statusBar.attach(footer, panel);

		panel.addUIElement(footer).inTL(footerRect.getX(), footerRect.getY());

		refreshButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + statusWidth + SHELL_STATUS_CLOSE_GAP, footerRect.getY(), refreshWidth, footerRect.getHeight()),
				strings.get("refresh"), "refresh", CutStyle.BOTTOM, false);
		closeButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + footerRect.getWidth() - closeWidth,
						footerRect.getY(), closeWidth, footerRect.getHeight()),
				strings.get("close"), "close", CutStyle.BOTTOM, false);
	}

	private void refreshShellText() {
		BattleNext_PageLayout.refreshHeading(headingContent, headingLabel, bodyRect.getWidth(), managementStrings.get("heading"));
		for (int index = 0; index < tabButtons.length; index++) {
			BattleNext_PageLayout.refreshButton(headingContent, tabButtons[index],
					managementStrings.get(BattleManagementPage.values()[index].titleKey));
		}
		BattleNext_PageLayout.refreshButton(headingContent, closeButton, strings.get("close"));
		BattleNext_PageLayout.refreshButton(headingContent, refreshButton, strings.get("refresh"));
		statusBar.refreshText();
	}

	private void showPage() {
		if (panel == null) return;

		BattleNext_UiInputRouter.activate(inputRouter);
		inputRouter.clear();
		if (attachedView != null) {
			attachedView.close();
		}
		attachedView = null;

		for (int index = 0; index < tabButtons.length; index++) {
			tabButtons[index].setEnabled(BattleManagementPage.values()[index] != currentPage);
		}
		if (loading) {
			showLoadingPage();
			return;
		}

		bodyRect.removeLatestSubPanel();
		CustomPanelAPI body = bodyRect.createSubPanel(panel, this).getPanel();
		BattleNext_PageView view = activeView();
		view.setInputRouter(inputRouter);
		view.setOverlayHost(panel);
		if (catalogs != null) view.setPersistence(catalogs::persist);

		view.attach(body);
		attachedView = view;
	}

	private BattleNext_PageView activeView() {
		return viewFor(currentPage);
	}

	private BattleNext_PageView viewFor(BattleManagementPage page) {

		if (page == BattleManagementPage.BATTLE_SELECTOR) {
			if (battleSelectorPage == null) {
				battleSelectorPage = new BattleNext_BattleSelectorPage(this::returnToBattleSelector, this,
						data().source(), data().reports());
			}
			return battleSelectorPage;
		}

		if (page == BattleManagementPage.FLEETS) {
			if (fleetPage == null) fleetPage = new BattleNext_FleetLibraryPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete, data().fleets(), data().variants());
			return fleetPage;
		}

		if (page == BattleManagementPage.REPORTS) {
			if (reportPage == null) reportPage = new BattleNext_BattleReportPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete, data().reports());
			return reportPage;
		}

		if (page == BattleManagementPage.VARIANTS) {
			if (variantPage == null) {
				variantPage = new BattleNext_PinnedVariantPage(this::returnToBattleSelector,
						this::confirmNavigation, this::confirmDelete, data().pins());
			}
			return variantPage;
		}

		if (page == BattleManagementPage.TOURNAMENTS) {
			if (tournamentPage == null) tournamentPage = new BattleNext_TournamentPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete, data());
			return tournamentPage;
		}

		if (page == BattleManagementPage.RULES) {
			if (rulesPage == null) rulesPage = new BattleNext_RulesPage(this::returnToBattleSelector,
					this::confirmNavigation, this::confirmDelete, data().rules());
			return rulesPage;
		}

		if (settingsPage == null) {
			settingsPage = new BattleNext_SettingsPage(this::confirmSimulatorEnable, data().source(), data().reports());
		}
		return settingsPage;
	}

	private Catalogs data() {
		if (data == null) data = Catalogs.create();
		return data;
	}

	private String loadingText(String stage) {
		return switch (stage) {
			case "fleets" -> managementStrings.get("page_fleets");
			case "quick_battles" -> battleStrings.get("quick_battle");
			case "rules" -> managementStrings.get("page_rules");
			case "tournaments", "instances" -> managementStrings.get("page_tournaments");
			case "reports" -> managementStrings.get("page_reports");
			case "variants" -> managementStrings.get("page_pinned_variants");
			default -> managementStrings.get("heading");
		};
	}

	private void requestPage(BattleManagementPage page) {
		if (page == currentPage) return;

		if (attachedView != null) {
			attachedView.closeOverlays();
		}
		if (hasUnsavedChanges()) {
			confirmationDialog.request(() -> {
				clearReportNotification();
				currentPage = page;
				refreshState();
				showPage();
			});
			return;
		}

		clearReportNotification();
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
		clearReportNotification();
		if (attachedView != null) {
			attachedView.close();
			attachedView = null;
		}

		settingsPage = null;
		battleSelectorPage = null;
		loading = false;
		if (catalogs != null) catalogs.close();
		catalogs = null;
		data = null;

		closeAction.run();
	}

	private boolean hasUnsavedChanges() {
		return attachedView != null && attachedView.hasUnsavedChanges();
	}

	private void requestSource(String id) {
		battleSelectorPage.closeOverlays();
		confirmNavigation(() -> battleSelectorPage.persistMutation(() -> {
			if (!id.isEmpty() && data().tournaments().get(id) == null) {
				throw new IllegalArgumentException("Selected tournament is missing: " + id);
			}
			if (id.isEmpty()) data().instances().activate("");
			return true;
		}, () -> {
			battleSelectorPage.selectSource(id);
			refreshState();
			showPage();
		}, strings.get("saving"), battleStrings.get("source_save_failed")));
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

	private void confirmSimulatorEnable(Runnable action) {
		confirmationDialog.requestAction(simulatorStrings.get("enable"),
				simulatorStrings.get("confirm_message"), action);
	}

	private void refreshState() {
		if (loading) return;

		if (catalogs != null) {
			activeTournamentId = data.instances().getActiveId();
			statusBar.update("", activeTournamentId, reportId);
			return;
		}

		String error = "";
		try {
			data().instances().ensureLoaded();
			activeTournamentId = data().instances().getActiveId();
		} catch (Exception exception) {
			error = strings.get("load_failed") + ": " + exception.getMessage();
		}

		statusBar.update(error, activeTournamentId, reportId);
	}

	private void clearReportNotification() {
		BattleNext_BattleSignals.clearPendingResult();
		reportId = "";
		statusBar.reportSaved(reportId);
	}

	@Override
	public boolean saveCurrentPage() {
		boolean cnText = BattleNext_Settings.isCnTextEnabled();
		boolean saved = activeView().save();
		if (saved && cnText != BattleNext_Settings.isCnTextEnabled()) {
			refreshShellText();
		}
		return saved;
	}

	@Override
	public String getSaveError() {
		return activeView().getStatusText();
	}

	@Override
	public List<String> getSaveErrors() {
		return activeView().getSaveErrors();
	}

	@Override
	public void discardCurrentPage() {
		activeView().discard();
	}

}
