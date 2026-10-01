package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;

import java.util.Map;

public class BattleNext_RoundRobinView extends BattleNext_TournamentView {

	public BattleNext_RoundRobinView(BattleNext_TournamentModel tournament,
			Map<String, String> fleetNames) {
		super(tournament, fleetNames);
	}

	@Override
	protected void buildLayout() {

		int count = tournament.getFleetIds().size();
		float cellWidth = NODE_WIDTH;
		float cellHeight = NODE_HEIGHT;

		for (int row = 0; row < count; row++) {
			Participant player = new Participant(tournament.getFleetIds().get(row), "", false, true);
			addDecoration(shorten(participantText(player), 22), 20f, 60f + (row + 1) * cellHeight,
					cellWidth, cellHeight);
			addDecoration(shorten(participantText(player), 22), 20f + (row + 1) * cellWidth, 60f,
					cellWidth, cellHeight);
			for (int column = 0; column < count; column++) {
				float x = 20f + (column + 1) * cellWidth;
				float y = 60f + (row + 1) * cellHeight;

				if (row == column) {
					addDecoration(shorten(participantText(player), 22), x, y, cellWidth, cellHeight);
					continue;
				}
				Participant enemy = new Participant(tournament.getFleetIds().get(column), "", false, true);
				String id = "RR-" + Math.min(row, column) + "-" + Math.max(row, column);
				addMatchNode(id, BattleNext_Match.MatchStage.ROUND_ROBIN, player, enemy,
						x, y, cellWidth, cellHeight, true);
			}
		}
	}
}
