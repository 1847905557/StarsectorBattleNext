package data.scripts.tournament;

import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Match.MatchStage;
import data.scripts.battle.BattleNext_Match.MatchStatus;
import data.scripts.battle.BattleNext_Round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BattleNext_TournamentEngine {

	public void start(BattleNext_TournamentModel tournament) {
		if (tournament.isStarted()) throw new IllegalStateException("Tournament has already started");
		validate(tournament);
		tournament.clearGeneratedMatches();
		tournament.setStarted(true);
		tournament.setCompleted(false);
		tournament.setChampionFleetId("");
		createOpeningMatches(tournament, Map.of());
		reconcile(tournament, Map.of());
	}

	public BattleNext_Match getNextMatch(BattleNext_TournamentModel tournament) {
		if (!tournament.isStarted() || tournament.isCompleted()) return null;
		BattleNext_Match match = tournament.findMatch(tournament.getPendingMatchId());
		if (match != null && match.getStatus() == MatchStatus.PENDING) return match;
		selectPendingMatch(tournament);
		return tournament.findMatch(tournament.getPendingMatchId());
	}

	public void recordRound(BattleNext_TournamentModel tournament, String matchId, String winner,
			BattleNext_BattleReportModel report) {
		BattleNext_Match match = tournament.findMatch(matchId);
		if (!tournament.isStarted() || tournament.isCompleted() || match == null || match.getStatus() != MatchStatus.PENDING) {
			throw new IllegalStateException("Tournament match is unavailable");
		}
		BattleNext_Round.RoundStatus result = resultFor(winner, match);
		BattleNext_Round round = new BattleNext_Round(match.getPlayer(), match.getEnemy());
		round.setStatus(result);
		round.setReport(report);
		match.addRound(round);
		if (result == BattleNext_Round.RoundStatus.DRAW) return;
		int winsNeeded = tournament.getWinTarget();
		if (match.getPlayerWins() >= winsNeeded || match.getEnemyWins() >= winsNeeded) {
			match.setStatus(MatchStatus.COMPLETE);
			reconcile(tournament, Map.of());
			return;
		}
		match.setSides(match.getEnemy(), match.getPlayer());
	}

	public void rollback(BattleNext_TournamentModel tournament, String matchId) {
		if (tournament.findMatch(matchId) == null) {
			throw new IllegalArgumentException("Unknown tournament match: " + matchId);
		}
		Set<String> affected = new HashSet<>();
		affected.add(matchId);
		boolean changed;
		do {
			changed = false;
			for (BattleNext_Match match : tournament.getMatches()) {
				for (String dependency : match.getDependencies()) {
					if (affected.contains(dependency) && affected.add(match.getId())) changed = true;
				}
			}
		} while (changed);
		Map<String, BattleNext_Match> preserved = new HashMap<>();
		for (BattleNext_Match match : tournament.getMatches()) {
			if (!affected.contains(match.getId())) preserved.put(match.getId(), match);
		}
		tournament.clearGeneratedMatches();
		tournament.setCompleted(false);
		tournament.setChampionFleetId("");
		createOpeningMatches(tournament, preserved);
		reconcile(tournament, preserved);
	}

	private void validate(BattleNext_TournamentModel tournament) {
		List<String> fleets = tournament.getFleetIds();
		if (tournament.getBracketType() == null || tournament.getBoCount() < 1
				|| tournament.getMaxFleet() < 2 || tournament.getMaxFleet() > 32
				|| fleets.size() < 2 || fleets.size() > tournament.getMaxFleet()) {
			throw new IllegalArgumentException("Invalid tournament configuration");
		}
		Set<String> seen = new HashSet<>();
		for (String fleet : fleets) {
			if (fleet.isEmpty() || !seen.add(fleet)) {
				throw new IllegalArgumentException("Tournament fleets must be unique");
			}
		}
	}

	private void createOpeningMatches(BattleNext_TournamentModel tournament, Map<String, BattleNext_Match> preserved) {
		List<String> fleets = tournament.getFleetIds();
		if (tournament.getBracketType() == TournamentBracketType.ROUND_ROBIN) {
			for (int first = 0; first < fleets.size(); first++) {
				for (int second = first + 1; second < fleets.size(); second++) {
					addMatch(tournament, "RR-" + first + "-" + second, new BracketFleetParticipant(fleets.get(first), ""),
							new BracketFleetParticipant(fleets.get(second), ""), MatchStage.ROUND_ROBIN, preserved);
				}
			}
			return;
		}
		if (tournament.getBracketType() == TournamentBracketType.KING_OF_THE_HILL) {
			addMatch(tournament, "K-1", new BracketFleetParticipant(fleets.get(0), ""), new BracketFleetParticipant(fleets.get(1), ""),
					MatchStage.KING_OF_THE_HILL, preserved);
			return;
		}
		int width = bracketWidth(fleets.size());
		List<Integer> seedOrder = new ArrayList<>(List.of(1, 2));
		for (int bracket = 4; bracket <= width; bracket *= 2) {
			List<Integer> next = new ArrayList<>();
			for (int seed : seedOrder) {
				next.add(seed);
				next.add(bracket + 1 - seed);
			}
			seedOrder = next;
		}
		List<BracketFleetParticipant> entries = new ArrayList<>();
		for (int seed : seedOrder) {
			entries.add(new BracketFleetParticipant(seed <= fleets.size() ? fleets.get(seed - 1) : "", ""));
		}
		pair(tournament, "W1", entries, tournament.getBracketType() == TournamentBracketType.DOUBLE_ELIMINATION
				? MatchStage.WINNERS : MatchStage.MAIN, preserved);
	}

	private void reconcile(BattleNext_TournamentModel tournament, Map<String, BattleNext_Match> preserved) {
		boolean changed;
		do {
			int count = tournament.getMatches().size();
			TournamentBracketType type = tournament.getBracketType();
			if (type == TournamentBracketType.ROUND_ROBIN) {
				if (allComplete(tournament.getMatches())) finish(tournament, roundRobinChampion(tournament));
			} else if (type == TournamentBracketType.KING_OF_THE_HILL) {
				advanceKof(tournament, preserved);
			} else {
				advanceBracket(tournament, type == TournamentBracketType.DOUBLE_ELIMINATION, preserved);
			}
			changed = tournament.getMatches().size() != count;
		} while (changed);
		selectPendingMatch(tournament);
	}

	private void advanceKof(BattleNext_TournamentModel tournament, Map<String, BattleNext_Match> preserved) {
		int index = tournament.getMatches().size();
		BattleNext_Match last = tournament.findMatch("K-" + index);
		if (last == null || last.getStatus() != MatchStatus.COMPLETE) return;
		if (index + 1 >= tournament.getFleetIds().size()) {
			finish(tournament, last.getWinnerFleetId());
			return;
		}
		addMatch(tournament, "K-" + (index + 1), new BracketFleetParticipant(last.getWinnerFleetId(), last.getId()),
				new BracketFleetParticipant(tournament.getFleetIds().get(index + 1), ""), MatchStage.KING_OF_THE_HILL, preserved);
	}

	private void advanceBracket(BattleNext_TournamentModel tournament, boolean doubleElim,
			Map<String, BattleNext_Match> preserved) {
		int width = bracketWidth(tournament.getFleetIds().size());
		int levels = Integer.numberOfTrailingZeros(width);
		for (int round = 1; round < levels; round++) {
			if (roundComplete(tournament, "W" + round, width >> round)
					&& tournament.findMatch("W" + (round + 1) + "-1") == null) {
				pair(tournament, "W" + (round + 1), winners(tournament, "W" + round, width >> round),
						doubleElim ? MatchStage.WINNERS : MatchStage.MAIN, preserved);
			}
		}
		if (!doubleElim) {
			if (roundComplete(tournament, "W" + levels, 1)) {
				finish(tournament, tournament.findMatch("W" + levels + "-1").getWinnerFleetId());
			}
			return;
		}
		if (roundComplete(tournament, "W1", width / 2) && tournament.findMatch("L1-1") == null) {
			pair(tournament, "L1", losers(tournament, "W1", width / 2), MatchStage.LOSERS, preserved);
		}
		for (int round = 2; round <= levels; round++) {
			int previousLoserRound = round == 2 ? 1 : 2 * round - 3;
			int previousCount = Math.max(1, width >> round);
			int loserRound = 2 * round - 2;
			if (roundComplete(tournament, "W" + round, width >> round)
					&& roundComplete(tournament, "L" + previousLoserRound, previousCount)
					&& tournament.findMatch("L" + loserRound + "-1") == null) {
				List<BracketFleetParticipant> survivors = winners(tournament, "L" + previousLoserRound, previousCount);
				List<BracketFleetParticipant> fallen = losers(tournament, "W" + round, width >> round);
				for (int index = 0; index < fallen.size(); index++) {
					addMatch(tournament, "L" + loserRound + "-" + (index + 1), survivors.get(index),
							fallen.get(index), MatchStage.LOSERS, preserved);
				}
			}
			if (round < levels && roundComplete(tournament, "L" + loserRound, width >> round)
					&& tournament.findMatch("L" + (loserRound + 1) + "-1") == null) {
				pair(tournament, "L" + (loserRound + 1), winners(tournament, "L" + loserRound, width >> round),
						MatchStage.LOSERS, preserved);
			}
		}
		int finalLoserRound = Math.max(1, 2 * levels - 2);
		if (roundComplete(tournament, "W" + levels, 1)
				&& roundComplete(tournament, "L" + finalLoserRound, 1)
				&& tournament.findMatch("GF") == null) {
			addMatch(tournament, "GF", winner(tournament.findMatch("W" + levels + "-1")),
					winner(tournament.findMatch("L" + finalLoserRound + "-1")), MatchStage.GRAND_FINAL, preserved);
		}
		BattleNext_Match grandFinal = tournament.findMatch("GF");
		if (grandFinal == null || grandFinal.getStatus() != MatchStatus.COMPLETE) return;
		String winnersChampion = tournament.findMatch("W" + levels + "-1").getWinnerFleetId();
		if (winnersChampion.equals(grandFinal.getWinnerFleetId())) {
			finish(tournament, winnersChampion);
			return;
		}
		if (tournament.findMatch("GF_RESET") == null) {
			addMatch(tournament, "GF_RESET", new BracketFleetParticipant(winnersChampion, "GF"),
					new BracketFleetParticipant(grandFinal.getWinnerFleetId(), "GF"), MatchStage.GRAND_FINAL_RESET, preserved);
		}
		BattleNext_Match reset = tournament.findMatch("GF_RESET");
		if (reset.getStatus() == MatchStatus.COMPLETE) finish(tournament, reset.getWinnerFleetId());
	}

	private void pair(BattleNext_TournamentModel tournament, String prefix, List<BracketFleetParticipant> entries,
			MatchStage stage, Map<String, BattleNext_Match> preserved) {
		for (int index = 0; index < entries.size(); index += 2) {
			BracketFleetParticipant second = index + 1 < entries.size() ? entries.get(index + 1) : new BracketFleetParticipant("", "");
			addMatch(tournament, prefix + "-" + (index / 2 + 1), entries.get(index), second, stage, preserved);
		}
	}

	private void addMatch(BattleNext_TournamentModel tournament, String id, BracketFleetParticipant first, BracketFleetParticipant second,
			MatchStage stage, Map<String, BattleNext_Match> preserved) {
		BracketFleetParticipant player = first.fleetId.isEmpty() ? second : first;
		BracketFleetParticipant enemy = first.fleetId.isEmpty() ? first : second;
		BattleNext_Match match = new BattleNext_Match(id, player.fleetId, enemy.fleetId, tournament.getMapId(), 1f);
		match.setStage(enemy.fleetId.isEmpty() ? MatchStage.BYE : stage);
		match.addDependency(first.sourceId);
		match.addDependency(second.sourceId);
		if (enemy.fleetId.isEmpty()) match.setStatus(MatchStatus.COMPLETE);
		BattleNext_Match old = preserved.get(id);
		if (old != null && old.getStage() == match.getStage()
				&& initialPlayer(old).equals(match.getPlayer()) && initialEnemy(old).equals(match.getEnemy())
				&& old.getDependencies().equals(match.getDependencies())) {
			match = old;
		}
		tournament.addGeneratedMatch(match);
	}

	private String initialPlayer(BattleNext_Match match) {
		return match.getRounds().isEmpty() ? match.getPlayer() : match.getRounds().get(0).getPlayer();
	}

	private String initialEnemy(BattleNext_Match match) {
		return match.getRounds().isEmpty() ? match.getEnemy() : match.getRounds().get(0).getEnemy();
	}

	private boolean roundComplete(BattleNext_TournamentModel tournament, String prefix, int count) {
		for (int index = 1; index <= count; index++) {
			BattleNext_Match match = tournament.findMatch(prefix + "-" + index);
			if (match == null || match.getStatus() != MatchStatus.COMPLETE) return false;
		}
		return true;
	}

	private List<BracketFleetParticipant> winners(BattleNext_TournamentModel tournament, String prefix, int count) {
		List<BracketFleetParticipant> result = new ArrayList<>();
		for (int index = 1; index <= count; index++) result.add(winner(tournament.findMatch(prefix + "-" + index)));
		return result;
	}

	private List<BracketFleetParticipant> losers(BattleNext_TournamentModel tournament, String prefix, int count) {
		List<BracketFleetParticipant> result = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			BattleNext_Match match = tournament.findMatch(prefix + "-" + index);
			result.add(new BracketFleetParticipant(match.getLoserFleetId(), match.getId()));
		}
		return result;
	}

	private BracketFleetParticipant winner(BattleNext_Match match) {
		return new BracketFleetParticipant(match.getWinnerFleetId(), match.getId());
	}

	private String roundRobinChampion(BattleNext_TournamentModel tournament) {
		List<String> fleets = tournament.getFleetIds();
		Map<String, Integer> wins = new HashMap<>();
		for (BattleNext_Match match : tournament.getMatches()) wins.merge(match.getWinnerFleetId(), 1, Integer::sum);
		int best = -1;
		for (String fleet : fleets) best = Math.max(best, wins.getOrDefault(fleet, 0));
		Set<String> tied = new HashSet<>();
		for (String fleet : fleets) {
			if (wins.getOrDefault(fleet, 0) == best) tied.add(fleet);
		}
		String champion = "";
		int headToHead = -1;
		for (String fleet : fleets) {
			if (!tied.contains(fleet)) continue;
			int score = 0;
			for (BattleNext_Match match : tournament.getMatches()) {
				if (fleet.equals(match.getWinnerFleetId()) && tied.contains(match.getLoserFleetId())) score++;
			}
			// Three-way cycles are rare; seed order resolves a remaining tie.
			if (score > headToHead) {
				champion = fleet;
				headToHead = score;
			}
		}
		return champion;
	}

	private boolean allComplete(List<BattleNext_Match> matches) {
		for (BattleNext_Match match : matches) {
			if (match.getStatus() != MatchStatus.COMPLETE) return false;
		}
		return true;
	}

	private int bracketWidth(int count) {
		int width = 2;
		while (width < count) width *= 2;
		return width;
	}

	private BattleNext_Round.RoundStatus resultFor(String winner, BattleNext_Match match) {
		if (winner == null || winner.isEmpty() || "draw".equals(winner)) return BattleNext_Round.RoundStatus.DRAW;
		if (winner.equals(match.getPlayer())) return BattleNext_Round.RoundStatus.PLAYER_WIN;
		if (winner.equals(match.getEnemy())) return BattleNext_Round.RoundStatus.ENEMY_WIN;
		throw new IllegalArgumentException("Winner is not a participant in the match");
	}

	private void finish(BattleNext_TournamentModel tournament, String champion) {
		tournament.setChampionFleetId(champion);
		tournament.setCompleted(true);
	}

	private void selectPendingMatch(BattleNext_TournamentModel tournament) {
		if (!tournament.isCompleted()) {
			for (BattleNext_Match match : tournament.getMatches()) {
				if (match.getStatus() == MatchStatus.PENDING) {
					tournament.setPendingMatchId(match.getId());
					return;
				}
			}
		}
		tournament.setPendingMatchId("");
	}

	private static class BracketFleetParticipant {
		private final String fleetId;
		private final String sourceId;

		private BracketFleetParticipant(String fleetId, String sourceId) {
			this.fleetId = fleetId;
			this.sourceId = sourceId;
		}
	}
}
