package data.scripts.battle;

import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.model.tournament.BattleNext_TournamentStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleSourceProvider {

	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_TournamentStore tournamentStore;
	private final BattleNext_TournamentEngine tournamentEngine;
	private final List<String> warnings = new ArrayList<>();
	private BattleNext_TournamentModel activeTournament;

	public BattleNext_BattleSourceProvider() {
		fleetStore = new BattleNext_FleetStore();
		tournamentStore = new BattleNext_TournamentStore();
		tournamentEngine = new BattleNext_TournamentEngine();
	}

	public List<BattleNext_Match> load() {
		warnings.clear();
		activeTournament = null;
		try {
			fleetStore.reload();
			tournamentStore.reload();
		} catch (Exception exception) {
			warnings.add("Unable to load battle sources: " + exception.getMessage());
		}

		String activeId = tournamentStore.getActiveId();
		if (!activeId.isEmpty()) {
			BattleNext_TournamentModel tournament = tournamentStore.get(activeId);
			if (tournament == null) {
				warnings.add("Active tournament is missing: " + activeId);
				return List.of();
			}
			activeTournament = tournament;
			if (!tournament.isStarted() || tournament.isCompleted()) {
				return List.of();
			}
			BattleNext_Match next = tournamentEngine.getNextMatch(tournament);
			return next == null ? List.of() : validate(List.of(next), "tournament " + activeId);
		}

		BattleNext_QuickBattleStore quickBattleStore = new BattleNext_QuickBattleStore();
		try {
			quickBattleStore.reload();
		} catch (Exception exception) {
			warnings.add("Unable to load quick battle list: " + exception.getMessage());
		}
		List<BattleNext_Match> matches = new ArrayList<>();
		for (BattleNext_QuickBattleModel model : quickBattleStore.getAll()) {
			matches.add(BattleNext_Match.fromQuickBattle(model));
		}
		return validate(matches, "quick battle");
	}

	public List<String> getWarnings() {
		return Collections.unmodifiableList(warnings);
	}

	public BattleNext_FleetStore getFleetStore() {
		return fleetStore;
	}

	public BattleNext_TournamentModel getActiveTournament() {
		return activeTournament;
	}

	public BattleNext_TournamentStore getTournamentStore() {
		return tournamentStore;
	}

	public BattleNext_TournamentEngine getTournamentEngine() {
		return tournamentEngine;
	}

	private List<BattleNext_Match> validate(List<BattleNext_Match> source, String sourceName) {
		List<BattleNext_Match> result = new ArrayList<>();
		for (BattleNext_Match match : source) {
			BattleNext_FleetModel player = fleetStore.get(match.getPlayer());
			BattleNext_FleetModel enemy = fleetStore.get(match.getEnemy());
			if (player == null || enemy == null) {
				warnings.add("Skipping " + sourceName + " " + (activeTournament == null ? "battle" : "match")
						+ " " + match.getId() + ": missing player/enemy fleet reference");
				continue;
			}
			result.add(match);
		}
		return result;
	}
}
