package data.scripts.match;

import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.combat.BattleNext_Scorekeeper;
import data.scripts.tournament.BattleNext_Tournament;
import data.scripts.tournament.BattleNext_TournamentStore;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class BattleNext_MatchRecorder extends com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin {

	private final BattleNext_Scorekeeper scorekeeper;
	private final BattleNext_Match match;
	private final BattleNext_MatchListProvider provider;
	private boolean recorded;

	public BattleNext_MatchRecorder(BattleNext_Scorekeeper scorekeeper, BattleNext_Match match,
			BattleNext_MatchListProvider provider) {
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
			com.fs.starfarer.api.Global.getLogger(BattleNext_MatchRecorder.class).error("Unable to save battle report", exception);
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
			appendRound(match, report);
			BattleNext_TournamentStore store = provider.getTournamentStore();
			store.save(tournament);
		}
		System.setProperty("AI_Battles_Next.pendingResult", "1");
	}

	private void appendRound(BattleNext_Match target, BattleNext_BattleReport report) {
		BattleNext_Round previous = target.getRounds().isEmpty()
				? null : target.getRounds().get(target.getRounds().size() - 1);
		int number = previous == null ? 1 : previous.getNumber();
		int attempt = previous == null ? 0 : previous.getAttempt() + 1;
		if (previous != null && !"draw".equals(previous.getWinner())) {
			number++;
			attempt = 0;
		}
		BattleNext_Round round = new BattleNext_Round(number, target.getPlayer(), target.getEnemy());
		round.setWinner(report.getWinner());
		round.setAttempt(attempt);
		round.setReport(report);
		target.addRound(round);
	}
}
