package data.scripts.report;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.combat.BattleNext_Scorekeeper;
import data.scripts.match.BattleNext_BattleSourceProvider;
import data.scripts.match.BattleNext_Match;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tournament.BattleNext_TournamentStore;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class BattleNext_BattleResultRecorder extends BaseEveryFrameCombatPlugin {

	private final BattleNext_Scorekeeper scorekeeper;
	private final BattleNext_Match match;
	private final BattleNext_BattleSourceProvider provider;
	private boolean recorded;

	public BattleNext_BattleResultRecorder(BattleNext_Scorekeeper scorekeeper, BattleNext_Match match,
			BattleNext_BattleSourceProvider provider) {
		this.scorekeeper = scorekeeper;
		this.match = match;
		this.provider = provider;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (recorded || !scorekeeper.isFrozen()) return;
		recorded = true;
		try {
			record(scorekeeper.getFrozenReport());
		} catch (IOException | org.json.JSONException exception) {
			com.fs.starfarer.api.Global.getLogger(BattleNext_BattleResultRecorder.class).error("Unable to save battle report", exception);
		}
	}

	private void record(BattleNext_BattleReport report) throws IOException, org.json.JSONException {
		if (report.getId().isEmpty()) report.setId(UUID.randomUUID().toString());
		BattleNext_Tournament tournament = provider == null ? null : provider.getActiveTournament();
		if (tournament == null) {
			BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
			try {
				store.reload();
			} catch (IOException | org.json.JSONException ignored) {
			}
			store.add(report);
			store.save();
		} else {
			BattleNext_TournamentEngine engine = provider.getTournamentEngine();
			engine.recordRound(tournament, match.getId(), resolveWinner(match, report.getWinner()), report);
			BattleNext_TournamentStore store = provider.getTournamentStore();
			store.save(tournament);
		}
		System.setProperty("AI_Battles_Next.pendingResult", "1");
	}

	private String resolveWinner(BattleNext_Match match, String winner) {
		if ("player".equals(winner)) {
			return match.getPlayer();
		}
		if ("enemy".equals(winner)) {
			return match.getEnemy();
		}
		return winner;
	}
}
