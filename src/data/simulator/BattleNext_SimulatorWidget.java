package data.simulator;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_BattleValidation;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import data.scripts.ui.widget.view.BattleNext_TournamentTemplate;
import data.simulator.BattleNext_SimulatorService.Status;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public class BattleNext_SimulatorWidget extends BattleNext_PanelBasedWidget {

	private static final float BUTTON_MAX_WIDTH = 360f;
	private final BooleanSupplier unlocked;
	private final BattleNext_SimulatorBrowser browser;
	private final BattleNext_BattleSourceProvider sources;
	private final CustomPanelAPI overlayHost;
	private final BattleNext_I18nSection simulatorStrings = BattleNext_I18nSection.getInstance("Simulator", "BattleNext_simulator_");
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private BattleNext_BattleSourceField sourceField;
	private BattleNext_BattleSourceField instanceField;
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
				contentPanel, simulatorStrings.get("heading"), Misc.getNegativeHighlightColor());
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
		float textWidth = areaWidth - BattleNext_PageLayout.PAGE_CONTENT_INSET * 2f;
		TooltipMakerAPI warning = contentPanel.createUIElement(textWidth, 0f, false);
		BattleNext_UiTheme.configure(warning);
		warning.setTextWidthOverride(textWidth);
		LabelAPI description = warning.addPara(simulatorStrings.get("warning"), BattleNext_UiTheme.TEXT, 0f);
		description.autoSizeToWidth(textWidth);
		description.setAlignment(Alignment.MID);
		description.getPosition().inTL(0f, 0f);
		warning.setHeightSoFar(description.getPosition().getHeight());
		LabelAPI emphasized = warning.addPara(simulatorStrings.get("warned"), Misc.getNegativeHighlightColor(), 12f);
		emphasized.autoSizeToWidth(textWidth);
		emphasized.setAlignment(Alignment.MID);
		emphasized.getPosition().inTL(0f, description.getPosition().getHeight() + 12f);
		float textHeight = description.getPosition().getHeight() + 12f + emphasized.getPosition().getHeight();
		warning.setHeightSoFar(textHeight);
		float top = (areaHeight - textHeight - 12f - buttonHeight) * 0.5f;
		contentPanel.addUIElement(warning).inTL(BattleNext_PageLayout.PAGE_CONTENT_INSET, top);
		BattleNext_PageLayout.fixedButton(contentPanel,
				new UIRect((areaWidth - buttonWidth) * 0.5f, top + textHeight + 12f, buttonWidth, buttonHeight),
				simulatorStrings.get("enable"), "simulator_enable", CutStyle.ALL, true);
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
					row -> new BattleNext_QuickBattleDisplay.Action(simulatorStrings.get("simulate"), "simulator:quick:" + row.getModel().getId(),
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
		if (tournament == null && sources.getInstances(browser.getSelectedId(), BattleNext_StartedTournament.ExecutionKind.SIMULATION).isEmpty()) {
			TooltipMakerAPI error = bodyPanel.createUIElement(bodyRect.getWidth(), bodyRect.getHeight(), false);
			error.addPara(tournamentStrings.get("missing_detail") + " " + browser.getSelectedId(), 0f);
			bodyPanel.addUIElement(error).inTL(0f, 0f);
			return;
		}
		List<BattleNext_StartedTournament> instances = sources.getInstances(browser.getSelectedId(), BattleNext_StartedTournament.ExecutionKind.SIMULATION);
		if (browser.getSelectedInstanceId() == null || !browser.getSelectedInstanceId().isEmpty()
				&& sources.getInstance(browser.getSelectedInstanceId()) == null) {
			browser.selectInstance(instances.isEmpty() ? "" : instances.get(0).getId());
		}
		CustomPanelAPI instancePanel = new UIRect(0f, 0f, bodyRect.getWidth(), BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT)
				.createSubPanel(bodyPanel, bodyPanel.getPlugin()).getPanel();
		instanceField = new BattleNext_BattleSourceField(inputRouter, overlayHost, id -> {
			clearBody();
			browser.selectInstance(id);
			buildBody();
		});
		instanceField.attach(instancePanel);
		instanceField.configureInstances(tournament, instances, true, browser.getSelectedInstanceId());
		BattleNext_StartedTournament started = sources.getInstance(browser.getSelectedInstanceId());
		BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedInstanceId());
		if (task != null) {
			started = task.getStartedTournament();
		}
		List<String> fleetIds = started == null ? browser.template(tournament).getFleetIds() : started.getFleetIds();
		List<String> reasons = tournament == null ? List.of(tournamentStrings.get("missing_detail") + " " + browser.getSelectedId())
				: started == null ? sources.getCatalogErrors() : sources.tournamentErrors(tournament, fleetIds);
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(tournament, started, fleetIds,
				sources.getFleetStore(), sources.getRuleStore(), task == null ? "" : task.getImportError(), reasons).withCheckResult(browser.getCheckResult());
		CustomPanelAPI displayPanel = new UIRect(0f, BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT + 8f, bodyRect.getWidth(),
				Math.max(1f, bodyRect.getHeight() - BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT - 8f))
				.createSubPanel(bodyPanel, bodyPanel.getPlugin()).getPanel();
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, started, details, tournamentActions(task), this::handleTournamentAction);
		if (started == null && browser.getSelectedInstanceId().isEmpty()) {
			BattleNext_TournamentTemplate template = browser.template(tournament);
			tournamentDisplay.enableTemplateEditing(overlayHost, new BattleNext_FleetSourceProvider(sources.getFleetStore()),
					template::getFleetIds, (slot, id) -> {
						template.select(slot, id);
						browser.setCheckResult(null);
						updateTournamentDetails();
					});
		}
		tournamentDisplay.attach(displayPanel);
		tournamentVersion = task == null ? started == null ? -1L : started.getVersion() : task.getVersion();
		tournamentTaskId = task == null ? "" : task.getRequest().id();
		if (browser.getTournamentState() != null) {
			tournamentDisplay.restoreState(browser.getTournamentState());
		}
	}

	private List<BattleNext_TournamentDisplay.Action> tournamentActions(BattleNext_SimulationTask task) {
		List<BattleNext_TournamentDisplay.Action> actions = new ArrayList<>();
		BattleNext_TournamentModel source = sources.getTournamentStore().get(browser.getSelectedId());
		List<String> reasons = source == null ? List.of(tournamentStrings.get("missing_detail") + " " + browser.getSelectedId()) : sources.getCatalogErrors();
		actions.add(new BattleNext_TournamentDisplay.Action(simulatorStrings.get("simulate_tournament"),
				"simulator:tournament:" + browser.getSelectedId(), reasons.isEmpty(), reasons, "simulate"));
		actions.add(new BattleNext_TournamentDisplay.Action(tournamentStrings.get("check_fleets"),
				"simulator:check:" + browser.getSelectedId(), source != null, List.of(), "simulate"));
		if (task == null || task.getState() == BattleNext_SimulationTask.State.FINISHED) return actions;
		String role = task.getState() == BattleNext_SimulationTask.State.IMPORT_FAILED ? "retry_save"
				: task.getState() == BattleNext_SimulationTask.State.PAUSED ? "resume" : "pause";
		actions.add(new BattleNext_TournamentDisplay.Action(simulatorStrings.get(role),
				"simulator:" + role + ":" + task.getRequest().id(), true, List.of()));
		return actions;
	}

	private void handleTournamentAction(Object action) {
		if (!(action instanceof String id)) return;
		try {
			BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedInstanceId());
			if (id.startsWith("simulator:pause:")) {
				task.pause();
			} else if (id.startsWith("simulator:resume:")) {
				task.resume();
			} else if (id.startsWith("simulator:retry_save:")) {
				task.retryImport();
			} else if (id.startsWith("simulator:tournament:") || id.startsWith("simulator:check:")) {
				BattleNext_TournamentModel configuration = sources.getTournamentStore().get(browser.getSelectedId());
				BattleNext_StartedTournament selected = sources.getInstance(browser.getSelectedInstanceId());
				List<String> fleetIds = selected == null ? browser.template(configuration).getFleetIds() : selected.getFleetIds();
				browser.setCheckResult(sources.checkTournament(browser.getSelectedId(), fleetIds));
				configuration = sources.getTournamentStore().get(browser.getSelectedId());
				if (id.startsWith("simulator:check:") || !browser.getCheckResult().failures().isEmpty()) {
					browser.setStatusError("");
					updateTournamentDetails();
					refreshStatus();
					return;
				}
				task = BattleNext_SimulatorService.getInstance().submit(BattleNext_SimulationRequest.fromTournament(configuration,
						fleetIds, sources.getFleetStore(), sources.getRuleStore(), BattleNext_SimulationConfig.quickBattle(BattleNext_Settings.getBattleTimeLimit())));
				clearBody();
				browser.selectInstance(task.getRequest().id());
				buildBody();
			}
			browser.setStatusError("");
		} catch (BattleNext_BattleValidation.Rejection rejection) {
			browser.setCheckResult(rejection.getResult());
			browser.setStatusError("");
			updateTournamentDetails();
			BattleNext_Logger.warn(getClass(), "simulator.tournament.rejected", BattleNext_Logger.fields().put("source_id", browser.getSelectedId()).build(), rejection);
		} catch (Exception exception) {
			browser.setStatusError(strings.get("load_failed"));
			BattleNext_Logger.warn(getClass(), "simulator.tournament.submission.failed",
					BattleNext_Logger.fields().put("source_id", browser.getSelectedId()).build(), exception);
		}
		refreshStatus();
	}

	private void refreshTournament() {
		if (tournamentDisplay == null || browser.getSelectedId().isEmpty()) return;
		BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedInstanceId());
		if (task == null && !tournamentTaskId.isEmpty()) {
			sources.reloadTournamentCatalog();
			clearBody();
			buildBody();
			return;
		}
		if (task == null || task.getVersion() == tournamentVersion) return;
		sources.reloadTournamentCatalog();
		BattleNext_TournamentModel configuration = sources.getTournamentStore().get(browser.getSelectedId());
		BattleNext_StartedTournament started = task.getStartedTournament();
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(configuration, started,
				sources.getFleetStore(), sources.getRuleStore(), task.getImportError(), sources.getCatalogErrors()).withCheckResult(browser.getCheckResult());
		tournamentDisplay.update(configuration, started, details, tournamentActions(task));
		tournamentVersion = task.getVersion();
		tournamentTaskId = started.getId();
	}

	private void updateTournamentDetails() {
		BattleNext_TournamentModel configuration = sources.getTournamentStore().get(browser.getSelectedId());
		if (configuration == null) {
			clearBody();
			buildBody();
			return;
		}
		BattleNext_SimulationTask task = BattleNext_SimulatorService.tournament(browser.getSelectedInstanceId());
		BattleNext_StartedTournament selected = sources.getInstance(browser.getSelectedInstanceId());
		List<String> fleetIds = selected == null ? browser.template(configuration).getFleetIds() : selected.getFleetIds();
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(configuration, selected, fleetIds,
				sources.getFleetStore(), sources.getRuleStore(), "", List.of()).withCheckResult(browser.getCheckResult());
		tournamentDisplay.update(configuration, selected, details, tournamentActions(task));
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
		String summary = simulatorStrings.format("status", simulatorStrings.get(current.phase().name().toLowerCase(Locale.ROOT)), label, current.queued());
		statusLabel.setText(statusContent.shortenString(error.isEmpty() ? summary : error, statusContent.getWidthSoFar()));
	}

	private void showStatusDetails(TooltipMakerAPI tooltip) {

		BattleNext_UiTheme.configure(tooltip);
		Status status = BattleNext_SimulatorService.status();
		tooltip.addTitle(simulatorStrings.get("heading"));
		tooltip.addPara(simulatorStrings.format("status", simulatorStrings.get(status.phase().name().toLowerCase(Locale.ROOT)),
				status.current() == null ? "—" : status.current().label(), status.queued()), 5f);
		if (!browser.getStatusError().isEmpty()) {
			tooltip.addPara(browser.getStatusError(), 5f);
		}
		for (BattleNext_SimulationRequest request : new BattleNext_SimulationRequest[]{status.current(), status.recentRequest()}) {
			if (request == null) continue;
			tooltip.addPara(request.id() + " - " + request.label(), 5f);
			if (request.kind() == BattleNext_SimulationRequest.Kind.MATCH) {
				try {
					tooltip.addPara(simulatorStrings.format("map", request.source().getJSONObject("match").getInt("map"),
							request.source().getInt("actualMap"), request.source().getJSONObject("match").getDouble("mapScale")), 5f);
				} catch (JSONException exception) {
					throw new IllegalStateException("Invalid simulation map snapshot: " + request.id(), exception);
				}
			}
		}
		if (status.result() != null) {
			try {
				BattleNext_SimulationResult result = status.result();
				String details = !result.startedTournamentJson().isEmpty()
						? simulatorStrings.format("tournament_result", result.simulationSeconds(), result.executionNanos() / 1000000000d)
						: !result.reportJson().isEmpty()
						? simulatorStrings.format("result", result.report().getWinner(), result.simulationSeconds(),
								result.executionNanos() / 1000000000d, result.report().getName()) : result.diagnostic();
				String outcome = result.outcome() == BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED
						? tournamentStrings.get("round_draw") : result.outcome().name();
				tooltip.addPara(outcome + ": " + details, 5f);
				if (!result.reportJson().isEmpty() && !result.diagnostic().isEmpty()) {
					tooltip.addPara(result.diagnostic(), 5f);
				}
				if (!status.importError().isEmpty()) {
					tooltip.addPara(status.importError(), 5f);
				}
			} catch (JSONException exception) {
				throw new IllegalStateException("Invalid simulator result", exception);
			}
		}
	}

	private void clearBody() {
		if (instanceField != null) {
			instanceField.close();
			instanceField = null;
		}
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
		if (tournamentDisplay != null) {
			tournamentDisplay.closeOverlays();
		}
		if (instanceField != null) {
			instanceField.closeOverlays();
		}
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
		if (instanceField != null && instanceField.handleGlobalInput(events)) return true;
		if (tournamentDisplay != null) {
			tournamentDisplay.handleGlobalInput(events);
		}
		return false;
	}
}
