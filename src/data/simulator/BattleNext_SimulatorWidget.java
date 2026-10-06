package data.simulator;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tools.BattleNext_Logger;
import org.json.JSONException;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.Locale;
import data.simulator.BattleNext_SimulatorService.Status;

public class BattleNext_SimulatorWidget extends BattleNext_PanelBasedWidget {

	private static final float BUTTON_MAX_WIDTH = 360f;
	private final BooleanSupplier unlocked;
	private final BattleNext_SimulatorBrowser browser;
	private final BattleNext_BattleSourceProvider sources;
	private final CustomPanelAPI overlayHost;
	private final BattleNext_I18nSection settingsStrings = BattleNext_I18nSection.getInstance("Settings", "BattleNext_settings_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private BattleNext_BattleSourceField sourceField;
	private BattleNext_QuickBattleDisplay quickBattleDisplay;
	private BattleNext_TournamentDisplay tournamentDisplay;
	private CustomPanelAPI contentPanel;
	private CustomPanelAPI bodyPanel;
	private UIRect bodyRect;
	private TooltipMakerAPI statusContent;
	private LabelAPI statusLabel;
	private Status displayedStatus;
	private String displayedError;
	private long tournamentVersion = -1L;
	private String tournamentTaskId = "";

	public BattleNext_SimulatorWidget(BattleNext_UiInputRouter inputRouter, CustomPanelAPI overlayHost,
			BooleanSupplier unlocked, BattleNext_SimulatorBrowser browser, BattleNext_BattleSourceProvider sources) {
		super(inputRouter);
		this.unlocked = unlocked;
		this.overlayHost = overlayHost;
		this.browser = browser;
		this.sources = sources;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {

		float areaWidth = panel.getPosition().getWidth();
		float areaHeight = panel.getPosition().getHeight();
		contentPanel = ownPanel(panel, panel.createCustomPanel(areaWidth, areaHeight, panel.getPlugin()));
		panel.addComponent(contentPanel).inTL(0f, 0f);
		new UIRect(0f, 0f, areaWidth, areaHeight).addBorder(contentPanel,
				Misc.getNegativeHighlightColor(), BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, areaWidth, BattleNext_PageLayout.HEADER_HEIGHT),
				contentPanel, settingsStrings.get("simulator_heading"), Misc.getNegativeHighlightColor());
		if (unlocked.getAsBoolean()) {
			UIRect footer = new UIRect(BattleNext_PageLayout.PAGE_CONTENT_INSET,
					areaHeight - BattleNext_PageLayout.PAGE_CONTENT_INSET - BattleNext_PageLayout.FOOTER_HEIGHT,
					areaWidth - BattleNext_PageLayout.PAGE_CONTENT_INSET * 2f, BattleNext_PageLayout.FOOTER_HEIGHT);
			statusContent = contentPanel.createUIElement(footer.getWidth(), footer.getHeight(), false);
			BattleNext_UiTheme.configure(statusContent);
			statusLabel = statusContent.addPara("", 0f);
			statusLabel.getPosition().inTL(0f, 5f);
			statusContent.addTooltipToPrevious(new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object ignored) {
					return 480f;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
					showStatusDetails(tooltip);
				}
			}, TooltipMakerAPI.TooltipLocation.ABOVE);
			statusContent.setHeightSoFar(footer.getHeight());
			contentPanel.addUIElement(statusContent).inTL(footer.getX(), footer.getY());
			refreshStatus();
			UIRect area = new UIRect(0f, BattleNext_PageLayout.HEADER_HEIGHT, areaWidth,
					areaHeight - BattleNext_PageLayout.HEADER_HEIGHT).shrink(BattleNext_PageLayout.PAGE_CONTENT_INSET);
			CustomPanelAPI sourcePanel = new UIRect(area.getX(), area.getY(), area.getWidth(), BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT)
					.createSubPanel(contentPanel, contentPanel.getPlugin()).getPanel();
			sourceField = new BattleNext_BattleSourceField(inputRouter, overlayHost, this::selectSource);
			sourceField.attach(sourcePanel);
			sourceField.configure(sources.getTournamentStore(), browser.getSelectedId());
			float top = area.getY() + BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT + 12f;
			if (!sources.getCatalogErrors().isEmpty()) {
				TooltipMakerAPI error = contentPanel.createUIElement(area.getWidth(), 0f, false);
				BattleNext_UiTheme.configure(error);
				error.setTextWidthOverride(area.getWidth());
				error.addPara(strings.get("load_failed") + ": " + String.join(" | ", sources.getCatalogErrors()), 0f).getPosition().inTL(0f, 0f);
				contentPanel.addUIElement(error).inTL(area.getX(), top);
				top += error.getHeightSoFar() + 8f;
			}
			bodyRect = new UIRect(area.getX(), top, area.getWidth(), Math.max(0f, footer.getY() - BattleNext_PageLayout.FOOTER_MARGIN - top));
			buildBody();
			return;
		}
		float buttonWidth = Math.min(BUTTON_MAX_WIDTH, Math.max(1f, areaWidth - BattleNext_PageLayout.PAGE_CONTENT_INSET * 2f));
		float buttonHeight = BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT;
		BattleNext_PageLayout.fixedButton(contentPanel,
				new UIRect((areaWidth - buttonWidth) * 0.5f, (areaHeight - buttonHeight) * 0.5f, buttonWidth, buttonHeight),
				settingsStrings.get("simulator_enable"), "simulator_enable", CutStyle.ALL, Misc.getNegativeHighlightColor());
	}

	private void selectSource(String id) {
		clearBody();
		browser.select(id);
		buildBody();
	}

	private void buildBody() {

		bodyPanel = ownPanel(contentPanel, bodyRect.createSubPanel(contentPanel, contentPanel.getPlugin()).getPanel());
		if (browser.getSelectedId().isEmpty()) {
			BattleNext_QuickBattleDisplay.Presentation presentation = new BattleNext_QuickBattleDisplay.Presentation(
					List.of(Column.PLAYER, Column.ENEMY, Column.MAP, Column.SCALE, Column.STATUS, Column.ACTIONS),
					List.of(0.3f, 0.3f, 0.05f, 0.1f, 0.15f, 0.1f), false,
					row -> new BattleNext_QuickBattleDisplay.Action(battleStrings.get("simulate"), "simulator:quick:" + row.getModel().getId(),
							sources.quickBattleErrors(row.getModel()).isEmpty()), this::submitQuickBattle);
			quickBattleDisplay = new BattleNext_QuickBattleDisplay(inputRouter, sources.getQuickBattleStore().getAll(), presentation,
					entry -> entry.getId(), row -> sources.quickBattleErrors(row.getModel()));
			quickBattleDisplay.attach(bodyPanel);
			if (browser.getQuickBattleState() != null) {
				quickBattleDisplay.restoreState(browser.getQuickBattleState());
			}
			return;
		}
		BattleNext_TournamentModel tournament = sources.getTournamentStore().get(browser.getSelectedId());
		BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedId());
		if (task != null) {
			BattleNext_TournamentSnapshot snapshot = task.getTournamentSnapshot();
			tournament = snapshot.getTournament();
			tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, snapshotDetails(snapshot),
					tournamentActions(task), this::handleTournamentAction);
			tournamentDisplay.attach(bodyPanel);
			tournamentVersion = snapshot.getVersion();
			tournamentTaskId = snapshot.getId();
			if (browser.getTournamentState() != null) {
				tournamentDisplay.restoreState(browser.getTournamentState());
			}
			return;
		}
		if (tournament == null) {
			TooltipMakerAPI error = bodyPanel.createUIElement(bodyRect.getWidth(), bodyRect.getHeight(), false);
			BattleNext_UiTheme.configure(error);
			error.addTitle(tournamentStrings.get("missing"));
			error.addPara(tournamentStrings.get("missing_detail") + " " + browser.getSelectedId(), 8f);
			bodyPanel.addUIElement(error).inTL(0f, 0f);
			return;
		}
		List<String> reasons = sources.tournamentErrors(tournament);
		BattleNext_TournamentDisplay.Action action = new BattleNext_TournamentDisplay.Action(battleStrings.get("simulate_tournament"),
				"simulator:tournament:" + tournament.getId(), reasons.isEmpty(), reasons);
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(tournament, sources.getFleetStore(), sources.getRuleStore(),
				String.join(" | ", sources.getCatalogErrors()), reasons);
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, details,
				List.of(action), this::handleTournamentAction);
		tournamentDisplay.attach(bodyPanel);
		if (browser.getTournamentState() != null) {
			tournamentDisplay.restoreState(browser.getTournamentState());
		}
	}

	private BattleNext_TournamentDisplay.Details snapshotDetails(BattleNext_TournamentSnapshot snapshot) {
		BattleNext_TournamentModel model = snapshot.getTournament();
		return new BattleNext_TournamentDisplay.Details(snapshot.getFleetNames(), snapshot.getRequest().source().has("rule"),
				model.getNextPendingMatch(), snapshot.getDiagnostic(), List.of());
	}

	private List<BattleNext_TournamentDisplay.Action> tournamentActions(BattleNext_SimulationTask task) {
		String sourceId = browser.getSelectedId();
		BattleNext_SimulationTask.State state = task.getState();
		if (state == BattleNext_SimulationTask.State.IMPORT_FAILED) {
			return List.of(new BattleNext_TournamentDisplay.Action(battleStrings.get("simulator_retry_save"), "simulator:retry:" + sourceId, true, List.of()));
		}
		if (state == BattleNext_SimulationTask.State.FINISHED && task.getTournamentSnapshot().getTournament().isCompleted()) {
			BattleNext_TournamentModel source = sources.getTournamentStore().get(sourceId);
			List<String> reasons = source == null ? List.of(tournamentStrings.get("missing_detail")) : sources.tournamentErrors(source);
			return List.of(new BattleNext_TournamentDisplay.Action(battleStrings.get("simulate_again"), "simulator:tournament:" + sourceId, reasons.isEmpty(), reasons));
		}
		String role = state == BattleNext_SimulationTask.State.PAUSED ? "resume" : "pause";
		return List.of(new BattleNext_TournamentDisplay.Action(battleStrings.get("simulator_" + role), "simulator:" + role + ":" + sourceId, true, List.of()));
	}

	private void handleTournamentAction(Object action) {
		if (!(action instanceof String id)) return;
		try {
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedId());
			if (id.startsWith("simulator:pause:")) {
				task.pause();
			} else if (id.startsWith("simulator:resume:")) {
				task.resume();
			} else if (id.startsWith("simulator:retry:")) {
				task.retryImport();
			} else if (id.startsWith("simulator:tournament:")) {
				BattleNext_TournamentModel model = sources.getTournamentStore().get(browser.getSelectedId());
				List<String> reasons = sources.tournamentErrors(model);
				if (!reasons.isEmpty()) {
					browser.setStatusError(String.join("; ", reasons));
					refreshStatus();
					return;
				}
				BattleNext_SimulatorService.getInstance().submit(BattleNext_SimulationRequest.fromTournament(model,
						sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.quickBattle(BattleNext_Settings.getBattleTimeLimit())));
			}
			browser.setStatusError("");
		} catch (RuntimeException exception) {
			browser.setStatusError(exception.getClass().getSimpleName() + ": " + exception.getMessage());
			BattleNext_Logger.warn(getClass(), "simulator.tournament.submission.failed",
					BattleNext_Logger.fields().put("source_id", browser.getSelectedId()).build(), exception);
		}
		refreshStatus();
	}

	private void refreshTournament() {
		if (tournamentDisplay == null || browser.getSelectedId().isEmpty()) return;
		BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedId());
		if (task == null) return;
		BattleNext_TournamentSnapshot snapshot = task.tournamentSnapshot();
		if (snapshot.getId().equals(tournamentTaskId) && snapshot.getVersion() == tournamentVersion) return;
		snapshot = task.getTournamentSnapshot();
		tournamentDisplay.update(snapshot.getTournament(), snapshotDetails(snapshot), tournamentActions(task));
		tournamentVersion = snapshot.getVersion();
		tournamentTaskId = snapshot.getId();
	}

	private void submitQuickBattle(Object action) {

		if (!(action instanceof String id) || !id.startsWith("simulator:quick:")) return;
		String entryId = id.substring("simulator:quick:".length());
		BattleNext_QuickBattleModel entry = sources.getQuickBattleStore().get(entryId);
		try {
			List<String> errors = sources.quickBattleErrors(entry);
			if (!errors.isEmpty()) {
				browser.setStatusError(String.join("; ", errors));
				BattleNext_Logger.warn(BattleNext_SimulatorWidget.class, "simulator.submission.rejected",
						BattleNext_Logger.fields().put("entry_id", entryId).put("entry", entry).put("errors", errors).build());
				refreshStatus();
				return;
			}
			BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromMatch(BattleNext_Match.fromQuickBattle(entry),
					sources.getFleetStore(), BattleNext_SimulationConfig.quickBattle(BattleNext_Settings.getBattleTimeLimit()));
			BattleNext_SimulatorService.getInstance().submit(request);
			browser.setStatusError("");
		} catch (RuntimeException exception) {
			browser.setStatusError(exception.getClass().getSimpleName() + ": " + exception.getMessage());
			BattleNext_Logger.warn(BattleNext_SimulatorWidget.class, "simulator.submission.rejected",
					BattleNext_Logger.fields().put("entry_id", entryId).put("entry", entry).build(), exception);
		}
		refreshStatus();
	}

	public void refreshStatus() {
		if (!unlocked.getAsBoolean()) return;
		refreshTournament();
		Status current = BattleNext_SimulatorService.status();
		String error = browser.getStatusError();
		if (current.equals(displayedStatus) && error.equals(displayedError)) return;
		displayedStatus = current;
		displayedError = error;
		String label = current.current() == null ? "—" : current.current().label();
		String summary = battleStrings.format("simulator_status", battleStrings.get("simulator_" + current.phase().name().toLowerCase(Locale.ROOT)), label, current.queued());
		statusLabel.setText(statusContent.shortenString(error.isEmpty() ? summary : error, statusContent.getWidthSoFar()));
	}

	private void showStatusDetails(TooltipMakerAPI tooltip) {

		BattleNext_UiTheme.configure(tooltip);
		Status status = BattleNext_SimulatorService.status();
		tooltip.addTitle(settingsStrings.get("simulator_heading"));
		tooltip.addPara(battleStrings.format("simulator_status", battleStrings.get("simulator_" + status.phase().name().toLowerCase(Locale.ROOT)),
				status.current() == null ? "—" : status.current().label(), status.queued()), 5f);
		if (!browser.getStatusError().isEmpty()) {
			tooltip.addPara(browser.getStatusError(), 5f);
		}
		for (BattleNext_SimulationRequest request : new BattleNext_SimulationRequest[]{status.current(), status.recentRequest()}) {
			if (request == null) continue;
			tooltip.addPara(request.id() + " - " + request.label(), 5f);
			if (request.kind() == BattleNext_SimulationRequest.Kind.MATCH) {
				try {
					tooltip.addPara(battleStrings.format("simulator_map", request.source().getJSONObject("match").getInt("map"),
							request.source().getInt("actualMap"), request.source().getJSONObject("match").getDouble("mapScale")), 5f);
				} catch (JSONException exception) {
					throw new IllegalStateException("Invalid simulation map snapshot: " + request.id(), exception);
				}
			}
		}
		if (status.result() != null) {
			try {
				BattleNext_SimulationResult result = status.result();
				String details = !result.tournamentSnapshotJson().isEmpty()
						? battleStrings.format("simulator_tournament_result", result.simulationSeconds(), result.executionNanos() / 1000000000d)
						: result.outcome() == BattleNext_SimulationResult.Outcome.COMPLETED
						? battleStrings.format("simulator_result", result.report().getWinner(), result.simulationSeconds(),
								result.executionNanos() / 1000000000d, result.report().getName()) : result.diagnostic();
				tooltip.addPara(result.outcome().name() + ": " + details, 5f);
				if (!status.importError().isEmpty()) {
					tooltip.addPara(status.importError(), 5f);
				}
			} catch (JSONException exception) {
				throw new IllegalStateException("Invalid simulator result", exception);
			}
		}
	}

	private void clearBody() {
		tournamentVersion = -1L;
		tournamentTaskId = "";
		if (quickBattleDisplay != null) {
			browser.rememberQuickBattle(quickBattleDisplay.captureState());
			quickBattleDisplay.close();
			quickBattleDisplay = null;
		}
		if (tournamentDisplay != null) {
			browser.rememberTournament(tournamentDisplay.captureState());
			tournamentDisplay.close();
			tournamentDisplay = null;
		}
		if (bodyPanel != null) {
			releasePanel(bodyPanel);
			bodyPanel = null;
		}
	}

	@Override
	protected void clearOverlays() {
		if (sourceField != null) {
			sourceField.closeOverlays();
		}
	}

	@Override
	protected void clearContent() {
		clearBody();
		if (sourceField != null) {
			sourceField.close();
			sourceField = null;
		}
		contentPanel = null;
		bodyRect = null;
		statusContent = null;
		statusLabel = null;
		displayedStatus = null;
		displayedError = null;
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		if (sourceField != null && sourceField.handleGlobalInput(events)) return true;
		if (tournamentDisplay != null) {
			tournamentDisplay.handleGlobalInput(events);
		}
		return false;
	}
}
