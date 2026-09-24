package data.scripts.tournament;

import data.scripts.report.BattleNext_BattleReport;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_TournamentEngine {

	public void start(BattleNext_Tournament tournament) {
		if (tournament.isStarted()) {
			throw new IllegalStateException("Tournament has already started");
		}
		validateTeams(tournament);
		tournament.clearMatches();
		generateMatches(tournament);
		if ("kof".equals(tournament.getBracketType())) {
			tournament.setKofNextIndex(2);
		}
		tournament.setStarted(true);
		tournament.setCompleted(false);
		tournament.setChampion("");
		selectPendingMatch(tournament);
	}

	public BattleNext_Match getNextMatch(BattleNext_Tournament tournament) {
		String pendingMatchId = tournament.getPendingMatchId();
		if (!pendingMatchId.isEmpty()) {
			BattleNext_Match pending = tournament.findMatch(pendingMatchId);
			if (pending != null && "pending".equals(pending.getStatus())) {
				return pending;
			}
		}
		selectPendingMatch(tournament);
		return tournament.getPendingMatchId().isEmpty() ? null : tournament.findMatch(tournament.getPendingMatchId());
	}

	public void recordRound(BattleNext_Tournament tournament, String matchId, String winner,
			BattleNext_BattleReport report) {
		BattleNext_Match match = tournament.findMatch(matchId);
		if (!tournament.isStarted() || tournament.isCompleted() || match == null) {
			throw new IllegalStateException("Tournament match is unavailable");
		}
		if (!"pending".equals(match.getStatus())) {
			throw new IllegalStateException("Tournament match is already complete");
		}
		String result = normalizeWinner(winner, match);
		BattleNext_Round previous = match.getRounds().isEmpty()
				? null : match.getRounds().get(match.getRounds().size() - 1);
		int number = previous == null ? 1 : previous.getNumber();
		int attempt = previous == null ? 0 : previous.getAttempt() + 1;
		if (previous != null && !"draw".equals(previous.getWinner()) && !match.isReplay()) {
			number++;
			attempt = 0;
		}
		BattleNext_Round round = new BattleNext_Round(number, match.getPlayer(), match.getEnemy());
		round.setWinner(result);
		round.setAttempt(attempt);
		round.setReport(report);
		match.addRound(round);
		if ("draw".equals(result)) {
			return;
		}
		match.setReplay(false);
		if (result.equals(match.getPlayer())) {
			match.addPlayerWin();
		} else {
			match.addEnemyWin();
		}
		int winsNeeded = winsNeeded(tournament.getMatchFormat());
		if (match.getPlayerWins() >= winsNeeded || match.getEnemyWins() >= winsNeeded) {
			match.setStatus("complete");
			advance(tournament, match, result);
			return;
		}
		match.setSides(match.getEnemy(), match.getPlayer());
	}

	public void rollback(BattleNext_Tournament tournament, String matchId) {
		BattleNext_Match target = tournament.findMatch(matchId);
		if (target == null) {
			throw new IllegalArgumentException("Unknown tournament match: " + matchId);
		}
		boolean reached = false;
		for (BattleNext_Match match : tournament.getMatches()) {
			if (match == target) {
				reached = true;
			}
			if (reached) {
				if (match == target) {
					match.resetProgressForReplay();
				} else {
					match.clearRoundsAfter(1);
					match.setStatus("pending");
				}
			}
		}
		tournament.setCompleted(false);
		tournament.setChampion("");
		tournament.setPendingMatchId(matchId);
	}

	private void validateTeams(BattleNext_Tournament tournament) {
		if (tournament.getTeams().size() < 2 || tournament.getTeams().size() > 32) {
			throw new IllegalArgumentException("Tournament requires between 2 and 32 teams");
		}
		Set<String> seen = new HashSet<>();
		for (String team : tournament.getTeams()) {
			if (team.isEmpty() || !seen.add(team)) {
				throw new IllegalArgumentException("Tournament teams must be non-empty and unique");
			}
		}
	}

	private void generateMatches(BattleNext_Tournament tournament) {
		List<String> teams = tournament.getTeams();
		String bracketType = tournament.getBracketType();
		if ("round_robin".equals(bracketType)) {
			for (int first = 0; first < teams.size(); first++) {
				for (int second = first + 1; second < teams.size(); second++) {
					addMatch(tournament, "round-robin-" + first + "-" + second, teams.get(first), teams.get(second), "round_robin");
				}
			}
			return;
		}
		if ("kof".equals(bracketType)) {
			addMatch(tournament, "kof-1", teams.get(0), teams.get(1), "kof");
			return;
		}
		for (int index = 0; index + 1 < teams.size(); index += 2) {
			String stage = "double_elim".equals(bracketType) ? "winners" : "main";
			addMatch(tournament, stage + "-" + (index / 2 + 1), teams.get(index), teams.get(index + 1), stage);
		}
		if (teams.size() % 2 != 0) {
			addMatch(tournament, "main-bye", teams.get(teams.size() - 1), "", "bye");
		}
	}

	private void addMatch(BattleNext_Tournament tournament, String id, String player, String enemy, String stage) {
		BattleNext_Match match = new BattleNext_Match(id, player, enemy, tournament.getMapId(), 1f);
		match.setStage(stage);
		if ("bye".equals(stage)) {
			match.setStatus("complete");
		}
		tournament.addGeneratedMatch(match);
	}

	private void advance(BattleNext_Tournament tournament, BattleNext_Match completed, String winner) {
		if ("kof".equals(tournament.getBracketType())) {
			advanceKof(tournament, completed, winner);
			return;
		}
		if ("round_robin".equals(tournament.getBracketType())) {
			if (allMatchesComplete(tournament)) {
				tournament.setChampion(findRoundRobinChampion(tournament));
				tournament.setCompleted(true);
			}
			return;
		}
		if (hasPendingMatch(tournament)) {
			return;
		}
		List<String> winners = completedWinners(tournament);
		if (winners.size() == 1) {
			tournament.setChampion(winners.get(0));
			tournament.setCompleted(true);
			return;
		}
		for (int index = 0; index + 1 < winners.size(); index += 2) {
			addMatch(tournament, "final-" + tournament.getMatches().size(), winners.get(index), winners.get(index + 1), "final");
		}
	}

	private void advanceKof(BattleNext_Tournament tournament, BattleNext_Match completed, String winner) {
		int nextIndex = tournament.getKofNextIndex();
		if (nextIndex >= tournament.getTeams().size()) {
			tournament.setChampion(winner);
			tournament.setCompleted(true);
			return;
		}
		tournament.setKofNextIndex(nextIndex + 1);
		String id = "kof-" + (tournament.getMatches().size() + 1);
		addMatch(tournament, id, winner, tournament.getTeams().get(nextIndex), "kof");
	}

	private void selectPendingMatch(BattleNext_Tournament tournament) {
		for (BattleNext_Match match : tournament.getMatches()) {
			if ("pending".equals(match.getStatus()) && !match.getEnemy().isEmpty()) {
				tournament.setPendingMatchId(match.getId());
				return;
			}
		}
		tournament.setPendingMatchId("");
	}

	private boolean hasPendingMatch(BattleNext_Tournament tournament) {
		for (BattleNext_Match match : tournament.getMatches()) {
			if ("pending".equals(match.getStatus()) && !match.getEnemy().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean allMatchesComplete(BattleNext_Tournament tournament) {
		for (BattleNext_Match match : tournament.getMatches()) {
			if (!"complete".equals(match.getStatus())) {
				return false;
			}
		}
		return true;
	}

	private List<String> completedWinners(BattleNext_Tournament tournament) {
		List<String> winners = new ArrayList<>();
		for (BattleNext_Match match : tournament.getMatches()) {
			if (!"complete".equals(match.getStatus()) || match.getRounds().isEmpty()) {
				continue;
			}
			BattleNext_Round round = match.getRounds().get(match.getRounds().size() - 1);
			if (!"draw".equals(round.getWinner())) {
				winners.add(round.getWinner());
			}
		}
		return winners;
	}

	private String findRoundRobinChampion(BattleNext_Tournament tournament) {
		String champion = tournament.getTeams().get(0);
		int bestWins = -1;
		for (String team : tournament.getTeams()) {
			int wins = 0;
			for (BattleNext_Match match : tournament.getMatches()) {
				if (!match.getRounds().isEmpty() && team.equals(match.getRounds().get(match.getRounds().size() - 1).getWinner())) {
					wins++;
				}
			}
			if (wins > bestWins) {
				champion = team;
				bestWins = wins;
			}
		}
		return champion;
	}

	private String normalizeWinner(String winner, BattleNext_Match match) {
		if (winner == null || winner.trim().isEmpty() || "draw".equals(winner)) {
			return "draw";
		}
		if (!winner.equals(match.getPlayer()) && !winner.equals(match.getEnemy())) {
			throw new IllegalArgumentException("Winner is not a participant in the match");
		}
		return winner;
	}

	private int winsNeeded(String matchFormat) {
		if ("BO3".equalsIgnoreCase(matchFormat)) {
			return 2;
		}
		if ("BO5".equalsIgnoreCase(matchFormat)) {
			return 3;
		}
		return 1;
	}
}
