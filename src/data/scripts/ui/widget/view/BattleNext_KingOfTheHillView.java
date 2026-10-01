package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;

import java.util.Map;

public class BattleNext_KingOfTheHillView extends BattleNext_TournamentView {

	public BattleNext_KingOfTheHillView(BattleNext_TournamentModel tournament,
			Map<String, String> fleetNames) {
		super(tournament, fleetNames);
	}

	@Override
	protected void buildLayout() {
		int count = tournament.getFleetIds().size();

		for (int index = 0; index < count - 1; index++) {
			Participant player = index == 0
					? new Participant(tournament.getFleetIds().get(0), "", false, true)
					: source("K-" + index, false);
			Participant enemy = new Participant(tournament.getFleetIds().get(index + 1), "", false, true);
			addMatchNode("K-" + (index + 1), BattleNext_Match.MatchStage.KING_OF_THE_HILL,
					player, enemy, 40f + index * 300f, 240f, NODE_WIDTH, NODE_HEIGHT, false);
		}
	}
}
