package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.util.BattleNext_I18nSection;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.boxutil.manager.ShaderCore;
import org.lwjgl.input.Keyboard;

import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.tournament.BattleNext_TournamentStore;

import java.util.List;

public class BattleNext_UiManager extends BaseCustomUIPanelPlugin {

	private enum Page {
		BATTLE_SELECTOR("page_battle_selector"), FLEETS("page_fleets"), REPORTS("page_reports"),
		TOURNAMENTS("page_tournaments"), RULES("page_rules"), SETTINGS("page_settings"),
		DRAFT("fleet_editor");

		private final String titleKey;

		Page(String titleKey) {
			this.titleKey = titleKey;
		}
	}

	private final Runnable closeAction;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private CustomPanelAPI body;
	private CustomPanelAPI confirmation;
	private LabelAPI statusLabel;
	private LabelAPI confirmationStatus;
	private final ButtonAPI[] tabButtons = new ButtonAPI[6];
	private Page currentPage = Page.BATTLE_SELECTOR;
	private Page pendingPage;
	private Integer pendingSourceSlot;
	private boolean pendingClose;
	private int activeTournamentSlot = -1;
	private boolean pendingResult;
	private String sourceStatus = "";
	private BattleNext_BattleSelectorPage battleSelectorPage;
	private BattleNext_FleetLibraryPage fleetPage;
	private BattleNext_FleetDraftEditor draftEditor;
	private BattleNext_FleetLibraryPage.DraftRequest pendingDraft;
	private BattleNext_BattleReportPage reportPage;
	private BattleNext_TournamentPage tournamentPage;
	private BattleNext_RulesPage rulesPage;
	private BattleNext_SettingsPage settingsPage;

	public BattleNext_UiManager(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		buildShell();
	}

