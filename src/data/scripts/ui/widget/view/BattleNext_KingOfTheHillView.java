package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_StartedTournament;

import java.util.List;
import java.util.Map;

public class BattleNext_KingOfTheHillView extends BattleNext_TournamentView {

	public BattleNext_KingOfTheHillView(BattleNext_TournamentModel tournament, BattleNext_StartedTournament started,
			List<String> fleetIds, Map<String, String> fleetNames) {
		super(tournament, started, fleetIds, fleetNames);
	}

	@Override
	protected void buildLayout() {
		int count = fleetIds.size();

		for (int index = 0; index < count - 1; index++) {
			Participant player = index == 0
					? seededFleet(1)
					: source("K-" + index, false);
			Participant enemy = seededFleet(index + 2);
			addMatchNode("K-" + (index + 1), BattleNext_Match.MatchStage.KING_OF_THE_HILL,
					player, enemy, 40f + index * 300f, 240f, NODE_WIDTH, NODE_HEIGHT, false);
		}
	}
}
