package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_StartedTournament;

import java.util.List;
import java.util.Map;

public class BattleNext_SingleEliminationView extends BattleNext_TournamentView {

	public BattleNext_SingleEliminationView(BattleNext_TournamentModel tournament, BattleNext_StartedTournament started,
			List<String> fleetIds, Map<String, String> fleetNames) {
		super(tournament, started, fleetIds, fleetNames);
	}

	@Override
	protected void buildLayout() {
		int width = 2;

		while (width < fleetIds.size()) {
			width *= 2;
		}
		buildWinners(width, BattleNext_Match.MatchStage.MAIN);
	}
}