	public void open() {
		currentPage = Page.BATTLE_SELECTOR;
		pendingDraft = null;
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
		if (confirmation != null) {
			handleConfirmation(buttonId);
			return;
		}
		if (buttonId instanceof Page) {
			requestPage((Page) buttonId);
		} else if (buttonId instanceof BattleNext_BattleSelectorPage.SourceRequest) {
			requestSource(((BattleNext_BattleSelectorPage.SourceRequest) buttonId).getSlot());
		} else if ("close".equals(buttonId)) {
			requestClose();
		} else if (buttonId instanceof BattleNext_FleetLibraryPage.DraftRequest) {
			pendingDraft = (BattleNext_FleetLibraryPage.DraftRequest)buttonId;
			requestPage(Page.DRAFT);
		} else if (currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null) {
			battleSelectorPage.buttonPressed(buttonId);
		} else if (currentPage == Page.FLEETS && fleetPage != null) {
			fleetPage.buttonPressed(buttonId);
		} else if (currentPage == Page.DRAFT && draftEditor != null) {
			draftEditor.buttonPressed(buttonId);
		} else if (currentPage == Page.REPORTS && reportPage != null) {
			reportPage.buttonPressed(buttonId);
		} else if (currentPage == Page.TOURNAMENTS && tournamentPage != null) {
			tournamentPage.buttonPressed(buttonId);
			refreshState();
		}
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (confirmation == null && currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null
				&& battleSelectorPage.handleGlobalInput(events)) return true;
		for (InputEventAPI event : events) {
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				if (confirmation != null) {
					handleConfirmation("confirm_cancel");
				} else if (currentPage == Page.DRAFT) {
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
		TooltipMakerAPI heading = panel.createUIElement(width - 16f, 30f, false);
		LabelAPI title = heading.addSectionHeading(text("panel_heading"), BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID, 0f);
		title.getPosition().inTL(0f, 0f);
		panel.addUIElement(heading).inTL(8f, 8f);

		TooltipMakerAPI tabs = panel.createUIElement(width - 16f, 30f, false);
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
		panel.addUIElement(tabs).inTL(8f, 46f);

		TooltipMakerAPI footer = panel.createUIElement(width - 16f, 30f, false);
		footer.setButtonFontOrbitron20();
		statusLabel = footer.addPara(statusText(), 0f, BattleNext_UiTheme.MUTED);
		statusLabel.getPosition().inBL(0f, 6f);
		ButtonAPI close = footer.addButton(text("close"), "close", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.BOTTOM, 180f, 30f, 0f);
		close.getPosition().inBR(0f, 0f);
		close.setMouseOverSound("BUtil_button_in");
		close.setButtonPressedSound("BUtil_button_down");
		panel.addUIElement(footer).inBL(8f, 8f);
	}

	private void showPage() {
		if (panel == null) return;
		for (int index = 0; index < tabButtons.length; index++) {
			tabButtons[index].setEnabled(Page.values()[index] != currentPage);
		}
		if (body != null) panel.removeComponent(body);
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		body = Global.getSettings().createCustom(width - 16f, height - 134f, this);
		panel.addComponent(body).inTL(8f, 86f);
		if (currentPage == Page.BATTLE_SELECTOR) {
			if (battleSelectorPage == null) battleSelectorPage = new BattleNext_BattleSelectorPage(this::returnToBattleSelector, this);
			battleSelectorPage.attach(body);
		} else if (currentPage == Page.FLEETS) {
			if (fleetPage == null) fleetPage = new BattleNext_FleetLibraryPage(this::returnToBattleSelector, this::buttonPressed);
			fleetPage.attach(body);
		} else if (currentPage == Page.DRAFT) {
			if (draftEditor == null) draftEditor = new BattleNext_FleetDraftEditor(() -> requestPage(Page.FLEETS));
			BattleNext_FleetModel source = pendingDraft == null ? null : pendingDraft.getFleet();
			boolean clone = pendingDraft != null && "clone".equals(pendingDraft.getAction());
			draftEditor.attach(body, source, clone);
			pendingDraft = null;
		} else if (currentPage == Page.REPORTS) {
			if (reportPage == null) reportPage = new BattleNext_BattleReportPage(this::returnToBattleSelector);
			reportPage.attach(body);
		} else if (currentPage == Page.TOURNAMENTS) {
			if (tournamentPage == null) tournamentPage = new BattleNext_TournamentPage(this::returnToBattleSelector);
			tournamentPage.attach(body);
		} else if (currentPage == Page.RULES) {
			if (rulesPage == null) rulesPage = new BattleNext_RulesPage();
			rulesPage.attach(body);
		} else if (currentPage == Page.SETTINGS) {
			if (settingsPage == null) settingsPage = new BattleNext_SettingsPage();
			settingsPage.attach(body);
		}
	}

	private void requestPage(Page page) {
		if (page == currentPage) return;
		if (hasUnsavedChanges()) {
			pendingPage = page;
			pendingSourceSlot = null;
			pendingClose = false;
			showConfirmation();
			return;
		}
		currentPage = page;
		refreshState();
		showPage();
	}

	private void requestClose() {
		if (hasUnsavedChanges()) {
			pendingPage = null;
			pendingSourceSlot = null;
			pendingClose = true;
			showConfirmation();
			return;
		}
		closeAction.run();
	}

	private boolean hasUnsavedChanges() {
		if (currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null) return battleSelectorPage.hasUnsavedChanges();
		return currentPage == Page.DRAFT && draftEditor != null && draftEditor.hasUnsavedChanges();
	}

	private void requestSource(int slot) {

		battleSelectorPage.closeDropdown();
		if (slot == activeTournamentSlot) return;
		if (hasUnsavedChanges()) {
			pendingSourceSlot = slot;
			pendingPage = null;
			pendingClose = false;
			showConfirmation();
			return;
		}
		if (applySource(slot)) showPage();
	}

	private boolean applySource(int slot) {

		try {
			BattleNext_TournamentStore store = new BattleNext_TournamentStore();
			store.reload();
			if (slot >= 0 && store.get(slot) == null) {
				throw new IllegalArgumentException(text("tournament_missing"));
			}
			store.setActiveSlot(slot);
			sourceStatus = "";
			refreshState();
			return true;
		} catch (Exception exception) {
			sourceStatus = text("source_save_failed") + ": " + exception.getMessage();
			if (statusLabel != null) statusLabel.setText(sourceStatus);
			return false;
		}
	}

	private void showConfirmation() {
		if (confirmation != null) return;
		float width = Math.min(520f, panel.getPosition().getWidth() - 40f);
		confirmation = Global.getSettings().createCustom(width, 160f, new ConfirmationPlugin(width));
		TooltipMakerAPI dialog = confirmation.createUIElement(width, 160f, false);
		BattleNext_UiTheme.configure(dialog);
		dialog.addTitle(text("unsaved_title"));
		dialog.addPara(text("unsaved_message"), 12f, BattleNext_UiTheme.TEXT);
		confirmationStatus = dialog.addPara("", 6f, BattleNext_UiTheme.MUTED);
		String[] actions = {"save", "discard", "cancel"};
		for (int index = 0; index < actions.length; index++) {
			ButtonAPI button = dialog.addButton(text("unsaved_" + actions[index]), "confirm_" + actions[index],
					BattleNext_UiTheme.TEXT, BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL, 145f, 30f, 0f);
			button.getPosition().inBL(22f + index * 165f, 12f);
			button.setMouseOverSound("BUtil_button_in");
			button.setButtonPressedSound("BUtil_button_down");
		}
		confirmation.addUIElement(dialog);
		panel.addComponent(confirmation).inMid();
	}

	private void handleConfirmation(Object action) {
		if ("confirm_cancel".equals(action)) {
			panel.removeComponent(confirmation);
			confirmation = null;
			pendingPage = null;
			pendingSourceSlot = null;
			pendingClose = false;
			return;
		}
		if (!"confirm_save".equals(action) && !"confirm_discard".equals(action)) return;
		if ("confirm_save".equals(action)) {
			if (currentPage == Page.BATTLE_SELECTOR && !battleSelectorPage.save()) {
				confirmationStatus.setText(battleSelectorPage.getStatusText());
				return;
			}
			if (currentPage == Page.DRAFT && !draftEditor.save()) {
				confirmationStatus.setText(draftEditor.getStatusText());
				return;
			}
		}
		if (pendingSourceSlot != null && !applySource(pendingSourceSlot)) {
			confirmationStatus.setText(sourceStatus);
			return;
		}
		if ("confirm_discard".equals(action) && currentPage == Page.BATTLE_SELECTOR && battleSelectorPage != null) {
			battleSelectorPage.discard();
		}
		panel.removeComponent(confirmation);
		confirmation = null;
		if (pendingSourceSlot != null) {
			pendingSourceSlot = null;
			showPage();
			return;
		}
		if (pendingClose) {
			pendingClose = false;
			closeAction.run();
			return;
		}
		currentPage = pendingPage;
		pendingPage = null;
		refreshState();
		showPage();
	}

	private void refreshState() {
		sourceStatus = "";
		pendingResult = System.getProperty("AI_Battles_Next.pendingResult") != null;
		if (pendingResult) {
			System.clearProperty("AI_Battles_Next.pendingResult");
		}
		try {
			BattleNext_TournamentStore store = new BattleNext_TournamentStore();
			store.reload();
			activeTournamentSlot = store.getActiveSlot();
		} catch (Exception exception) {
			activeTournamentSlot = -1;
			sourceStatus = text("load_failed") + ": " + exception.getMessage();
		}
		if (statusLabel != null) statusLabel.setText(statusText());
	}

	private String statusText() {
		if (!sourceStatus.isEmpty()) return sourceStatus;
		if (activeTournamentSlot >= 0) {
			return text("tournament_active") + ": " + activeTournamentSlot;
		}
		if (pendingResult) {
			return text("result_updated");
		}
		return text("ready");
	}

	private String text(String key) {
		return strings.get(key);
	}

	private class ConfirmationPlugin extends BaseCustomUIPanelPlugin {

		private final UIBorderObject border = new UIBorderObject(false, true);
		private final float borderX;
		private final float borderY;

		private ConfirmationPlugin(float width) {
			border.setSize(width + 16f, 176f);
			borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - 8f;
			borderY = (ShaderCore.getScreenHeight() - 160f) * 0.5f - 8f;
		}

		@Override
		public void renderBelow(float alphaMult) {
			border.setAlpha(alphaMult);
			border.render(borderX, borderY);
		}

		@Override
		public void buttonPressed(Object buttonId) {
			handleConfirmation(buttonId);
		}
	}
}
