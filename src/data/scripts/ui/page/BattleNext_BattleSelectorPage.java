package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_QuickBattleEditor;
import data.scripts.ui.BattleNext_UiTheme;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

public class BattleNext_BattleSelectorPage implements BattleNext_PageView {

	public static class SourceRequest {

		private final String id;

		public SourceRequest(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}
	}

	private enum Action {
		TOGGLE_SOURCE
	}

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private UIRect sourceButtonRect;
	private CustomPanelAPI dropdownPanel;

	private ButtonAPI sourceButton;
	private BattleNext_QuickBattleEditor quickBattleEditor;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_TournamentEngine tournamentEngine = new BattleNext_TournamentEngine();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RulesStore rulesStore = new BattleNext_RulesStore();
	private String selectedId = "";
	private String loadError = "";

	public BattleNext_BattleSelectorPage(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		closeSourceList();
		this.panel = panel;
		load();
		createContents();
	}

	@Override
	public void createContents() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] sections = root.splitVertically(88f);
		contentRect = sections[1];
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), 30f), panel,
				text("page_battle_selector"));

		float labelWidth = Math.min(175f, root.getWidth() * 0.25f);
		float buttonWidth = Math.min(420f, root.getWidth() * 0.45f);
		float groupX = (root.getWidth() - labelWidth - 12f - buttonWidth) * 0.5f;
		UIRect labelRect = new UIRect(groupX, 44f, labelWidth, 30f);
		TooltipMakerAPI sourceLabel = labelRect.beginTooltip(panel, false);
		sourceLabel.setParaOrbitronLarge();
		sourceLabel.addPara(text("battle_source"), 0f, BattleNext_UiTheme.ACCENT)
				.getPosition().inTL(0f, 5f);
		sourceLabel.setHeightSoFar(labelRect.getHeight());
		labelRect.addTooltip();
		sourceButtonRect = new UIRect(groupX + labelWidth + 12f, 44f, buttonWidth, 30f);
		sourceButton = BattleNext_PageLayout.fixedButton(panel, sourceButtonRect,
				sourceLabel(), Action.TOGGLE_SOURCE, CutStyle.ALL, false);
		sourceButton.setEnabled(tournamentStore.isLoaded());

		refreshContent();
	}

	@Override
	public void refreshContent() {
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, inputOwner).getPanel();
		quickBattleEditor = null;
		if (selectedId.isEmpty()) {
			quickBattleEditor = new BattleNext_QuickBattleEditor(backAction, inputOwner);
			quickBattleEditor.attach(contentPanel);
		} else {
			buildTournamentDetails(contentPanel);
		}
	}

	@Override
	public boolean hasUnsavedChanges() {
		return selectedId.isEmpty() && quickBattleEditor != null && quickBattleEditor.hasUnsavedChanges();
	}

	@Override
	public boolean save() {
		return quickBattleEditor == null || quickBattleEditor.save();
	}

	@Override
	public void discard() {
		if (quickBattleEditor != null) quickBattleEditor.discard();
	}

	public void closeDropdown() {
		closeSourceList();
	}

	public void refreshRowStatuses() {
		if (quickBattleEditor != null) quickBattleEditor.refreshRowStatuses();
	}

	@Override
	public String getStatusText() {
		return quickBattleEditor == null ? loadError : quickBattleEditor.getStatusText();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {

		if (dropdownPanel == null) return false;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				closeSourceList();
				event.consume();
				return true;
			}
			if (!event.isMouseDownEvent()) continue;
			float scale = Global.getSettings().getScreenScaleMult();
			float x = Mouse.getX() / scale;
			float y = Mouse.getY() / scale;
			if (!contains(dropdownPanel, x, y) && !contains(sourceButton, x, y)) {
				closeSourceList();
			}
		}
		return false;
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId == Action.TOGGLE_SOURCE) {
			if (dropdownPanel == null) openSourceList();
			else closeSourceList();
			return;
		}
		if ("tournament:start".equals(buttonId)) {
			startTournament();
		} else if (selectedId.isEmpty() && quickBattleEditor != null) {
			quickBattleEditor.buttonPressed(buttonId);
		}
	}

	private boolean contains(UIComponentAPI component, float x, float y) {
		PositionAPI position = component.getPosition();
		return x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	private void load() {
		loadError = "";
		try {
			tournamentStore.reload();
			selectedId = tournamentStore.getActiveId();
		} catch (Exception exception) {
			loadError = text("load_failed") + ": " + exception.getMessage();
		}
		try {
			rulesStore.reload();
		} catch (Exception exception) {
			loadError += (loadError.isEmpty() ? "" : " | ") + text("load_failed") + ": " + exception.getMessage();
		}
		try {
			fleetStore.reload();
		} catch (Exception exception) {
			loadError += (loadError.isEmpty() ? "" : " | ") + text("load_failed") + ": " + exception.getMessage();
		}
	}

	private String sourceLabel() {
		if (selectedId.isEmpty()) return text("quick_battle");
		BattleNext_Tournament tournament = tournamentStore.get(selectedId);
		if (tournament == null) return text("tournament_missing") + " [" + selectedId + "]";
		return tournament.getName() + " [" + selectedId + "]";
	}

	private void openSourceList() {

		if (panel == null || dropdownPanel != null || !tournamentStore.isLoaded()) return;
		float width = sourceButton.getPosition().getWidth();
		int optionCount = tournamentStore.getAll().size() + 1;
		float dropdownY = sourceButtonRect.getY() + sourceButtonRect.getHeight() + 2f;
		float visibleHeight = Math.min(Math.min(240f, optionCount * 35f),
				panel.getPosition().getHeight() - dropdownY - 8f);
		dropdownPanel = panel.createCustomPanel(width, visibleHeight, inputOwner);
		TooltipMakerAPI options = dropdownPanel.createUIElement(width - 5f, visibleHeight, true);
		options.setButtonFontOrbitron20();
		BattleNext_PageLayout.button(options, text("quick_battle"), new SourceRequest(""), width - 15f);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " [" + tournament.getId() + "]";
			BattleNext_PageLayout.button(options, label, new SourceRequest(tournament.getId()), width - 15f);
		}
		dropdownPanel.addUIElement(options).inTL(0f, 0f);
		panel.addComponent(dropdownPanel).inTL(sourceButtonRect.getX(), dropdownY);
	}

	private void closeSourceList() {
		if (panel != null && dropdownPanel != null) panel.removeComponent(dropdownPanel);
		dropdownPanel = null;
	}

	private void buildTournamentDetails(CustomPanelAPI detailPanel) {

		BattleNext_Tournament tournament = tournamentStore.get(selectedId);
		if (tournament == null) {
			UIRect messageRect = new UIRect(0f, 0f, detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight());
			TooltipMakerAPI message = messageRect.beginTooltip(detailPanel, false);
			BattleNext_UiTheme.configure(message);
			message.addTitle(text("tournament_missing"));
			message.addPara(text("tournament_missing_detail"), 8f, BattleNext_UiTheme.MUTED);
			messageRect.addTooltip();
			return;
		}
		UIRect[] rows = new UIRect(0f, 0f, detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight())
				.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT + 10f);
		BattleNext_PageLayout.heading(rows[0], detailPanel, tournament.getName());
		float listWidth = BattleNext_PageLayout.listWidth(detailPanel);
		UIRect[] columns = rows[1].splitHorizontally(listWidth + 10f);

		listRectSetup(columns[0], detailPanel, tournament);
		scheduleSetup(columns[1], detailPanel, tournament);
	}

	private void listRectSetup(UIRect rect, CustomPanelAPI detailPanel, BattleNext_Tournament tournament) {
		rect.removeLatestSubPanel();
		CustomPanelAPI summaryPanel = rect.createSubPanel(detailPanel, null).getPanel();
		TooltipMakerAPI summary = summaryPanel.createUIElement(rect.getWidth(), rect.getHeight(), true);
		if (!loadError.isEmpty()) summary.addPara(loadError, 0f, BattleNext_UiTheme.MUTED);
		summary.addTitle(text("tournament_summary"));
		summary.addPara(text("status") + ": " + tournamentStatus(tournament), 8f, BattleNext_UiTheme.TEXT);
		summary.addPara(text("tournament_format") + ": " + tournament.getBracketType()
				+ " / " + tournament.getMatchFormat(), 6f, BattleNext_UiTheme.TEXT);
		summary.addPara(text("tournament_rules") + ": "
				+ (rulesStore.get(tournament.getRulesId()) == null
						? text("tournament_rules_unavailable") : text("tournament_rules_ready")),
				6f, BattleNext_UiTheme.TEXT);
		summary.addPara(text("teams"), 10f, BattleNext_UiTheme.ACCENT);
		for (String team : tournament.getTeams()) {
			summary.addPara(team, 5f, BattleNext_UiTheme.TEXT);
		}
		summaryPanel.addUIElement(summary).inTL(0f, 0f);
	}

	private void scheduleSetup(UIRect rect, CustomPanelAPI detailPanel, BattleNext_Tournament tournament) {
		rect.removeLatestSubPanel();
		CustomPanelAPI schedulePanel = rect.createSubPanel(detailPanel, detailPanel.getPlugin()).getPanel();
		TooltipMakerAPI schedule = schedulePanel.createUIElement(rect.getWidth(), rect.getHeight(), true);
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
		if (!tournament.isStarted()) {
			ButtonAPI start = BattleNext_PageLayout.button(schedule, text("tournament_start"), "tournament:start", 220f);
			start.setEnabled(tournamentStore.isLoaded() && fleetStore.isLoaded() && rulesStore.isLoaded());
		}
		schedulePanel.addUIElement(schedule).inTL(0f, 0f);
	}

	private void startTournament() {
		if (!tournamentStore.isLoaded() || !fleetStore.isLoaded() || !rulesStore.isLoaded()) {
			loadError = text("load_failed") + ": " + (!tournamentStore.isLoaded() ? tournamentStore.getLoadError()
					: !fleetStore.isLoaded() ? fleetStore.getLoadError() : rulesStore.getLoadError());
			refreshContent();
			return;
		}
		BattleNext_Tournament source = tournamentStore.get(selectedId);
		if (source == null || source.isStarted()) return;
		if (!List.of("single_elim", "double_elim", "round_robin", "kof").contains(source.getBracketType())
				|| !List.of("BO1", "BO3", "BO5").contains(source.getMatchFormat())
				|| source.getMapId() < -1 || source.getMapId() > 11) {
			loadError = text("invalid_tournament_format");
			refreshContent();
			return;
		}
		if (source.getTeams().size() < 2 || source.getTeams().size() > 32) {
			loadError = text("tournament_need_fleets");
			refreshContent();
			return;
		}
		BattleNext_Tournament tournament;
		try {
			tournament = BattleNext_Tournament.fromJson(source.toJson());
		} catch (Exception exception) {
			loadError = exception.getMessage();
			refreshContent();
			return;
		}
		if (rulesStore.get(tournament.getRulesId()) == null) {
			loadError = text("tournament_rules_unavailable");
			refreshContent();
			return;
		}
		for (String team : tournament.getTeams()) {
			if (fleetStore.get(team) == null) {
				loadError = text("invalid_player") + ": " + team;
				refreshContent();
				return;
			}
		}
		try {
			tournamentEngine.start(tournament);
			tournamentStore.save(tournament);
			tournamentStore.setActiveId(tournament.getId());
			loadError = text("tournament_started");
		} catch (Exception exception) {
			loadError = exception.getMessage();
		}
		refreshContent();
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
