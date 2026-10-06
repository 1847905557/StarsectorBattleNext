package data.model.report;

import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Round;
import data.simulator.BattleNext_TournamentSnapshot;

import java.io.IOException;
import org.json.JSONException;

public record BattleNext_ReportTarget(BattleNext_BattleReportModel report, String context,
		BattleNext_TournamentModel tournament, String matchId, int roundIndex, BattleNext_TournamentSnapshot snapshot) {

	public BattleNext_ReportTarget(BattleNext_BattleReportModel report, String context,
			BattleNext_TournamentModel tournament, String matchId, int roundIndex) {
		this(report, context, tournament, matchId, roundIndex, null);
	}

	public boolean isReadOnly() {
		return snapshot != null;
	}

	public String identity() {
		if (snapshot != null) return "simulation:" + snapshot.getId() + "/" + matchId + "/" + roundIndex;
		return tournament == null ? "report:" + report.getId()
				: "tournament:" + tournament.getId() + "/" + matchId + "/" + roundIndex;
	}

	public void rename(BattleNext_BattleReportStore reports, BattleNext_TournamentStore tournaments, String name)
			throws IOException, JSONException {
		if (isReadOnly()) throw new IllegalStateException("Tournament simulation reports are read-only");
		String previous = report.getName();
		report.setName(name);
		try {
			if (tournament == null) reports.save(report);
			else tournaments.save(tournament);
		} catch (IOException | JSONException | RuntimeException exception) {
			report.setName(previous);
			throw exception;
		}
	}

	public void delete(BattleNext_BattleReportStore reports, BattleNext_TournamentStore tournaments)
			throws IOException, JSONException {
		if (isReadOnly()) throw new IllegalStateException("Tournament simulation reports are read-only");
		if (tournament == null) {
			reports.delete(report.getId());
			return;
		}
		BattleNext_Round round = tournament.findMatch(matchId).getRounds().get(roundIndex);
		round.setReport(new BattleNext_BattleReportModel());
		try {
			tournaments.save(tournament);
		} catch (IOException | JSONException | RuntimeException exception) {
			round.setReport(report);
			throw exception;
		}
	}
}
