package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;

import java.util.Map;

public class BattleNext_SingleEliminationView extends BattleNext_TournamentView {

	public BattleNext_SingleEliminationView(BattleNext_TournamentModel tournament,
			Map<String, String> fleetNames) {
		super(tournament, fleetNames);
	}

	@Override
	protected void buildLayout() {
		int width = 2;

		while (width < tournament.getFleetIds().size()) {
			width *= 2;
		}
		buildWinners(width, BattleNext_Match.MatchStage.MAIN);
	}
}
