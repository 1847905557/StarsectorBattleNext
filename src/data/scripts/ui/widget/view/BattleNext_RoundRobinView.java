package data.scripts.ui.widget.view;

import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_StartedTournament;

import java.util.List;
import java.util.Map;

public class BattleNext_RoundRobinView extends BattleNext_TournamentView {

	public BattleNext_RoundRobinView(BattleNext_TournamentModel tournament, BattleNext_StartedTournament started,
			List<String> fleetIds, Map<String, String> fleetNames) {
		super(tournament, started, fleetIds, fleetNames);
	}

	@Override
	protected void buildLayout() {

		int count = fleetIds.size();
		float cellWidth = NODE_WIDTH;
		float cellHeight = NODE_HEIGHT;

		for (int row = 0; row < count; row++) {
			Participant player = seededFleet(row + 1);
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
				Participant enemy = seededFleet(column + 1);
				String id = "RR-" + Math.min(row, column) + "-" + Math.max(row, column);
				addMatchNode(id, BattleNext_Match.MatchStage.ROUND_ROBIN, player, enemy,
						x, y, cellWidth, cellHeight, true);
			}
		}
	}
}
