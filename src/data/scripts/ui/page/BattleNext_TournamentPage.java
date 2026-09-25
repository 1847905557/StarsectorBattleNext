package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_TournamentPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RulesStore rulesStore = new BattleNext_RulesStore();
	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private BattleNext_Tournament selected;
	private String status = "";

	public BattleNext_TournamentPage(Runnable backAction) {
		this.backAction = backAction;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		selected = null;
		fleetStore.reload();
		try {
			tournamentStore.reload();
			rulesStore.reload();
		} catch (Exception exception) {
			status = exception.getMessage();
		}
		createContents();
	}

	@Override
	public void createContents() {
		// left list / right details / bottom back button
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] bottom = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		BattleNext_PageLayout.heading(header[0], panel, text("page_tournaments"));

		float listWidth = BattleNext_PageLayout.listWidth(panel);
		UIRect[] columns = bottom[0].splitHorizontally(listWidth + 10f);
		listRect = columns[0];
		detailRect = columns[1];

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		TooltipMakerAPI footer = BattleNext_PageLayout.footer(footerRect, panel);
		BattleNext_PageLayout.button(footer, text("back"), "back", 160f).getPosition().inBL(0f, 0f);

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI listContent = listPanel.createUIElement(listRect.getWidth(), listRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(0f, 0f);

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI detailContent = detailPanel.createUIElement(detailRect.getWidth(), detailRect.getHeight(), true);
		addDetails(detailContent);
		detailPanel.addUIElement(detailContent).inTL(0f, 0f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String)buttonId).startsWith("tournament:")) {
			handleTournamentAction((String)buttonId);
			return;
		}
		if ("back".equals(buttonId)) {
			if (selected != null) {
				selected = null;
				refreshContent();
			} else {
				backAction.run();
			}
		}
	}

	private void handleTournamentAction(String action) {
		try {
			if ("tournament:create".equals(action)) {
				createTournament();
			} else if (action.startsWith("tournament:select:")) {
				selected = tournamentStore.get(Integer.parseInt(action.substring("tournament:select:".length())));
			} else if ("tournament:activate".equals(action)) {
				tournamentStore.setActiveSlot(selected.getSlot());
			} else if ("tournament:deactivate".equals(action)) {
				tournamentStore.setActiveSlot(-1);
			} else if ("tournament:start".equals(action)) {
				startTournament();
			}
		} catch (Exception exception) {
			status = exception.getMessage();
		}
		refreshContent();
	}

	private void createTournament() throws IOException, JSONException {
		int slot = firstFreeSlot();
		if (slot < 0) {
			throw new IllegalStateException(text("tournament_no_slot"));
		}
		BattleNext_Tournament tournament = new BattleNext_Tournament(slot,
				text("tournament_context") + " " + slot, "single_elim", "BO1", 2);
		int added = 0;
		for (BattleNext_FleetModel fleet : fleetStore.getAll()) {
			tournament.addTeam(fleet.getId());
			added++;
			if (added == 2) {
				break;
			}
		}
		if (added < 2) {
			throw new IllegalStateException(text("tournament_need_fleets"));
		}
		tournamentStore.save(tournament);
		selected = tournament;
		status = text("tournament_created");
	}

	private void startTournament() throws IOException, JSONException {
		if (selected == null || selected.isStarted()) {
			return;
		}
		if (rulesStore.get(selected.getRulesSlot()) == null) {
			status = text("tournament_rules_unavailable");
			return;
		}
		engine.start(selected);
		tournamentStore.save(selected);
		status = text("tournament_started");
	}

	private int firstFreeSlot() {
		for (int slot = 0; slot < 32; slot++) {
			if (tournamentStore.get(slot) == null) {
				return slot;
			}
		}
		return -1;
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_tournaments"));
		BattleNext_PageLayout.button(listContent, text("tournament_create"), "tournament:create", 220f);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " [" + tournament.getBracketType() + ", "
					+ tournament.getMatchFormat() + "]";
			BattleNext_PageLayout.button(listContent, label, "tournament:select:" + tournament.getSlot(),
					listContent.getPosition().getWidth() - 20f);
		}
	}

	private void addDetails(TooltipMakerAPI detailContent) {
		if (!status.isEmpty()) detailContent.addPara(status, 0f, BattleNext_UiTheme.MUTED);
		if (selected == null) {
			detailContent.addTitle(text("select_tournament"));
			return;
		}
		detailContent.addTitle(selected.getName());
		detailContent.addPara(selected.getBracketType() + " / " + selected.getMatchFormat() + " / "
				+ selected.getTeams().size() + " " + text("teams"), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(selected.isStarted() ? text("tournament_locked")
				: text("tournament_editable"), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("tournament_rules") + ": "
				+ (rulesStore.get(selected.getRulesSlot()) == null
				? text("tournament_rules_unavailable") : text("tournament_rules_ready")),
				8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("teams"), 10f, BattleNext_UiTheme.ACCENT);
		for (String team : selected.getTeams()) {
			detailContent.addPara(team, 6f, BattleNext_UiTheme.TEXT);
		}
		detailContent.addPara(text("matches"), 10f, BattleNext_UiTheme.ACCENT);
		for (BattleNext_Match match : selected.getMatches()) {
			detailContent.addPara(match.getId() + " " + match.getPlayer() + " / " + match.getEnemy()
					+ " [" + match.getStatus() + "]", 6f, BattleNext_UiTheme.TEXT);
		}
		if (selected.getSlot() == tournamentStore.getActiveSlot()) {
			detailContent.addPara(text("tournament_active"), 8f, BattleNext_UiTheme.ACCENT);
			BattleNext_PageLayout.button(detailContent, text("tournament_deactivate"),
					"tournament:deactivate", 220f);
		} else {
			BattleNext_PageLayout.button(detailContent, text("tournament_activate"),
					"tournament:activate", 220f);
		}
		if (!selected.isStarted()) {
			BattleNext_PageLayout.button(detailContent, text("tournament_start"),
					"tournament:start", 220f);
		}
	}

	private String text(String key) {
		return strings.get(key);
	}
}
