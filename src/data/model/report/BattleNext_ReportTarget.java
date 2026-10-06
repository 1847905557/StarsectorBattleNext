package data.model.report;

import org.json.JSONException;

import java.io.IOException;

public record BattleNext_ReportTarget(BattleNext_BattleReportModel report) {

	public String identity() {
		return "report:" + report.getId();
	}

	public void rename(BattleNext_BattleReportStore reports, String name) throws IOException, JSONException {
		String previous = report.getName();
		report.setName(name);
		try {
			reports.save(report);
		} catch (IOException | JSONException | RuntimeException exception) {
			report.setName(previous);
			throw exception;
		}
	}

	public void delete(BattleNext_BattleReportStore reports) throws IOException, JSONException {
		reports.delete(report.getId());
	}
}
