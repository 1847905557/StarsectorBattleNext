package data.scripts.battle;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_BattleSignals;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.tools.BattleNext_Settings;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class BattleNext_BattleResultRecorder extends BaseEveryFrameCombatPlugin {

	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private final BattleNext_Match match;
	private final BattleNext_BattleSourceProvider provider;
	private boolean recorded;

	public BattleNext_BattleResultRecorder(BattleNext_ScorekeeperPlugin scorekeeper, BattleNext_Match match,
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
		} catch (IOException | org.json.JSONException | RuntimeException exception) {
			com.fs.starfarer.api.Global.getLogger(BattleNext_BattleResultRecorder.class).error("Unable to save battle report", exception);
		}
	}

	private void record(BattleNext_BattleReportModel report) throws IOException, org.json.JSONException {
		if (report.getId().isEmpty()) report.setId(UUID.randomUUID().toString());
		if (report.getCreatedAt() <= 0L) report.setCreatedAt(System.currentTimeMillis());
		BattleNext_TournamentModel tournament = provider == null ? null : provider.getActiveTournament();
		if (tournament == null) {
			BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
			store.reload();
			store.save(report);
			int keepCount = BattleNext_Settings.getReportKeepCount();
			List<BattleNext_BattleReportModel> reports = new java.util.ArrayList<>(store.getAll());
			reports.sort(java.util.Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
			for (int index = keepCount; index < reports.size(); index++) {
				store.delete(reports.get(index).getId());
			}
		} else {
			BattleNext_TournamentEngine engine = provider.getTournamentEngine();
			engine.recordRound(tournament, match.getId(), resolveWinner(match, report.getWinner()), report);
			BattleNext_TournamentStore store = provider.getTournamentStore();
			store.save(tournament);
		}
		BattleNext_BattleSignals.markPendingResult();
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
