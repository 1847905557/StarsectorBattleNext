package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.view.BattleNext_TournamentView;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BattleNext_TournamentDisplay {

	public record Details(Map<String, String> fleetNames, boolean ruleAvailable, BattleNext_Match currentMatch,
			String loadError, List<String> warnings, boolean startEnabled, boolean authorizeAvailable) {
		public Details {
			fleetNames = Map.copyOf(fleetNames);
			warnings = List.copyOf(warnings);
		}
	}

	private static final float TITLE_GAP = 10f;
	private static final float CONTENT_WIDTH_RESERVE = 12f;
	private static final float ACTION_BUTTON_WIDTH = 220f;
	private static final float DETAIL_GAP = 6f;
	private static final float SECTION_GAP = 10f;
	private static final float FLEET_GAP = 5f;
	private static final float MATCH_GAP = 5f;
	private static final float WARNING_GAP = 4f;

	private final BattleNext_UiInputRouter inputRouter;
	private final BattleNext_TournamentModel tournament;
	private final Details details;
	private final Consumer<Object> onAction;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private BattleNext_TournamentView chart;

	public BattleNext_TournamentDisplay(BattleNext_UiInputRouter inputRouter,
			BattleNext_TournamentModel tournament, Details details, Consumer<Object> onAction) {
		this.inputRouter = inputRouter;
		this.tournament = tournament;
		this.details = details;
		this.onAction = onAction;
	}

	public void attach(CustomPanelAPI panel) {

		close();
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] rows = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT + TITLE_GAP);
		BattleNext_PageLayout.heading(rows[0], panel, tournament.getName());
		CustomPanelAPI viewport = rows[1].createSubPanel(panel, new ActionPlugin()).getPanel();
		TooltipMakerAPI content = viewport.createUIElement(rows[1].getWidth(), rows[1].getHeight(), true);
		BattleNext_UiTheme.configure(content);
		content.setBgAlpha(0f);
		chart = BattleNext_TournamentView.createView(tournament, details.fleetNames());
		float availableWidth = Math.max(1f, rows[1].getWidth() - CONTENT_WIDTH_RESERVE);
		CustomPanelAPI canvas = chart.attach(viewport, content, viewport.getPosition(), inputRouter, availableWidth);
		addSummary(viewport, content, availableWidth, canvas.getPosition().getWidth());
		viewport.addUIElement(content).inTL(0f, 0f);
	}

	public void handleGlobalInput(List<InputEventAPI> events) {
		if (chart != null) {
			chart.handleInput(events);
		}
	}

	public void close() {
		if (chart != null) {
			chart.close();
			chart = null;
		}
	}

	private void addSummary(CustomPanelAPI owner, TooltipMakerAPI content, float availableWidth, float width) {

		float columnWidth = Math.max(1f, (width - BattleNext_PageLayout.EDITOR_COLUMN_GAP) * 0.5f);
		CustomPanelAPI row = owner.createCustomPanel(availableWidth, 1f, null);
		CustomPanelAPI summary = row.createCustomPanel(width, 1f, owner.getPlugin());
		TooltipMakerAPI title = summary.createUIElement(columnWidth, 0f, false);
		BattleNext_UiTheme.configure(title);
		title.setBgAlpha(0f);
		title.addTitle(text("tournament_summary"));
		TooltipMakerAPI overview = summary.createUIElement(columnWidth, 0f, false);
		BattleNext_UiTheme.configure(overview);
		overview.setBgAlpha(0f);
		addOverview(overview, columnWidth);
		TooltipMakerAPI fleets = summary.createUIElement(columnWidth, 0f, false);
		BattleNext_UiTheme.configure(fleets);
		fleets.setBgAlpha(0f);
		TooltipMakerAPI fleetTitle = summary.createUIElement(columnWidth, 0f, false);
		BattleNext_UiTheme.configure(fleetTitle);
		fleetTitle.setBgAlpha(0f);
		fleetTitle.addTitle(text("fleet_ids"));
		for (String fleetId : tournament.getFleetIds()) {
			fleets.addPara(fleetLabel(fleetId), fleetId.equals(tournament.getFleetIds().get(0)) ? 0f : FLEET_GAP);
		}

		float titleHeight = title.getHeightSoFar();
		float fleetTitleHeight = fleetTitle.getHeightSoFar();
		float columnsTop = Math.max(titleHeight, fleetTitleHeight) + SECTION_GAP;
		float height = columnsTop + Math.max(overview.getHeightSoFar(), fleets.getHeightSoFar());
		summary.getPosition().setSize(width, height);
		title.getPosition().setSize(columnWidth, titleHeight);
		fleetTitle.getPosition().setSize(columnWidth, fleetTitleHeight);
		overview.getPosition().setSize(columnWidth, overview.getHeightSoFar());
		fleets.getPosition().setSize(columnWidth, fleets.getHeightSoFar());
		summary.addUIElement(title).inTL(0f, 0f);
		summary.addUIElement(fleetTitle).inTL(columnWidth + BattleNext_PageLayout.EDITOR_COLUMN_GAP, 0f);
		summary.addUIElement(overview).inTL(0f, columnsTop);
		summary.addUIElement(fleets).inTL(columnWidth + BattleNext_PageLayout.EDITOR_COLUMN_GAP, columnsTop);
		summary.getPosition().setSize(width, height);
		row.addComponent(summary).inTL((availableWidth - width) * 0.5f, 0f);
		row.getPosition().setSize(availableWidth, height);
		content.addCustom(row, SECTION_GAP);
	}

	private void addOverview(TooltipMakerAPI content, float width) {

		content.addPara(text("status") + ": " + tournamentStatus(), 0f);
		content.addPara(text("tournament_format") + ": " + bracketLabel()
				+ " / " + strings.format("bo", tournament.getBoCount()), DETAIL_GAP);
		content.addPara(strings.format("chart_finished_matches", completedMatches(tournament),
				tournament.getPlayableMatchTotal()), DETAIL_GAP);
		if (tournament.isCompleted()) {
			content.addPara(text("winner") + ": " + fleetLabel(tournament.getChampionFleetId()), DETAIL_GAP);
		}
		content.addPara(text("tournament_rules") + ": "
				+ (tournament.getRuleId().isEmpty() ? text("rules_unbound")
						: text(details.ruleAvailable() ? "tournament_rules_ready" : "tournament_rules_unavailable")), DETAIL_GAP);
		content.addPara(text("battle_map_short") + ": "
				+ (tournament.getMapId() < 0 ? text("chart_random_map") : tournament.getMapId())
				+ " / " + text("battle_map_scale_short") + ": 1", DETAIL_GAP);
		if (!details.loadError().isEmpty()) {
			content.addPara(details.loadError(), WARNING_GAP);
		}
		for (String error : chart.errors()) {
			content.addPara(text("chart_error") + ": " + error, WARNING_GAP);
		}
		BattleNext_Match next = details.currentMatch();
		if (next == null) {
			content.addPara(text("no_pending_match"), MATCH_GAP);
		} else {
			content.addPara(text("next_match") + ": " + next.getId() + " / " + fleetLabel(next.getPlayer())
					+ " vs " + fleetLabel(next.getEnemy()), MATCH_GAP);
		}
		for (String warning : details.warnings()) {
			content.addPara(warning, WARNING_GAP);
		}
		if (details.authorizeAvailable()) {
			BattleNext_PageLayout.button(content, text("rules_force_start"), "tournament:authorize",
					Math.min(ACTION_BUTTON_WIDTH, width));
		}
		if (!tournament.isStarted()) {
			ButtonAPI start = BattleNext_PageLayout.button(content, text("tournament_start"),
					"tournament:start", Math.min(ACTION_BUTTON_WIDTH, width));
			start.setEnabled(details.startEnabled());
		}
	}

	public static int completedMatches(BattleNext_TournamentModel tournament) {
		int completed = 0;
		for (BattleNext_Match match : tournament.getMatches()) {
			if (match.getEnemy().isEmpty()) continue;
			if (match.getStatus() != BattleNext_Match.MatchStatus.COMPLETE) continue;

			completed++;
		}
		return completed;
	}

	private String fleetLabel(String id) {
		String name = details.fleetNames().get(id);
		if (name == null) {
			return text("invalid_player") + " [" + id + "]";
		}
		return name + " [" + id + "]";
	}

	private String tournamentStatus() {
		if (tournament.isCompleted()) return text("tournament_completed");
		if (tournament.isStarted()) return text("tournament_in_progress");

		return text("tournament_not_started");
	}

	private String bracketLabel() {
		if (tournament.getBracketType() == null) return text("chart_unknown_bracket");

		return text("bracket_" + tournament.getBracketType().name().toLowerCase(java.util.Locale.ROOT));
	}

	private String text(String key) {
		return strings.get(key);
	}

	private class ActionPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			if (chart == null || !inputRouter.isEmpty()) return;

			if ("tournament:start".equals(buttonId) && !tournament.isStarted() && details.startEnabled()
					|| "tournament:authorize".equals(buttonId) && details.authorizeAvailable()) {
				onAction.accept(buttonId);
			}
		}
	}
}
