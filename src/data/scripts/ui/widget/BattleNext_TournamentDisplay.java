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
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class BattleNext_TournamentDisplay extends BattleNext_PanelBasedWidget {

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

	private final BattleNext_TournamentModel tournament;
	private final Details details;
	private final Consumer<Object> onAction;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private BattleNext_TournamentView chart;

	public BattleNext_TournamentDisplay(BattleNext_UiInputRouter inputRouter,
			BattleNext_TournamentModel tournament, Details details, Consumer<Object> onAction) {
		super(inputRouter);
		this.tournament = tournament;
		this.details = details;
		this.onAction = onAction;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		buildDisplay(panel);
	}

	@Override
	protected void clearOverlays() {
		closeChart();
	}

	private void buildDisplay(CustomPanelAPI panel) {

		closeChart();
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] rows = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT + TITLE_GAP);
		BattleNext_PageLayout.heading(rows[0], panel, tournament.getName());
		CustomPanelAPI viewport = ownPanel(panel, rows[1].createSubPanel(panel, new ActionPlugin()).getPanel());
		TooltipMakerAPI content = viewport.createUIElement(rows[1].getWidth(), rows[1].getHeight(), true);
		BattleNext_UiTheme.configure(content);
		content.setBgAlpha(0f);
		chart = BattleNext_TournamentView.createView(tournament, details.fleetNames());
		float availableWidth = Math.max(1f, rows[1].getWidth() - CONTENT_WIDTH_RESERVE);
		CustomPanelAPI canvas = chart.attach(viewport, content, viewport.getPosition(), inputRouter, availableWidth);
		addSummary(viewport, content, availableWidth, canvas.getPosition().getWidth());
		viewport.addUIElement(content).inTL(0f, 0f);
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		if (chart != null) {
			chart.handleInput(events);
		}
		return false;
	}

	public void closeChart() {
		if (chart != null) {
			chart.close();
			chart = null;
		}
	}

	private void addSummary(CustomPanelAPI owner, TooltipMakerAPI content, float availableWidth, float width) {

		float columnWidth = Math.max(1f, (width - BattleNext_PageLayout.EDITOR_COLUMN_GAP) * 0.5f);
		CustomPanelAPI row = ownPanel(content, owner.createCustomPanel(availableWidth, 1f, null));
		CustomPanelAPI summary = row.createCustomPanel(width, 1f, owner.getPlugin());
		TooltipMakerAPI title = summary.createUIElement(columnWidth, 0f, false);
		BattleNext_UiTheme.configure(title);
		title.setBgAlpha(0f);
		title.addTitle(tournamentStrings.get("summary"));
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
		fleetTitle.addTitle(tournamentStrings.get("participant_fleets"));
		for (String fleetId : tournament.getFleetIds()) {
			float pad = fleetId.equals(tournament.getFleetIds().get(0)) ? 0f : FLEET_GAP;
			String name = fleetLabel(fleetId);
			fleets.addPara(name, pad);
			BattleNext_PageLayout.addIdTooltip(fleets, name, fleetId);
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

		content.addPara(strings.get("status") + ": " + tournamentStatus(), 0f);
		content.addPara(tournamentStrings.get("format") + ": " + bracketLabel()
				+ " / " + battleStrings.format("best_of_format", tournament.getBoCount()), DETAIL_GAP);
		content.addPara(tournamentStrings.format("chart_finished_matches", completedMatches(tournament),
				tournament.getPlayableMatchTotal()), DETAIL_GAP);
		if (tournament.isCompleted()) {
			String winnerId = tournament.getChampionFleetId();
			String winner = strings.get("winner") + ": " + fleetLabel(winnerId);
			content.addPara(winner, DETAIL_GAP);
			BattleNext_PageLayout.addIdTooltip(content, winner, winnerId);
		}
		content.addPara(tournamentStrings.get("rules") + ": "
				+ (tournament.getRuleId().isEmpty() ? ruleStrings.get("none_assigned")
						: tournamentStrings.get(details.ruleAvailable() ? "rules_ready" : "rules_unavailable")), DETAIL_GAP);
		content.addPara(battleStrings.get("map_label") + ": "
				+ (tournament.getMapId() < 0 ? tournamentStrings.get("chart_random_map") : tournament.getMapId())
				+ " / " + battleStrings.get("map_scale_label") + ": 1", DETAIL_GAP);
		if (!details.loadError().isEmpty()) {
			content.addPara(details.loadError(), WARNING_GAP);
		}
		for (String error : chart.errors()) {
			content.addPara(tournamentStrings.get("chart_error") + ": " + error, WARNING_GAP);
		}
		BattleNext_Match next = details.currentMatch();
		if (next == null) {
			content.addPara(battleStrings.get("no_pending_match"), MATCH_GAP);
		} else {
			String match = battleStrings.get("next_match") + ": " + fleetLabel(next.getPlayer())
					+ " vs " + fleetLabel(next.getEnemy());
			content.addPara(match, MATCH_GAP);
			BattleNext_PageLayout.addMetadataTooltip(content, match,
					next.getId(), next.getPlayer(), next.getEnemy());
		}
		for (String warning : details.warnings()) {
			content.addPara(warning, WARNING_GAP);
		}
		if (details.authorizeAvailable()) {
			BattleNext_PageLayout.button(content, ruleStrings.get("force_start_match"), "tournament:authorize",
					Math.min(ACTION_BUTTON_WIDTH, width));
		}
		if (!tournament.isStarted()) {
			ButtonAPI start = BattleNext_PageLayout.button(content, tournamentStrings.get("start"),
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
			return tournamentStrings.get("invalid_participant_fleet") + " [" + id + "]";
		}
		return name;
	}

	private String tournamentStatus() {
		if (tournament.isCompleted()) return tournamentStrings.get("status_completed");
		if (tournament.isStarted()) return tournamentStrings.get("status_in_progress");

		return tournamentStrings.get("status_not_started");
	}

	private String bracketLabel() {
		if (tournament.getBracketType() == null) return tournamentStrings.get("chart_unknown_bracket");

		return tournamentStrings.get("bracket_" + tournament.getBracketType().name().toLowerCase(Locale.ROOT));
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
