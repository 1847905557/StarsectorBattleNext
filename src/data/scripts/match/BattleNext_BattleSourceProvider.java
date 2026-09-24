package data.scripts.match;

import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tournament.BattleNext_TournamentStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleSourceProvider {

	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_TournamentStore tournamentStore;
	private final BattleNext_TournamentEngine tournamentEngine;
	private final List<String> warnings = new ArrayList<>();
	private BattleNext_Tournament activeTournament;

	public BattleNext_BattleSourceProvider() {
		fleetStore = new BattleNext_FleetStore();
		tournamentStore = new BattleNext_TournamentStore();
		tournamentEngine = new BattleNext_TournamentEngine();
	}

	public List<BattleNext_Match> load() {
		warnings.clear();
		activeTournament = null;
		fleetStore.reload();
		try {
			tournamentStore.reload();
		} catch (Exception exception) {
			warnings.add("Unable to load tournament state: " + exception.getMessage());
			return List.of();
		}

		int activeSlot = tournamentStore.getActiveSlot();
		if (activeSlot >= 0) {
			BattleNext_Tournament tournament = tournamentStore.get(activeSlot);
			if (tournament == null) {
				warnings.add("Active tournament slot is missing: " + activeSlot);
				return List.of();
			}
			activeTournament = tournament;
			if (!tournament.isStarted() || tournament.isCompleted()) {
				return List.of();
			}
			BattleNext_Match next = tournamentEngine.getNextMatch(tournament);
			return next == null ? List.of() : validate(List.of(next), "tournament slot " + activeSlot);
		}

		BattleNext_QuickBattleStore quickBattleStore = new BattleNext_QuickBattleStore();
		boolean userFile = quickBattleStore.hasUserFile();
		try {
			if (userFile) {
				quickBattleStore.load();
			} else {
				quickBattleStore.loadBuiltIn();
			}
		} catch (Exception exception) {
			warnings.add("Unable to load " + (userFile ? "user" : "built-in")
					+ " quick battle list: " + exception.getMessage());
		}
		return validate(quickBattleStore.getMatches(), "quick battle");
	}

	public List<String> getWarnings() {
		return Collections.unmodifiableList(warnings);
	}

	public BattleNext_FleetStore getFleetStore() {
		return fleetStore;
	}

	public BattleNext_Tournament getActiveTournament() {
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
