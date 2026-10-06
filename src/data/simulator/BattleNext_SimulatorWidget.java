package data.simulator;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import data.model.tournament.BattleNext_TournamentModel;
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
			bodyRect = new UIRect(area.getX(), top, area.getWidth(), Math.max(0f, area.getY() + area.getHeight() - top));
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
							sources.quickBattleErrors(row.getModel()).isEmpty()));
			quickBattleDisplay = new BattleNext_QuickBattleDisplay(inputRouter, sources.getQuickBattleStore().getAll(), presentation,
					entry -> entry.getId(), row -> sources.quickBattleErrors(row.getModel()));
			quickBattleDisplay.attach(bodyPanel);
			if (browser.getQuickBattleState() != null) {
				quickBattleDisplay.restoreState(browser.getQuickBattleState());
			}
			return;
		}
		BattleNext_TournamentModel tournament = sources.getTournamentStore().get(browser.getSelectedId());
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
				List.of(action), ignored -> {});
		tournamentDisplay.attach(bodyPanel);
		if (browser.getTournamentState() != null) {
			tournamentDisplay.restoreState(browser.getTournamentState());
		}
	}

	private void clearBody() {
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
