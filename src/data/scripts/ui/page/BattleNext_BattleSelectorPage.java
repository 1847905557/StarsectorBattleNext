package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.missions.BattleNext_MissionBattleMap;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.rule.BattleNext_RulesCheck;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_QuickBattleEditor;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_DropdownField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.tools.BattleNext_BattleSignals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BattleNext_BattleSelectorPage implements BattleNext_PageView {

	private data.scripts.ui.BattleNext_UiInputRouter inputRouter = new data.scripts.ui.BattleNext_UiInputRouter();

	@Override
	public void setInputRouter(data.scripts.ui.BattleNext_UiInputRouter router) {
		this.inputRouter = router;
	}

	private static final float SOURCE_HEADER_HEIGHT = 88f;
	private static final float SOURCE_LABEL_MAX_WIDTH = 175f;
	private static final float SOURCE_LABEL_WIDTH_SHARE = 0.25f;
	private static final float SOURCE_BUTTON_MAX_WIDTH = 420f;
	private static final float SOURCE_BUTTON_WIDTH_SHARE = 0.45f;
	private static final float SOURCE_CONTROL_GAP = 12f;
	private static final float SOURCE_CONTROL_TOP = 44f;
	private static final float SOURCE_CONTROL_HEIGHT = 30f;
	private static final float SOURCE_LABEL_TEXT_TOP_INSET = 5f;
	private static final float TOURNAMENT_MISSING_MESSAGE_GAP = 8f;

	private static final int RANDOM_MAP_ID = -1;
	private static final int LAST_MAP_ID = BattleNext_MissionBattleMap.MAP_COUNT - 1;

	public static class BattleSourceSelectionRequest {
		private final String id;

		public BattleSourceSelectionRequest(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}
	}

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private BattleNext_DropdownField sourceDropdown;
	private BattleNext_QuickBattleEditor quickBattleEditor;
	private BattleNext_TournamentDisplay tournamentDisplay;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_TournamentEngine tournamentEngine = new BattleNext_TournamentEngine();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RuleStore ruleStore = new BattleNext_RuleStore();
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private String selectedId = "";
	private String loadError = "";

	public BattleNext_BattleSelectorPage(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		if (sourceDropdown != null) sourceDropdown.close();
		this.panel = panel;
		load();
		createContents();
	}

	@Override
	public void createContents() {
		if (sourceDropdown != null) sourceDropdown.close();
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] sections = root.splitVertically(SOURCE_HEADER_HEIGHT);
		contentRect = sections[1];
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT), panel,
				text("page_battle_selector"));

		float labelWidth = Math.min(SOURCE_LABEL_MAX_WIDTH, root.getWidth() * SOURCE_LABEL_WIDTH_SHARE);
		float buttonWidth = Math.min(SOURCE_BUTTON_MAX_WIDTH, root.getWidth() * SOURCE_BUTTON_WIDTH_SHARE);
		float groupX = (root.getWidth() - labelWidth - SOURCE_CONTROL_GAP - buttonWidth) * 0.5f;
		UIRect labelRect = new UIRect(groupX, SOURCE_CONTROL_TOP,
				labelWidth, SOURCE_CONTROL_HEIGHT);
		TooltipMakerAPI sourceLabel = labelRect.beginTooltip(panel, false);
		sourceLabel.setParaOrbitronLarge();
		sourceLabel.addPara(text("battle_source"), 0f)
				.getPosition().inTL(0f, SOURCE_LABEL_TEXT_TOP_INSET);
		sourceLabel.setHeightSoFar(labelRect.getHeight());
		labelRect.addTooltip();
		UIRect sourceButtonRect = new UIRect(groupX + labelWidth + SOURCE_CONTROL_GAP,
				SOURCE_CONTROL_TOP, buttonWidth, SOURCE_CONTROL_HEIGHT);
		sourceDropdown = new BattleNext_DropdownField(inputRouter, panel, inputOwner, buttonWidth,
				id -> {
					inputOwner.buttonPressed(new BattleSourceSelectionRequest(id));
				});
		sourceDropdown.attach(null, sourceButtonRect);
		sourceDropdown.setOptions(sourceOptions());
		sourceDropdown.setValue(selectedId);
		sourceDropdown.setEnabled(tournamentStore.isLoaded());

		refreshContent();
	}

	@Override
	public void refreshContent() {
		if (sourceDropdown != null) sourceDropdown.close();
		closeChart();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, inputOwner).getPanel();
		quickBattleEditor = null;
		if (selectedId.isEmpty()) {
			quickBattleEditor = new BattleNext_QuickBattleEditor(backAction, inputOwner);
			quickBattleEditor.setInputRouter(inputRouter);
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
		if (sourceDropdown != null) sourceDropdown.close();
	}

	public void refreshRowStatuses() {
		if (quickBattleEditor != null) quickBattleEditor.refreshRowStatuses();
	}

	@Override
	public String getStatusText() {
		return quickBattleEditor == null ? loadError : quickBattleEditor.getStatusText();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (sourceDropdown != null && sourceDropdown.handleGlobalInput(events)) return true;

		if (tournamentDisplay != null) {
			tournamentDisplay.handleGlobalInput(events);
		}
		return false;
	}

	public void closeChart() {
		if (tournamentDisplay != null) {
			tournamentDisplay.close();
			tournamentDisplay = null;
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("tournament:start".equals(buttonId)) {
			startTournament();
		} else if ("tournament:authorize".equals(buttonId)) {
			BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
			BattleNext_Match next = tournament == null ? null : nextMatch(tournament);
			if (next != null) {
				provider.load();
				if (provider.getWarnings().isEmpty() && !provider.getRuleWarnings().isEmpty()) {
					BattleNext_BattleSignals.authorizeMatch(tournament.getId(), next.getId());
					loadError = text("rules_authorized");
				}
				refreshContent();
			}
		} else if (selectedId.isEmpty() && quickBattleEditor != null) {
			quickBattleEditor.buttonPressed(buttonId);
		}
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
			ruleStore.reload();
		} catch (Exception exception) {
			loadError += (loadError.isEmpty() ? "" : " | ") + text("load_failed") + ": " + exception.getMessage();
		}
		try {
			fleetStore.reload();
		} catch (Exception exception) {
			loadError += (loadError.isEmpty() ? "" : " | ") + text("load_failed") + ": " + exception.getMessage();
		}
	}

	private List<BattleNext_DropdownField.DropdownChoice> sourceOptions() {
		List<BattleNext_DropdownField.DropdownChoice> options = new ArrayList<>();
		options.add(new BattleNext_DropdownField.DropdownChoice("", text("quick_battle")));
		for (BattleNext_TournamentModel tournament : tournamentStore.getAll()) {
			options.add(new BattleNext_DropdownField.DropdownChoice(tournament.getId(),
					tournament.getName()));
		}
		return options;
	}

	private void buildTournamentDetails(CustomPanelAPI detailPanel) {

		BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
		if (tournament == null) {
			UIRect messageRect = new UIRect(0f, 0f, detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight());
			TooltipMakerAPI message = messageRect.beginTooltip(detailPanel, false);
			BattleNext_UiTheme.configure(message);
			message.addTitle(text("tournament_missing"));
			message.addPara(text("tournament_missing_detail"), TOURNAMENT_MISSING_MESSAGE_GAP);
			messageRect.addTooltip();
			return;
		}
		Map<String, String> fleetNames = new java.util.LinkedHashMap<>();
		for (String fleetId : tournament.getFleetIds()) {
			if (fleetStore.get(fleetId) != null) {
				fleetNames.put(fleetId, fleetStore.get(fleetId).getName());
			}
		}
		BattleNext_Match next = nextMatch(tournament);
		List<String> warnings = new ArrayList<>();
		boolean authorizeAvailable = false;

		if (selectedId.equals(tournamentStore.getActiveId()) && next != null) {
			provider.load();
			warnings.addAll(provider.getWarnings());
			warnings.addAll(provider.getRuleWarnings());
			authorizeAvailable = provider.getWarnings().isEmpty() && !provider.getRuleWarnings().isEmpty();
		}
		BattleNext_TournamentDisplay.Details details = new BattleNext_TournamentDisplay.Details(fleetNames,
				ruleStore.get(tournament.getRuleId()) != null, next, loadError, warnings,
				tournamentStore.isLoaded() && fleetStore.isLoaded() && ruleStore.isLoaded(), authorizeAvailable);
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, details, inputOwner::buttonPressed);
		tournamentDisplay.attach(detailPanel);
	}

	private void startTournament() {
		if (!tournamentStore.isLoaded() || !fleetStore.isLoaded() || !ruleStore.isLoaded()) {
			loadError = text("load_failed") + ": " + (!tournamentStore.isLoaded() ? tournamentStore.getLoadError()
					: !fleetStore.isLoaded() ? fleetStore.getLoadError() : ruleStore.getLoadError());
			refreshContent();
			return;
		}
		BattleNext_TournamentModel source = tournamentStore.get(selectedId);
		if (source == null || source.isStarted()) return;
		if (source.getBracketType() == null || source.getBoCount() < 1
				|| source.getMaxFleet() < BattleNext_TournamentModel.MIN_FLEET_COUNT
				|| source.getMaxFleet() > BattleNext_TournamentModel.MAX_FLEET_COUNT
				|| source.getMapId() < RANDOM_MAP_ID || source.getMapId() > LAST_MAP_ID) {
			loadError = text("invalid_tournament_format");
			refreshContent();
			return;
		}
		if (source.getFleetIds().size() < BattleNext_TournamentModel.MIN_FLEET_COUNT
				|| source.getFleetIds().size() > source.getMaxFleet()) {
			loadError = text("tournament_need_fleets");
			refreshContent();
			return;
		}
		BattleNext_TournamentModel tournament;
		try {
			tournament = new BattleNext_TournamentModel();
			tournament.fromJson(source.toJson());
		} catch (Exception exception) {
			loadError = exception.getMessage();
			refreshContent();
			return;
		}
		if (!tournament.getRuleId().isEmpty() && ruleStore.get(tournament.getRuleId()) == null) {
			loadError = text("tournament_rules_unavailable");
			refreshContent();
			return;
		}
		if (!tournament.getRuleId().isEmpty()) {
			List<String> missing = new BattleNext_RulesCheck().missingRestrictions(ruleStore.get(tournament.getRuleId()));
			if (!missing.isEmpty()) {
				loadError = String.join("; ", missing);
				refreshContent();
				return;
			}
		}
		for (String fleet : tournament.getFleetIds()) {
			if (fleetStore.get(fleet) == null) {
				loadError = text("invalid_player") + ": " + fleet;
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

	private BattleNext_Match nextMatch(BattleNext_TournamentModel tournament) {
		if (!tournament.isStarted() || tournament.isCompleted()) return null;
		BattleNext_Match pending = tournament.findMatch(tournament.getPendingMatchId());
		if (pending != null && pending.getStatus() == BattleNext_Match.MatchStatus.PENDING) return pending;
		for (BattleNext_Match match : tournament.getMatches()) {
			if (match.getStatus() == BattleNext_Match.MatchStatus.PENDING && !match.getEnemy().isEmpty()) return match;
		}
		return null;
	}

	private String text(String key) {
		return strings.get(key);
	}
}
