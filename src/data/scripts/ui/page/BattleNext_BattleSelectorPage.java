package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_QuickBattleEditor;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_BattleSelectorPage extends BattleNext_PageView {

	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");

	private static final float SOURCE_HEADER_HEIGHT = 88f;
	private static final float SOURCE_CONTROL_TOP = 44f;
	private static final float SOURCE_CONTROL_HEIGHT = 30f;
	private static final float TOURNAMENT_MISSING_MESSAGE_GAP = 8f;

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
	private UIRect contentRect;
	private UIRect sourceRect;
	private BattleNext_BattleSourceField sourceField;
	private BattleNext_QuickBattleEditor quickBattleEditor;
	private BattleNext_TournamentDisplay tournamentDisplay;
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_TournamentStore tournamentStore = provider.getTournamentStore();
	private final BattleNext_TournamentEngine tournamentEngine = new BattleNext_TournamentEngine();
	private final BattleNext_FleetStore fleetStore = provider.getFleetStore();
	private final BattleNext_RuleStore ruleStore = provider.getRuleStore();
	private String selectedId = "";
	private String loadError = "";

	public BattleNext_BattleSelectorPage(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	protected void loadPageState() {
		quickBattleEditor = null;
		load();
	}

	@Override
	protected void buildPage() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] sections = root.splitVertically(SOURCE_HEADER_HEIGHT);
		contentRect = sections[1];
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT), panel,
				managementStrings.get("page_battle_selector"));

		sourceRect = new UIRect(0f, SOURCE_CONTROL_TOP, root.getWidth(), SOURCE_CONTROL_HEIGHT);
	}

	@Override
	protected void refreshPage() {
		sourceRect.removeLatestSubPanel();
		CustomPanelAPI sourcePanel = sourceRect.createSubPanel(panel, inputOwner).getPanel();
		sourceField = new BattleNext_BattleSourceField(inputRouter, panel,
				id -> inputOwner.buttonPressed(new BattleSourceSelectionRequest(id)));
		sourceField.attach(sourcePanel);
		sourceField.configure(tournamentStore, selectedId);
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, inputOwner).getPanel();
		if (selectedId.isEmpty()) {
			if (quickBattleEditor == null) {
				quickBattleEditor = new BattleNext_QuickBattleEditor(backAction, inputOwner);
			}
			attachEditor(contentPanel);
		} else {
			quickBattleEditor = null;
			buildTournamentDetails(contentPanel);
		}
	}

	@Override
	protected void clearPageOverlays() {
		if (quickBattleEditor != null) quickBattleEditor.closeOverlays();
		if (sourceField != null) {
			sourceField.closeOverlays();
		}
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return quickBattleEditor;
	}

	@Override
	protected void clearPageResources() {
		if (sourceField != null) {
			sourceField.close();
			sourceField = null;
		}
		closeChart();
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if ("tournament:start".equals(buttonId)) {
			startTournament();
		} else if ("tournament:authorize".equals(buttonId)) {
			BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
			BattleNext_Match next = tournament == null ? null : tournament.getNextPendingMatch();
			if (next != null) {
				if (provider.authorizeNextMatch(tournament.getId(), next.getId())) {
					loadError = ruleStrings.get("next_briefing_authorized");
				}
				refreshContent();
			}
		} else if (selectedId.isEmpty() && quickBattleEditor != null) {
			quickBattleEditor.buttonPressed(buttonId);
		}
	}

	@Override
	protected boolean isPageDirty() {
		return selectedId.isEmpty() && quickBattleEditor != null && quickBattleEditor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		return quickBattleEditor == null || quickBattleEditor.save();
	}

	@Override
	protected void discardPage() {
		if (quickBattleEditor != null) quickBattleEditor.discard();
	}

	@Override
	protected String pageStatus() {
		return quickBattleEditor == null ? loadError : quickBattleEditor.getStatusText();
	}

	@Override
	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (sourceField != null && sourceField.handleGlobalInput(events)) return true;
		if (quickBattleEditor != null && quickBattleEditor.handleGlobalInput(events)) return true;

		if (tournamentDisplay != null) {
			tournamentDisplay.handleGlobalInput(events);
		}
		return false;
	}

	@Override
	public void advance(float amount) {
		if (quickBattleEditor != null) {
			quickBattleEditor.refreshRowStatuses();
		}
	}

	public void closeChart() {
		if (tournamentDisplay != null) {
			tournamentDisplay.close();
			tournamentDisplay = null;
		}
	}

	private void load() {
		List<String> errors = provider.reloadTournamentCatalog();
		loadError = errors.isEmpty() ? "" : strings.get("load_failed") + ": " + String.join(" | ", errors);
		if (tournamentStore.isLoaded()) {
			selectedId = tournamentStore.getActiveId();
		}
	}

	private void buildTournamentDetails(CustomPanelAPI detailPanel) {

		UIRect area = BattleNext_PageLayout.pageContentRect(new UIRect(0f, 0f,
				detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight()));
		detailPanel = area.createSubPanel(detailPanel, detailPanel.getPlugin()).getPanel();
		BattleNext_TournamentModel tournament = tournamentStore.get(selectedId);
		if (tournament == null) {
			UIRect messageRect = new UIRect(0f, 0f, detailPanel.getPosition().getWidth(), detailPanel.getPosition().getHeight());
			TooltipMakerAPI message = messageRect.beginTooltip(detailPanel, false);
			BattleNext_UiTheme.configure(message);
			message.setTextWidthOverride(messageRect.getWidth());
			BattleNext_PageLayout.contentTitle(message, tournamentStrings.get("missing"), messageRect.getWidth(), 0f);
			message.addPara(tournamentStrings.get("missing_detail"), TOURNAMENT_MISSING_MESSAGE_GAP);
			messageRect.addTooltip();
			return;
		}
		BattleNext_Match next = tournament.getNextPendingMatch();
		List<String> warnings = new ArrayList<>();
		boolean authorizeAvailable = false;

		if (selectedId.equals(tournamentStore.getActiveId()) && next != null) {
			provider.load();
			warnings.addAll(provider.getWarnings());
			warnings.addAll(provider.getRuleWarnings());
			authorizeAvailable = provider.getWarnings().isEmpty() && !provider.getRuleWarnings().isEmpty();
		}
		BattleNext_TournamentDisplay.Details details = BattleNext_TournamentDisplay.details(tournament, fleetStore, ruleStore, loadError, warnings);
		List<BattleNext_TournamentDisplay.Action> actions = new ArrayList<>();
		if (authorizeAvailable) {
			actions.add(new BattleNext_TournamentDisplay.Action(ruleStrings.get("force_start_match"), "tournament:authorize", true, List.of()));
		}
		if (!tournament.isStarted()) {
			actions.add(new BattleNext_TournamentDisplay.Action(tournamentStrings.get("start"), "tournament:start",
					tournamentStore.isLoaded() && fleetStore.isLoaded() && ruleStore.isLoaded(), List.of()));
		}
		tournamentDisplay = new BattleNext_TournamentDisplay(inputRouter, tournament, details, actions, inputOwner::buttonPressed);
		tournamentDisplay.attach(detailPanel);
	}

	private void startTournament() {
		BattleNext_TournamentModel source = tournamentStore.get(selectedId);
		if (source == null || source.isStarted()) return;
		try {
			tournamentEngine.startAndActivate(source, tournamentStore, fleetStore, ruleStore);
			loadError = tournamentStrings.get("started");
		} catch (Exception exception) {
			loadError = source.getFleetIds().size() < BattleNext_TournamentModel.MIN_FLEET_COUNT
					? tournamentStrings.get("need_fleets") + ": " + exception.getMessage() : exception.getMessage();
		}
		refreshContent();
	}
}
