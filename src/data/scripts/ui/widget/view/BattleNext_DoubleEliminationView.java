package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;

import java.util.Map;

public class BattleNext_DoubleEliminationView extends BattleNext_TournamentView {

	public BattleNext_DoubleEliminationView(BattleNext_TournamentModel tournament,
			Map<String, String> fleetNames) {
		super(tournament, fleetNames);
	}

	@Override
	protected void buildLayout() {

		int width = 2;

		while (width < tournament.getFleetIds().size()) {
			width *= 2;
		}
		int levels = Integer.numberOfTrailingZeros(width);
		int loserRounds = Math.max(1, levels * 2 - 2);
		buildWinners(width, BattleNext_Match.MatchStage.WINNERS);
		float loserTop = 140f + width * 55f;
		int openingCount = Math.max(1, width / 4);

		for (int index = 0; index < openingCount; index++) {
			Participant player = source("W1-" + (index * 2 + 1), true);
			Participant enemy = width == 2 ? new Participant("", "", false, true)
					: source("W1-" + (index * 2 + 2), true);
			addMatchNode("L1-" + (index + 1), BattleNext_Match.MatchStage.LOSERS, player, enemy,
					40f, loserTop + index * 110f, NODE_WIDTH, NODE_HEIGHT, false);
		}
		for (int winnerRound = 2; winnerRound <= levels; winnerRound++) {
			int loserRound = winnerRound * 2 - 2;
			int count = width >> winnerRound;

			for (int index = 0; index < count; index++) {
				addMatchNode("L" + loserRound + "-" + (index + 1), BattleNext_Match.MatchStage.LOSERS,
						source("L" + (loserRound - 1) + "-" + (index + 1), false),
						source("W" + winnerRound + "-" + (index + 1), true),
						40f + (loserRound - 1) * 300f, loserTop + index * 110f * (1 << (winnerRound - 2)),
						NODE_WIDTH, NODE_HEIGHT, false);
			}
			if (winnerRound == levels) continue;

			for (int index = 0; index < count / 2; index++) {
				addMatchNode("L" + (loserRound + 1) + "-" + (index + 1), BattleNext_Match.MatchStage.LOSERS,
						source("L" + loserRound + "-" + (index * 2 + 1), false),
						source("L" + loserRound + "-" + (index * 2 + 2), false),
						40f + loserRound * 300f,
						loserTop + (index * 2 + 0.5f) * 110f * (1 << (winnerRound - 2)),
						NODE_WIDTH, NODE_HEIGHT, false);
			}
		}
		float finalX = 40f + Math.max(levels, loserRounds) * 300f;
		float finalY = (19f + width * 27.5f + loserTop) * 0.5f;

		addMatchNode("GF", BattleNext_Match.MatchStage.GRAND_FINAL,
				source("W" + levels + "-1", false), source("L" + loserRounds + "-1", false),
				finalX, finalY, NODE_WIDTH, NODE_HEIGHT, false);
		addMatchNode("GF_RESET", BattleNext_Match.MatchStage.GRAND_FINAL_RESET,
				source("GF", true), source("GF", false),
				finalX + 300f, finalY, NODE_WIDTH, NODE_HEIGHT, false);
	}
}
