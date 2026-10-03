package data.scripts.battle;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

public final class BattleNext_MatchProgress {

	private final int matchIndex;
	private final int matchTotal;
	private final int roundIndex;
	private final int roundTotal;
	private final int boCount;

	private BattleNext_MatchProgress(int matchIndex, int matchTotal, int roundIndex, int roundTotal, int boCount) {
		this.matchIndex = matchIndex;
		this.matchTotal = matchTotal;
		this.roundIndex = roundIndex;
		this.roundTotal = roundTotal;
		this.boCount = boCount;
	}

	public static BattleNext_MatchProgress quickBattle(int matchIndex, int matchTotal, int roundIndex) {
		return new BattleNext_MatchProgress(matchIndex, matchTotal, roundIndex, 2, -1);
	}

	public static BattleNext_MatchProgress tournament(BattleNext_TournamentModel tournament, BattleNext_Match match) {
		return tournament(tournament, match, match.getRounds().size() + 1);
	}

	public static BattleNext_MatchProgress tournament(BattleNext_TournamentModel tournament, BattleNext_Match match,
			int roundIndex) {
		int index = tournament.getPlayableMatchIndex(match.getId());
		if (index < 0) throw new IllegalArgumentException("Match is not a playable tournament match");
		return new BattleNext_MatchProgress(index + 1, tournament.getPlayableMatchTotal(),
				roundIndex, tournament.getBoCount(), tournament.getBoCount());
	}

	public String format(BattleNext_I18nSection strings) {
		String result = strings.format("match_progress", matchIndex, matchTotal) + ", "
				+ strings.format("round_progress", roundIndex, roundTotal);
		return boCount < 0 ? result : result + ", " + strings.format("best_of_format", boCount);
	}
}
