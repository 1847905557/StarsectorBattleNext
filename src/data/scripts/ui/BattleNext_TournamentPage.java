package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.rules.BattleNext_RulesStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tournament.BattleNext_TournamentStore;

import java.io.IOException;
import java.util.List;

public class BattleNext_TournamentPage {

	private final Runnable backAction;
	private final BattleNext_TournamentStore tournamentStore = new BattleNext_TournamentStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_RulesStore rulesStore = new BattleNext_RulesStore();
	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
	private CustomPanelAPI panel;
	private TooltipMakerAPI content;
	private BattleNext_Tournament selected;
	private String status = "";

	public BattleNext_TournamentPage(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
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
			throw new IllegalStateException(text("BattleNext_ui_tournament_no_slot"));
		}
		BattleNext_Tournament tournament = new BattleNext_Tournament(slot, "Tournament " + slot, "single_elim", "BO1", 2);
		int added = 0;
		for (BattleNext_FleetModel fleet : fleetStore.getAll()) {
			tournament.addTeam(fleet.getId());
			added++;
			if (added == 2) {
				break;
			}
		}
		if (added < 2) {
			throw new IllegalStateException(text("BattleNext_ui_tournament_need_fleets"));
		}
		tournamentStore.save(tournament);
		selected = tournament;
		status = text("BattleNext_ui_tournament_created");
	}

	private void startTournament() throws IOException, org.json.JSONException {
		if (selected == null || selected.isStarted()) {
			return;
		}
		if (rulesStore.get(selected.getRulesSlot()) == null) {
			status = text("BattleNext_ui_tournament_rules_unavailable");
			return;
		}
		engine.start(selected);
		tournamentStore.save(selected);
		status = text("BattleNext_ui_tournament_started");
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
		if (content != null) {
			panel.removeComponent(content);
		}
		content = panel.createUIElement(720f, 480f, false);
		BattleNext_UiTheme.configure(content);
		content.addTitle(text("BattleNext_ui_page_tournaments"));
		if (!status.isEmpty()) {
			content.addPara(status, 8f, BattleNext_UiTheme.MUTED);
		}
		if (selected == null) {
			addList();
		} else {
			addDetails();
		}
		content.addButton(text("BattleNext_ui_back"), "back", BattleNext_UiTheme.ACCENT,
				BattleNext_UiTheme.PANEL, 160f, 30f, 8f);
		panel.addUIElement(content).inMid();
	}

	private void addList() {
		content.addButton(text("BattleNext_ui_tournament_create"), "tournament:create",
				BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 220f, 30f, 6f);
		for (BattleNext_Tournament tournament : tournamentStore.getAll()) {
			String label = tournament.getName() + " [" + tournament.getBracketType() + ", "
					+ tournament.getMatchFormat() + "]";
			content.addButton(label, "tournament:select:" + tournament.getSlot(), BattleNext_UiTheme.ACCENT,
					BattleNext_UiTheme.PANEL, 460f, 30f, 6f);
		}
	}

	private void addDetails() {
		content.addPara(selected.getName(), 10f, BattleNext_UiTheme.TEXT);
		content.addPara(selected.getBracketType() + " / " + selected.getMatchFormat() + " / "
				+ selected.getTeams().size() + " teams", 8f, BattleNext_UiTheme.MUTED);
		content.addPara(selected.isStarted() ? text("BattleNext_ui_tournament_locked")
				: text("BattleNext_ui_tournament_editable"), 8f, BattleNext_UiTheme.MUTED);
		content.addPara(text("BattleNext_ui_tournament_rules") + ": "
				+ (rulesStore.get(selected.getRulesSlot()) == null
				? text("BattleNext_ui_tournament_rules_unavailable") : text("BattleNext_ui_tournament_rules_ready")),
				8f, BattleNext_UiTheme.MUTED);
		for (String team : selected.getTeams()) {
			content.addPara(team, 6f, BattleNext_UiTheme.TEXT);
		}
		for (data.scripts.match.BattleNext_Match match : selected.getMatches()) {
			content.addPara(match.getId() + " " + match.getPlayer() + " / " + match.getEnemy()
					+ " [" + match.getStatus() + "]", 6f, BattleNext_UiTheme.TEXT);
		}
		if (selected.getSlot() == tournamentStore.getActiveSlot()) {
			content.addPara(text("BattleNext_ui_tournament_active"), 8f, BattleNext_UiTheme.ACCENT);
			content.addButton(text("BattleNext_ui_tournament_deactivate"), "tournament:deactivate",
					BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 220f, 30f, 6f);
		} else {
			content.addButton(text("BattleNext_ui_tournament_activate"), "tournament:activate",
					BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 220f, 30f, 6f);
		}
		if (!selected.isStarted()) {
			content.addButton(text("BattleNext_ui_tournament_start"), "tournament:start",
					BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 220f, 30f, 6f);
		}
	}

	private String text(String key) {
		try {
			return Global.getSettings().getString("AI_Battles_Next", key);
		} catch (RuntimeException exception) {
			return key;
		}
	}
}
