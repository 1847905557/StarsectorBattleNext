package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.model.fleet.BattleNext_FleetStore;
import data.model.report.BattleNext_BattleReportStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import data.scripts.ui.widget.view.BattleNext_TournamentView;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BattleNext_TournamentDisplay extends BattleNext_PanelBasedWidget {

	public record Details(Map<String, String> fleetNames, boolean ruleAvailable, BattleNext_Match currentMatch,
			String loadError, List<String> warnings, Set<String> reportIds, BattleNext_BattleValidation.Result checkResult) {
		public Details(Map<String, String> fleetNames, boolean ruleAvailable, BattleNext_Match currentMatch,
				String loadError, List<String> warnings, Set<String> reportIds) {
			this(fleetNames, ruleAvailable, currentMatch, loadError, warnings, reportIds, null);
		}
		public Details(Map<String, String> fleetNames, boolean ruleAvailable, BattleNext_Match currentMatch,
				String loadError, List<String> warnings) {
			this(fleetNames, ruleAvailable, currentMatch, loadError, warnings, Set.of());
		}
		public Details {
			fleetNames = Map.copyOf(fleetNames);
			warnings = List.copyOf(warnings);
			reportIds = Set.copyOf(reportIds);
		}

		public Details withCheckResult(BattleNext_BattleValidation.Result result) {
			return new Details(fleetNames, ruleAvailable, currentMatch, loadError, warnings, reportIds, result);
		}
	}

	public record Action(String label, Object id, boolean enabled, List<String> reasons, String group) {
		public Action(String label, Object id, boolean enabled, List<String> reasons) {
			this(label, id, enabled, reasons, "");
		}
		public Action {
			reasons = List.copyOf(reasons);
		}
	}

	public record ViewState(float scroll, BattleNext_TournamentView.ViewState chart) { }

	private static final float TITLE_GAP = 10f;
	private static final float CONTENT_WIDTH_RESERVE = 12f;
	private static final float ACTION_BUTTON_WIDTH = 180f;
	private static final float DETAIL_GAP = 6f;
	private static final float SECTION_GAP = 10f;
	private static final float FLEET_GAP = 5f;
	private static final float MATCH_GAP = 5f;
	private static final float WARNING_GAP = 4f;

	private BattleNext_TournamentModel tournament;
	private BattleNext_StartedTournament started;
	private Details details;
	private Supplier<List<String>> saveErrors = List::of;
	private final Consumer<Object> onAction;
	private List<Action> actions;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private BattleNext_TournamentView chart;
	private TooltipMakerAPI content;
	private TooltipMakerAPI headingContent;
	private LabelAPI headingLabel;
	private CustomPanelAPI viewport;
	private CustomPanelAPI canvas;
	private CustomPanelAPI summaryRow;
	private CustomPanelAPI summaryPanel;
	private CustomPanelAPI overlayHost;
	private BattleNext_FleetSourceProvider fleetSource;
	private Supplier<List<String>> templateFleetIds;
	private BiConsumer<Integer, String> onFleetSelected;
	private BattleNext_OptionPicker fleetPicker;
	private int selectedSlot;

	public BattleNext_TournamentDisplay(BattleNext_UiInputRouter inputRouter,
			BattleNext_TournamentModel tournament, BattleNext_StartedTournament started, Details details, List<Action> actions, Consumer<Object> onAction) {
		super(inputRouter);
		this.tournament = tournament;
		this.started = started;
		this.details = details;
		this.onAction = onAction;
		this.actions = List.copyOf(actions);
	}

	public static Details details(BattleNext_TournamentModel tournament, BattleNext_StartedTournament started, BattleNext_FleetStore fleets,
			BattleNext_RuleStore rules, String loadError, List<String> warnings) {
		return details(tournament, started, started == null ? List.of() : started.getFleetIds(), fleets, rules, loadError, warnings);
	}

	public static Details details(BattleNext_TournamentModel tournament, BattleNext_StartedTournament started, List<String> fleetIds,
			BattleNext_FleetStore fleets, BattleNext_RuleStore rules, String loadError, List<String> warnings) {
		Map<String, String> names = new LinkedHashMap<>();
		for (String id : fleetIds) {
			if (fleets.get(id) == null) continue;
			names.put(id, fleets.get(id).getName());
		}
		Set<String> reportIds = new LinkedHashSet<>();
		List<String> diagnostics = new ArrayList<>(warnings);
		if (started != null) {
			BattleNext_BattleReportStore reports = new BattleNext_BattleReportStore();
			try {
				reports.reload();
				reports.getAll().forEach(report -> reportIds.add(report.getId()));
			} catch (Exception exception) {
				diagnostics.add(exception.toString());
			}
		}
		return new Details(names, tournament != null && rules.isLoaded() && rules.get(tournament.getRuleId()) != null,
				(started == null ? null : started.getNextPendingMatch()), loadError, diagnostics, reportIds);
	}

	public void enableTemplateEditing(CustomPanelAPI overlayHost, BattleNext_FleetSourceProvider fleetSource,
			Supplier<List<String>> fleetIds, BiConsumer<Integer, String> onFleetSelected) {
		this.overlayHost = overlayHost;
		this.fleetSource = fleetSource;
		templateFleetIds = fleetIds;
		this.onFleetSelected = onFleetSelected;
	}

	public boolean isEditable() {
		return started == null && templateFleetIds != null;
	}

	public void setSaveErrors(Supplier<List<String>> source) {
		saveErrors = source;
	}

	public ViewState captureState() {
		return new ViewState(content.getExternalScroller().getYOffset(), chart.captureState());
	}

	public void restoreState(ViewState state) {
		chart.restoreState(state.chart());
		content.getExternalScroller().setYOffset(state.scroll());
	}

	public void update(BattleNext_TournamentModel model, BattleNext_StartedTournament started, Details details, List<Action> actions) {
		ViewState state = captureState();
		tournament = model;
		this.started = started;
		this.details = details;
		this.actions = List.copyOf(actions);
		refreshContent();
		restoreState(state);
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		buildDisplay(panel);
	}

	@Override
	protected void clearOverlays() {
		if (fleetPicker != null) {
			fleetPicker.closeOverlays();
		}
		if (chart != null) {
			chart.close();
		}
	}

	@Override
	protected void clearContent() {
		if (fleetPicker != null) {
			fleetPicker.close();
			fleetPicker = null;
		}
		if (chart != null) {
			chart.close();
		}
	}

	@Override
	protected void clearPageState() {
		chart = null;
	}

	private void buildDisplay(CustomPanelAPI panel) {

		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		float headingHeight = Math.min(root.getHeight(), BattleNext_PageLayout.HEADER_HEIGHT + TITLE_GAP);
		UIRect[] rows = {new UIRect(0f, 0f, root.getWidth(), headingHeight),
				new UIRect(0f, headingHeight, root.getWidth(), Math.max(1f, root.getHeight() - headingHeight))};
		CustomPanelAPI headingPanel = ownPanel(panel, rows[0].createSubPanel(panel, panel.getPlugin()).getPanel());
		headingContent = headingPanel.createUIElement(rows[0].getWidth(), rows[0].getHeight(), false);
		headingLabel = BattleNext_PageLayout.heading(rows[0], headingPanel, headingContent, displayName());
		viewport = ownPanel(panel, rows[1].createSubPanel(panel, new ActionPlugin()).getPanel());
		content = viewport.createUIElement(rows[1].getWidth(), rows[1].getHeight(), true);
		BattleNext_UiTheme.configure(content);
		content.setBgAlpha(0f);
		if (chart == null) {
			chart = BattleNext_TournamentView.createView(tournament, started, participants(), details.fleetNames());
		} else {
			chart.update(tournament, started, participants(), details.fleetNames());
		}
		chart.setReportIds(details.reportIds());
		chart.setEditable(isEditable(), this::selectFleet);
		float availableWidth = Math.max(1f, rows[1].getWidth() - CONTENT_WIDTH_RESERVE);
		canvas = chart.attach(viewport, content, viewport.getPosition(), inputRouter, availableWidth);
		if (isEditable()) {
			fleetPicker = new BattleNext_OptionPicker(inputRouter, overlayHost, fleetSource,
					BattleNext_OptionPicker.OptionSelectionMode.SINGLE, () -> {
						String id = templateFleetIds.get().get(selectedSlot);
						return fleetSource.getOptions().stream().anyMatch(option -> option.getId().equals(id)) ? List.of(id) : List.of();
					}, ids -> onFleetSelected.accept(selectedSlot, ids.isEmpty() ? "" : ids.get(0)));
		}
		addSummary(viewport, content, availableWidth, canvas.getPosition().getWidth());
		viewport.addUIElement(content).inTL(0f, 0f);
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		if (fleetPicker != null && fleetPicker.handleGlobalInput(events)) return true;
		if (chart != null) {
			chart.handleInput(events);
		}
		return false;
	}

	private void selectFleet(BattleNext_TournamentView.SlotHit hit) {
		selectedSlot = hit.slot();
		fleetPicker.openAt(canvas, () -> chart.slotAnchor(hit));
	}

	public void closeChart() {
		if (chart != null) {
			chart.close();
			chart = null;
		}
	}

	private void addSummary(CustomPanelAPI owner, TooltipMakerAPI content, float availableWidth, float width) {
		summaryRow = ownPanel(content, owner.createCustomPanel(availableWidth, 1f, null));
		buildSummary(summaryRow, availableWidth, width, owner.getPlugin());
		content.addCustom(summaryRow, SECTION_GAP);
	}

	private void buildSummary(CustomPanelAPI row, float availableWidth, float width, CustomUIPanelPlugin plugin) {

		float columnWidth = Math.max(1f, (width - BattleNext_PageLayout.EDITOR_COLUMN_GAP) * 0.5f);
		CustomPanelAPI summary = ownPanel(row, row.createCustomPanel(width, 1f, plugin));
		summaryPanel = summary;
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
		List<String> participantIds = participants().stream().filter(id -> !id.isEmpty()).toList();
		for (int index = 0; index < participantIds.size(); index++) {
			String fleetId = participantIds.get(index);
			float pad = index == 0 ? 0f : FLEET_GAP;
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
	}

	private void addOverview(TooltipMakerAPI content, float width) {

		content.addPara(strings.get("status") + ": " + tournamentStatus(), 0f);
		if (tournament == null) {
			content.addPara(tournamentStrings.get("missing_detail") + " " + started.getSourceTournamentId(), DETAIL_GAP);
		} else {
			content.addPara(tournamentStrings.get("format") + ": " + bracketLabel()
					+ " / " + battleStrings.format("best_of_format", tournament.getBoCount()), DETAIL_GAP);
			content.addPara(tournamentStrings.format("chart_finished_matches", completedMatches(started),
					started == null ? BattleNext_TournamentEngine.playableMatchTotal(tournament, participants()) : started.getPlayableMatchTotal(tournament)), DETAIL_GAP);
		}
		if ((started != null && started.isCompleted())) {
			String winnerId = started.getChampionFleetId();
			String winner = strings.get("winner") + ": " + fleetLabel(winnerId);
			content.addPara(winner, DETAIL_GAP);
			BattleNext_PageLayout.addIdTooltip(content, winner, winnerId);
		}
		if (tournament != null) {
			content.addPara(tournamentStrings.get("rules") + ": "
					+ (tournament.getRuleId().isEmpty() ? ruleStrings.get("none_assigned")
							: tournamentStrings.get(details.ruleAvailable() ? "rules_ready" : "rules_unavailable")), DETAIL_GAP);
			content.addPara(battleStrings.get("map_label") + ": "
					+ (tournament.getMapId() < 0 ? tournamentStrings.get("chart_random_map") : tournament.getMapId())
					+ " / " + battleStrings.get("map_scale_label") + ": 1", DETAIL_GAP);
		}
		if (!details.loadError().isEmpty()) {
			content.addPara(details.loadError(), WARNING_GAP);
			content.addTooltipTo(BattleNext_SaveErrors.tooltip(saveErrors), content.getPrev(), TooltipMakerAPI.TooltipLocation.ABOVE, true);
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
		if (details.checkResult() != null) {
			boolean passed = details.checkResult().failures().isEmpty();
			content.addPara(tournamentStrings.get(passed ? "check_passed" : "check_failed"),
					passed ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), DETAIL_GAP);
			for (BattleNext_BattleValidation.Failure failure : details.checkResult().failures()) {
				String name = details.fleetNames().get(failure.subject());
				String identity = name == null ? failure.subject() : name + " [" + failure.subject() + "]";
				content.addPara(identity + ": " + failure.message(), WARNING_GAP);
			}
		}
		for (int index = 0; index < actions.size();) {
			Action first = actions.get(index);
			int end = index + 1;
			while (!first.group().isEmpty() && end < actions.size() && first.group().equals(actions.get(end).group())) {
				end++;
			}
			int count = end - index;
			float buttonWidth = Math.min(ACTION_BUTTON_WIDTH, Math.max(1f, (width - (count - 1) * BattleNext_PageLayout.EDITOR_BUTTON_GAP) / count));
			float top = content.getHeightSoFar() + BattleNext_PageLayout.EDITOR_BUTTON_GAP;
			for (int offset = 0; index < end; index++, offset++) {
				Action action = actions.get(index);
				ButtonAPI button = BattleNext_PageLayout.button(content, action.label(), action.id(), buttonWidth);
				button.getPosition().inTL(offset * (buttonWidth + BattleNext_PageLayout.EDITOR_BUTTON_GAP), top);
				button.setEnabled(action.enabled());
				if (!action.reasons().isEmpty()) {
					content.addTooltipToPrevious(new BaseTooltipCreator() {
						@Override
						public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
							for (String reason : action.reasons()) {
								tooltip.addPara(reason, WARNING_GAP);
							}
						}
					}, TooltipMakerAPI.TooltipLocation.RIGHT);
				}
			}
			content.setHeightSoFar(top + BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT);
		}
	}

	public static int completedMatches(BattleNext_StartedTournament started) {
		int completed = 0;
		if (started == null) return 0;
		for (BattleNext_Match match : started.getMatches()) {
			if (match.getEnemy().isEmpty()) continue;
			if (match.getStatus() != BattleNext_Match.MatchStatus.COMPLETE) continue;

			completed++;
		}
		return completed;
	}

	private List<String> participants() {
		if (started != null) return started.getFleetIds();
		if (templateFleetIds != null) return templateFleetIds.get();
		return Collections.nCopies(tournament.getMaxFleet(), "");
	}

	private String fleetLabel(String id) {
		if (id.isEmpty()) return tournamentStrings.get(started == null ? "select_fleet" : "stage_bye");
		String name = details.fleetNames().get(id);
		if (name == null) {
			return tournamentStrings.get("invalid_participant_fleet") + " [" + id + "]";
		}
		return name;
	}

	private String displayName() {
		return started == null ? tournament.getName() : started.getName(tournament);
	}

	private String tournamentStatus() {
		if ((started != null && started.isCompleted())) return tournamentStrings.get("status_completed");
		if (started != null) return tournamentStrings.get("status_in_progress");

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

			for (Action action : actions) {
				if (!action.enabled() || !action.id().equals(buttonId)) continue;
				onAction.accept(buttonId);
				return;
			}
		}
	}
}
