package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.match.BattleNext_Match;
import data.scripts.match.BattleNext_Round;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.util.BattleNext_I18nSection;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

public class BattleNext_BattleSelectorPage {

	public static class SourceRequest {

		private final int slot;

		public SourceRequest(int slot) {
			this.slot = slot;
		}

		public int getSlot() {
			return slot;
		}
	}

	private enum Action {
		TOGGLE_SOURCE
	}

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private CustomPanelAPI contentPanel;
	private CustomPanelAPI sourceListPanel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI sourceBar;
	private ButtonAPI sourceButton;
	private BattleNext_QuickBattleEditor quickBattleEditor;
	private BattleNext_TournamentStore tournamentStore;
	private BattleNext_RulesStore rulesStore;
	private int selectedSlot = -1;
	private String loadError = "";

	public BattleNext_BattleSelectorPage(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	public void attach(CustomPanelAPI panel) {

		if (this.panel != panel) {
			heading = null;
			sourceBar = null;
			contentPanel = null;
			sourceListPanel = null;
			quickBattleEditor = null;
		}
		this.panel = panel;
		load();
		rebuild();
	}

	public boolean hasUnsavedChanges() {
		return selectedSlot == -1 && quickBattleEditor != null && quickBattleEditor.hasUnsavedChanges();
	}

	public boolean save() {
		return quickBattleEditor == null || quickBattleEditor.save();
	}

	public void discard() {
		if (quickBattleEditor != null) quickBattleEditor.reload();
	}

	public void closeDropdown() {
		closeSourceList();
	}

	public String getStatusText() {
		return quickBattleEditor == null ? loadError : quickBattleEditor.getStatusText();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {

		if (sourceListPanel == null) return false;
		for (InputEventAPI event : events) {
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				closeSourceList();
				event.consume();
				return true;
			}
			if (!event.isMouseDownEvent()) continue;
			float scale = Global.getSettings().getScreenScaleMult();
			float x = Mouse.getX() / scale;
			float y = Mouse.getY() / scale;
			if (!contains(sourceListPanel, x, y) && !contains(sourceButton, x, y)) {
				closeSourceList();
			}
		}
		return false;
	}

	public void buttonPressed(Object buttonId) {
		if (buttonId == Action.TOGGLE_SOURCE) {
			if (sourceListPanel == null) openSourceList();
			else closeSourceList();
			return;
		}
		if (selectedSlot == -1 && quickBattleEditor != null) {
			quickBattleEditor.buttonPressed(buttonId);
		}
	}

	private boolean contains(com.fs.starfarer.api.ui.UIComponentAPI component, float x, float y) {
		com.fs.starfarer.api.ui.PositionAPI position = component.getPosition();
		return x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	private void load() {
		loadError = "";
		tournamentStore = new BattleNext_TournamentStore();
		rulesStore = new BattleNext_RulesStore();
		try {
			tournamentStore.reload();
			selectedSlot = tournamentStore.getActiveSlot();
		} catch (Exception exception) {
			selectedSlot = -1;
			loadError = text("load_failed") + ": " + exception.getMessage();
		}
		try {
			rulesStore.reload();
		} catch (Exception exception) {
			loadError = text("load_failed") + ": " + exception.getMessage();
		}
	}

	private void rebuild() {

		closeSourceList();
		if (heading != null) panel.removeComponent(heading);
		if (sourceBar != null) panel.removeComponent(sourceBar);
		if (contentPanel != null) panel.removeComponent(contentPanel);
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		heading = BattleNext_PageLayout.heading(panel, text("page_battle_selector"));
		sourceBar = panel.createUIElement(width, 30f, false);
		sourceBar.setButtonFontOrbitron20();
		sourceBar.addPara(text("battle_source"), 0f, BattleNext_UiTheme.ACCENT)
				.getPosition().inBL(0f, 8f);
		sourceButton = sourceBar.addButton(sourceLabel(), Action.TOGGLE_SOURCE,
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL,
				Math.min(420f, width - 200f), 30f, 0f);
		sourceButton.getPosition().inBL(185f, 0f);
		sourceButton.setMouseOverSound("BUtil_button_in");
		sourceButton.setButtonPressedSound("BUtil_button_down");
		sourceBar.setHeightSoFar(30f);
		panel.addUIElement(sourceBar).inTL(0f, 40f);

		contentPanel = Global.getSettings().createCustom(width, height - 84f, inputOwner);
		panel.addComponent(contentPanel).inTL(0f, 84f);
		quickBattleEditor = null;
		if (selectedSlot == -1) {
			quickBattleEditor = new BattleNext_QuickBattleEditor(backAction, inputOwner);
			quickBattleEditor.attach(contentPanel);
		} else {
			buildTournamentDetails();
		}
	}

	private String sourceLabel() {
		if (selectedSlot < 0) return text("quick_battle");
		BattleNext_Tournament tournament = tournamentStore.get(selectedSlot);
		if (tournament == null) return text("tournament_missing") + " #" + selectedSlot;
		return tournament.getName() + " (#" + selectedSlot + ")";
	}

	private void openSourceList() {

		if (panel == null || sourceListPanel != null) return;
		float width = sourceButton.getPosition().getWidth();
		int optionCount = tournamentStore.getAll().size() + 1;
		float visibleHeight = Math.min(240f, optionCount * 35f);
		sourceListPanel = Global.getSettings().createCustom(width, visibleHeight, inputOwner);
		TooltipMakerAPI options = sourceListPanel.createUIElement(width - 5f, visibleHeight, true);
		options.setButtonFontOrbitron20();
		BattleNext_PageLayout.button(options, text("quick_battle"), new SourceRequest(-1), width - 15f);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " (#" + tournament.getSlot() + ")";
			BattleNext_PageLayout.button(options, label, new SourceRequest(tournament.getSlot()), width - 15f);
		}
		sourceListPanel.addUIElement(options).inTL(0f, 0f);
		panel.addComponent(sourceListPanel).inTL(185f, 72f);
	}

	private void closeSourceList() {
		if (sourceListPanel == null) return;
		panel.removeComponent(sourceListPanel);
		sourceListPanel = null;
	}

	private void buildTournamentDetails() {

		BattleNext_Tournament tournament = tournamentStore.get(selectedSlot);
		if (tournament == null) {
			TooltipMakerAPI message = BattleNext_PageLayout.column(contentPanel, 0f,
					contentPanel.getPosition().getWidth());
			message.addTitle(text("tournament_missing"));
			message.addPara(text("tournament_missing_detail"), 8f, BattleNext_UiTheme.MUTED);
			return;
		}
		BattleNext_PageLayout.heading(contentPanel, tournament.getName());
		float listWidth = BattleNext_PageLayout.listWidth(contentPanel);
		TooltipMakerAPI summary = BattleNext_PageLayout.column(contentPanel, 0f, listWidth);
		TooltipMakerAPI schedule = BattleNext_PageLayout.column(contentPanel, listWidth + 10f,
				contentPanel.getPosition().getWidth() - listWidth - 10f);
		if (!loadError.isEmpty()) summary.addPara(loadError, 0f, BattleNext_UiTheme.MUTED);
		summary.addTitle(text("tournament_summary"));
		summary.addPara(text("status") + ": " + tournamentStatus(tournament), 8f, BattleNext_UiTheme.TEXT);
		summary.addPara(text("tournament_format") + ": " + tournament.getBracketType()
				+ " / " + tournament.getMatchFormat(), 6f, BattleNext_UiTheme.TEXT);
		summary.addPara(text("tournament_rules") + ": " +
				(rulesStore.get(tournament.getRulesSlot()) == null
						? text("tournament_rules_unavailable") : text("tournament_rules_ready")),
				6f, BattleNext_UiTheme.TEXT);
		summary.addPara(text("teams"), 10f, BattleNext_UiTheme.ACCENT);
		for (String team : tournament.getTeams()) {
			summary.addPara(team, 5f, BattleNext_UiTheme.TEXT);
		}
		BattleNext_Match next = nextMatch(tournament);
		schedule.addTitle(text("next_match"));
		if (next == null) {
			schedule.addPara(text("no_pending_match"), 5f, BattleNext_UiTheme.MUTED);
		} else {
			schedule.addPara(next.getId() + ": " + next.getPlayer() + " / " + next.getEnemy(),
					5f, BattleNext_UiTheme.TEXT);
		}
		schedule.addPara(text("matches"), 12f, BattleNext_UiTheme.ACCENT);
		for (BattleNext_Match match : tournament.getMatches()) {
			schedule.addPara(text("match") + " " + match.getId() + ": " + match.getPlayer()
					+ " / " + match.getEnemy() + " [" + match.getStatus() + "]",
					8f, BattleNext_UiTheme.TEXT);
			for (BattleNext_Round round : match.getRounds()) {
				schedule.addPara(text("round") + " " + round.getNumber() + " / "
						+ text("attempt") + " " + round.getAttempt() + " / "
						+ text("winner") + ": " + round.getWinner(), 4f, BattleNext_UiTheme.MUTED);
			}
		}
	}

	private BattleNext_Match nextMatch(BattleNext_Tournament tournament) {
		if (!tournament.isStarted() || tournament.isCompleted()) return null;
		BattleNext_Match pending = tournament.findMatch(tournament.getPendingMatchId());
		if (pending != null && "pending".equals(pending.getStatus())) return pending;
		for (BattleNext_Match match : tournament.getMatches()) {
			if ("pending".equals(match.getStatus()) && !match.getEnemy().isEmpty()) return match;
		}
		return null;
	}

	private String tournamentStatus(BattleNext_Tournament tournament) {
		if (tournament.isCompleted()) return text("tournament_completed");
		if (tournament.isStarted()) return text("tournament_in_progress");
		return text("tournament_not_started");
	}

	private String text(String key) {
		return strings.get(key);
	}
}
