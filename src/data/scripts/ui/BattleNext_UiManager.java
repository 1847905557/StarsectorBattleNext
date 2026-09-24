package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.input.Keyboard;

import data.scripts.match.BattleNext_MatchListProvider;
import data.scripts.fleet.BattleNext_FleetModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BattleNext_UiManager extends BaseCustomUIPanelPlugin {

	private enum Page {
		MAIN("BattleNext_ui_page_main"), QUICK("BattleNext_ui_page_quick"), FLEETS("BattleNext_ui_page_fleets"), DRAFT("BattleNext_ui_fleet_editor"),
		TOURNAMENTS("BattleNext_ui_page_tournaments"), RULES("BattleNext_ui_page_rules"),
		SETTINGS("BattleNext_ui_page_settings");

		private final String titleKey;

		Page(String titleKey) {
			this.titleKey = titleKey;
		}
	}

	private final Runnable closeAction;
	private final Deque<Page> pageStack = new ArrayDeque<>();
	private final List<Object> actions = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI content;
	private Page currentPage = Page.MAIN;
	private int selectedAction;
	private int activeTournamentSlot = -1;
	private boolean pendingResult;
	private BattleNext_QuickBattleEditor quickEditor;
	private BattleNext_FleetLibraryPage fleetPage;
	private BattleNext_FleetDraftEditor draftEditor;
	private BattleNext_FleetLibraryPage.DraftRequest pendingDraft;

	public BattleNext_UiManager(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		refreshState();
		rebuild();
	}

	@Override
	public void positionChanged(PositionAPI position) {
	}

	@Override
	public void render(float alphaMult) {
	}

	@Override
	public void advance(float amount) {
		if (currentPage == Page.QUICK && quickEditor != null) {
			quickEditor.advance();
		}
		if (currentPage == Page.FLEETS && fleetPage != null) {
			fleetPage.advance();
		}
		if (currentPage == Page.DRAFT && draftEditor != null) {
			draftEditor.advance();
		}
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		if (currentPage == Page.QUICK && quickEditor != null) {
			quickEditor.processInput(events);
		}
		if (currentPage == Page.FLEETS && fleetPage != null) {
			fleetPage.processInput(events);
		}
		if (currentPage == Page.DRAFT && draftEditor != null) {
			draftEditor.processInput(events);
		}
		for (InputEventAPI event : events) {
			if (!event.isKeyboardEvent() || !event.isKeyDownEvent()) {
				continue;
			}
			if (event.getEventValue() == Keyboard.KEY_UP) {
				selectedAction = Math.max(0, selectedAction - 1);
				event.consume();
			} else if (event.getEventValue() == Keyboard.KEY_DOWN) {
				selectedAction = Math.min(Math.max(0, actions.size() - 1), selectedAction + 1);
				event.consume();
			} else if (event.getEventValue() == Keyboard.KEY_RETURN && !actions.isEmpty()) {
				buttonPressed(actions.get(selectedAction));
				event.consume();
			}
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (currentPage == Page.QUICK && quickEditor != null && !(buttonId instanceof Page)) {
			quickEditor.buttonPressed(buttonId);
		} else if (currentPage == Page.FLEETS && fleetPage != null && !(buttonId instanceof Page)) {
			fleetPage.buttonPressed(buttonId);
		} else if (currentPage == Page.DRAFT && draftEditor != null && !(buttonId instanceof Page)) {
			draftEditor.buttonPressed(buttonId);
		} else if (buttonId instanceof BattleNext_FleetLibraryPage.DraftRequest) {
			pendingDraft = (BattleNext_FleetLibraryPage.DraftRequest)buttonId;
			pageStack.push(currentPage);
			currentPage = Page.DRAFT;
			selectedAction = 0;
			if (draftEditor == null) draftEditor = new BattleNext_FleetDraftEditor(this::goBack);
			rebuild();
		} else if (buttonId instanceof Page) {
			pageStack.push(currentPage);
			currentPage = (Page) buttonId;
			selectedAction = 0;
			rebuild();
		} else if ("back".equals(buttonId)) {
			goBack();
		} else if ("close".equals(buttonId)) {
			closeAction.run();
		}
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		for (InputEventAPI event : events) {
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				if (!pageStack.isEmpty()) {
					goBack();
				} else {
					closeAction.run();
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

	private void goBack() {
		if (pageStack.isEmpty()) {
			closeAction.run();
			return;
		}
		currentPage = pageStack.pop();
		selectedAction = 0;
		rebuild();
	}

	private void rebuild() {
		if (panel == null) {
			return;
		}
		if (content != null) {
			panel.removeComponent(content);
		}
		content = panel.createUIElement(720f, 480f, false);
		BattleNext_UiTheme.configure(content);
		content.addTitle(text(currentPage.titleKey));
		content.addPara(statusText(), 12f, BattleNext_UiTheme.MUTED);
		actions.clear();
		if (currentPage == Page.MAIN) {
			addPageButton(Page.QUICK);
			addPageButton(Page.FLEETS);
			addPageButton(Page.TOURNAMENTS);
			addPageButton(Page.RULES);
			addPageButton(Page.SETTINGS);
		} else if (currentPage == Page.QUICK) {
			if (quickEditor == null) {
				quickEditor = new BattleNext_QuickBattleEditor(this::goBack);
			}
			quickEditor.attach(panel);
			return;
		} else if (currentPage == Page.FLEETS) {
			if (fleetPage == null) {
				fleetPage = new BattleNext_FleetLibraryPage(this::goBack, this::buttonPressed);
			}
			fleetPage.attach(panel);
			return;
		} else if (currentPage == Page.DRAFT) {
			if (draftEditor == null) draftEditor = new BattleNext_FleetDraftEditor(this::goBack);
			BattleNext_FleetModel source = pendingDraft == null ? null : pendingDraft.getFleet();
			boolean clone = pendingDraft != null && "clone".equals(pendingDraft.getAction());
			draftEditor.attach(panel, source, clone);
			pendingDraft = null;
			return;
		} else {
			content.addPara(text("BattleNext_ui_page_placeholder"), 12f, BattleNext_UiTheme.TEXT);
			addActionButton(text("BattleNext_ui_back"), "back");
		}
		addActionButton(text("BattleNext_ui_close"), "close");
		panel.addUIElement(content).inMid();
	}

	private void addPageButton(Page page) {
		actions.add(page);
		content.addButton(text(page.titleKey), page, BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 250f, 34f, 8f);
	}

	private void addActionButton(String label, Object action) {
		actions.add(action);
		content.addButton(label, action, BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 180f, 32f, 8f);
	}

	private void refreshState() {
		pendingResult = System.getProperty("AI_Battles_Next.pendingResult") != null;
		if (pendingResult) {
			System.clearProperty("AI_Battles_Next.pendingResult");
		}
		BattleNext_MatchListProvider provider = new BattleNext_MatchListProvider();
		provider.load();
		if (provider.getActiveTournament() != null) {
			activeTournamentSlot = provider.getTournamentStore().getActiveSlot();
		}
	}

	private String statusText() {
		if (activeTournamentSlot >= 0) {
			return text("BattleNext_ui_tournament_active") + ": " + activeTournamentSlot;
		}
		if (pendingResult) {
			return text("BattleNext_ui_result_updated");
		}
		return text("BattleNext_ui_ready");
	}

	private String text(String key) {
		try {
			return Global.getSettings().getString("AI_Battles_Next", key);
		} catch (RuntimeException exception) {
			return key;
		}
	}
}
