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

import data.scripts.match.BattleNext_MatchListProvider;
import data.scripts.fleet.BattleNext_FleetModel;

import java.util.List;

public class BattleNext_UiManager extends BaseCustomUIPanelPlugin {

	private enum Page {
		QUICK("page_quick"), FLEETS("page_fleets"), REPORTS("page_reports"),
		TOURNAMENTS("page_tournaments"), RULES("page_rules"), SETTINGS("page_settings"),
		DRAFT("fleet_editor");

		private final String titleKey;

		Page(String titleKey) {
			this.titleKey = titleKey;
		}
	}

	private final Runnable closeAction;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("AI_Battles_Next", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private CustomPanelAPI body;
	private CustomPanelAPI confirmation;
	private LabelAPI statusLabel;
	private LabelAPI confirmationStatus;
	private final ButtonAPI[] tabButtons = new ButtonAPI[6];
	private Page currentPage = Page.QUICK;
	private Page pendingPage;
	private boolean pendingClose;
	private int activeTournamentSlot = -1;
	private boolean pendingResult;
	private BattleNext_QuickBattleEditor quickEditor;
	private BattleNext_FleetLibraryPage fleetPage;
	private BattleNext_FleetDraftEditor draftEditor;
	private BattleNext_FleetLibraryPage.DraftRequest pendingDraft;
	private BattleNext_BattleReportPage reportPage;
	private BattleNext_TournamentPage tournamentPage;

	public BattleNext_UiManager(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		buildShell();
	}

	public void open() {
		currentPage = Page.QUICK;
		pendingDraft = null;
		refreshState();
		if (quickEditor != null) {
			quickEditor.reload();
		}
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
		} else if ("close".equals(buttonId)) {
			requestClose();
		} else if (buttonId instanceof BattleNext_FleetLibraryPage.DraftRequest) {
			pendingDraft = (BattleNext_FleetLibraryPage.DraftRequest)buttonId;
			requestPage(Page.DRAFT);
		} else if (currentPage == Page.QUICK && quickEditor != null) {
			quickEditor.buttonPressed(buttonId);
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

	private void returnToQuick() {
		if (currentPage == Page.QUICK) {
			requestClose();
			return;
		}
		requestPage(Page.QUICK);
	}

	private void buildShell() {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		TooltipMakerAPI heading = panel.createUIElement(width, 32f, false);
		heading.addSectionHeading(text("page_main"), BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, 0f);
		panel.addUIElement(heading).inTMid(0f);

		TooltipMakerAPI tabs = panel.createUIElement(width - 16f, 28f, false);
		tabs.setButtonFontOrbitron20();
		float tabWidth = (width - 16f - 25f) / 6f;
		for (int index = 0; index < 6; index++) {
			Page page = Page.values()[index];
			ButtonAPI button = tabs.addButton(text(page.titleKey), page, BattleNext_UiTheme.TEXT,
					BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.TOP, tabWidth, 26f, 0f);
			button.getPosition().inBL(index * (tabWidth + 5f), 0f);
			button.setMouseOverSound("BUtil_button_in");
			button.setButtonPressedSound("BUtil_button_down");
			tabButtons[index] = button;
		}
		panel.addUIElement(tabs).inTL(8f, 40f);

		TooltipMakerAPI footer = panel.createUIElement(width, 32f, false);
		footer.setButtonFontOrbitron20();
		statusLabel = footer.addPara(statusText(), 0f, BattleNext_UiTheme.MUTED);
		statusLabel.getPosition().inBL(12f, 6f);
		ButtonAPI close = footer.addButton(text("close"), "close", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.BOTTOM, 180f, 30f, 0f);
		close.getPosition().inBMid(0f);
		close.setMouseOverSound("BUtil_button_in");
		close.setButtonPressedSound("BUtil_button_down");
		panel.addUIElement(footer).inBMid(8f);
	}

	private void showPage() {
		if (panel == null) return;
		for (int index = 0; index < tabButtons.length; index++) {
			tabButtons[index].setEnabled(Page.values()[index] != currentPage);
		}
		if (body != null) panel.removeComponent(body);
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		body = Global.getSettings().createCustom(width, height - 128f, this);
		panel.addComponent(body).inBL(0f, 46f);
		if (currentPage == Page.QUICK) {
			if (quickEditor == null) quickEditor = new BattleNext_QuickBattleEditor(this::returnToQuick);
			quickEditor.attach(body);
		} else if (currentPage == Page.FLEETS) {
			if (fleetPage == null) fleetPage = new BattleNext_FleetLibraryPage(this::returnToQuick, this::buttonPressed);
			fleetPage.attach(body);
		} else if (currentPage == Page.DRAFT) {
			if (draftEditor == null) draftEditor = new BattleNext_FleetDraftEditor(() -> requestPage(Page.FLEETS));
			BattleNext_FleetModel source = pendingDraft == null ? null : pendingDraft.getFleet();
			boolean clone = pendingDraft != null && "clone".equals(pendingDraft.getAction());
			draftEditor.attach(body, source, clone);
			pendingDraft = null;
		} else if (currentPage == Page.REPORTS) {
			if (reportPage == null) reportPage = new BattleNext_BattleReportPage(this::returnToQuick);
			reportPage.attach(body);
		} else if (currentPage == Page.TOURNAMENTS) {
			if (tournamentPage == null) tournamentPage = new BattleNext_TournamentPage(this::returnToQuick);
			tournamentPage.attach(body);
		} else {
			TooltipMakerAPI placeholder = body.createUIElement(width - 20f, height - 138f, true);
			BattleNext_UiTheme.configure(placeholder);
			placeholder.addTitle(text(currentPage.titleKey));
			placeholder.addPara(text("page_placeholder"), 12f, BattleNext_UiTheme.TEXT);
			body.addUIElement(placeholder).inTL(10f, 0f);
		}
	}

	private void requestPage(Page page) {
		if (page == currentPage) return;
		if (hasUnsavedChanges()) {
			pendingPage = page;
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
			pendingClose = true;
			showConfirmation();
			return;
		}
		closeAction.run();
	}

	private boolean hasUnsavedChanges() {
		if (currentPage == Page.QUICK && quickEditor != null) return quickEditor.hasUnsavedChanges();
		return currentPage == Page.DRAFT && draftEditor != null && draftEditor.hasUnsavedChanges();
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
			pendingClose = false;
			return;
		}
		if (!"confirm_save".equals(action) && !"confirm_discard".equals(action)) return;
		if ("confirm_save".equals(action)) {
			if (currentPage == Page.QUICK && !quickEditor.save()) {
				confirmationStatus.setText(quickEditor.getStatusText());
				return;
			}
			if (currentPage == Page.DRAFT && !draftEditor.save()) {
				confirmationStatus.setText(draftEditor.getStatusText());
				return;
			}
		}
		panel.removeComponent(confirmation);
		confirmation = null;
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
		pendingResult = System.getProperty("AI_Battles_Next.pendingResult") != null;
		if (pendingResult) {
			System.clearProperty("AI_Battles_Next.pendingResult");
		}
		BattleNext_MatchListProvider provider = new BattleNext_MatchListProvider();
		provider.load();
		activeTournamentSlot = -1;
		if (provider.getActiveTournament() != null) {
			activeTournamentSlot = provider.getTournamentStore().getActiveSlot();
		}
		if (statusLabel != null) statusLabel.setText(statusText());
	}

	private String statusText() {
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
