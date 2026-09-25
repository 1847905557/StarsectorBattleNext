package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_PageView;
import data.scripts.ui.BattleNext_UiTheme;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RulesStore rulesStore = new BattleNext_RulesStore();
	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI listContent;
	private TooltipMakerAPI detailContent;
	private TooltipMakerAPI footer;
	private BattleNext_Tournament selected;
	private String status = "";

	public BattleNext_TournamentPage(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		if (this.panel != panel) {
			heading = null;
			listContent = null;
			detailContent = null;
			footer = null;
		}
		this.panel = panel;
		selected = null;
		fleetStore.reload();
		try {
			tournamentStore.reload();
			rulesStore.reload();
		} catch (Exception exception) {
			status = exception.getMessage();
		}
		rebuild();
	}

	public void advance() {
	}

	public void processInput(List<InputEventAPI> events) {
	}

	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String)buttonId).startsWith("tournament:")) {
			handleTournamentAction((String)buttonId);
			return;
		}
		if ("back".equals(buttonId)) {
			if (selected != null) {
				selected = null;
				rebuild();
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
		rebuild();
	}

	private void createTournament() throws IOException, org.json.JSONException {
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

	private void startTournament() throws IOException, org.json.JSONException {
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

	private void rebuild() {
		if (heading != null) panel.removeComponent(heading);
		if (listContent != null) panel.removeComponent(listContent);
		if (detailContent != null) panel.removeComponent(detailContent);
		if (footer != null) panel.removeComponent(footer);

		heading = BattleNext_PageLayout.heading(panel, text("page_tournaments"));
		float listWidth = BattleNext_PageLayout.listWidth(panel);
		listContent = BattleNext_PageLayout.column(panel, 0f, listWidth);
		detailContent = BattleNext_PageLayout.column(panel, listWidth + 10f,
				panel.getPosition().getWidth() - listWidth - 10f);
		footer = BattleNext_PageLayout.footer(panel);
		BattleNext_PageLayout.button(footer, text("back"), "back", 160f)
				.getPosition().inBL(0f, 0f);
		addList();
		addDetails();
	}

	private void addList() {
		listContent.addTitle(text("page_tournaments"));
		BattleNext_PageLayout.button(listContent, text("tournament_create"),
				"tournament:create", 220f);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " [" + tournament.getBracketType() + ", "
					+ tournament.getMatchFormat() + "]";
			BattleNext_PageLayout.button(listContent, label, "tournament:select:" + tournament.getSlot(),
					listContent.getPosition().getWidth() - 20f);
		}
	}

	private void addDetails() {
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
