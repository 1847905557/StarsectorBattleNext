package data.scripts.match;

import com.fs.starfarer.api.Global;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_MatchListProvider {

	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_TournamentStore tournamentStore;
	private final List<String> warnings = new ArrayList<>();
	private BattleNext_Tournament activeTournament;

	public BattleNext_MatchListProvider() {
		fleetStore = new BattleNext_FleetStore();
		tournamentStore = new BattleNext_TournamentStore();
	}

	public List<BattleNext_Match> load() {
		warnings.clear();
		activeTournament = null;
		fleetStore.reload();
		try {
			tournamentStore.reload();
		} catch (Exception exception) {
			warnings.add("Unable to load tournament state: " + exception.getMessage());
		}

		int activeSlot = tournamentStore.getActiveSlot();
		if (activeSlot >= 0) {
			BattleNext_Tournament tournament = tournamentStore.get(activeSlot);
			if (tournament == null) {
				warnings.add("Active tournament slot is missing: " + activeSlot);
				return List.of();
			}
			activeTournament = tournament;
			return validate(tournament.getMatches(), "tournament slot " + activeSlot);
		}

		BattleNext_MatchBook book = new BattleNext_MatchBook();
		try {
			book.load();
		} catch (Exception exception) {
			warnings.add("Unable to load user quick battle list: " + exception.getMessage());
		}
		if (book.getMatches().isEmpty()) {
			try {
				book.loadBuiltIn();
			} catch (Exception exception) {
				warnings.add("Unable to load built-in quick battle list: " + exception.getMessage());
			}
		}
		return validate(book.getMatches(), "quick battle");
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

	public boolean hasLegacyMatches() {
		try {
			Global.getSettings().loadText("tournament/PvP_matches.csv", "AI_Battles_Next");
			return true;
		} catch (Exception exception) {
			return false;
		}
	}

	private List<BattleNext_Match> validate(List<BattleNext_Match> source, String sourceName) {
		List<BattleNext_Match> result = new ArrayList<>();
		for (BattleNext_Match match : source) {
			BattleNext_FleetModel player = fleetStore.get(match.getPlayer());
			BattleNext_FleetModel enemy = fleetStore.get(match.getEnemy());
			if (player == null || enemy == null) {
				warnings.add("Skipping " + sourceName + " match " + match.getId() + ": missing player/enemy fleet reference");
				continue;
			}
			result.add(match);
		}
		return result;
	}
}
