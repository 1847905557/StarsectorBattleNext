package data.simulator;

import data.model.report.BattleNext_BattleReportModel;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public record BattleNext_SimulationResult(String id, Outcome outcome, String reportJson,
		float simulationSeconds, long executionNanos, long steps, long peakHeapBytes, String diagnostic) {

	public enum Outcome { COMPLETED, CANCELLED, BUDGET_EXCEEDED, FAILED }

	public BattleNext_SimulationResult {
		UUID.fromString(id);
		if (outcome == null || diagnostic == null || reportJson == null || !Float.isFinite(simulationSeconds)
				|| simulationSeconds < 0f || executionNanos < 0L || steps < 0L || peakHeapBytes < 0L) throw new IllegalArgumentException("Invalid simulator result metrics");
		if (outcome == Outcome.COMPLETED && reportJson.isEmpty()) throw new IllegalArgumentException("Completed simulation requires a report");
	}

	public BattleNext_BattleReportModel report() throws JSONException {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.fromJson(new JSONObject(reportJson));
		return report;
	}

	public JSONObject toJson() throws JSONException {
		return new JSONObject().put("id", id).put("outcome", outcome.name()).put("report", reportJson)
				.put("simulationSeconds", simulationSeconds).put("executionNanos", executionNanos).put("steps", steps)
				.put("peakHeapBytes", peakHeapBytes).put("diagnostic", diagnostic);
	}

	public static BattleNext_SimulationResult fromJson(JSONObject json) throws JSONException {
		return new BattleNext_SimulationResult(json.getString("id"), Outcome.valueOf(json.getString("outcome")),
				json.getString("report"), (float) json.getDouble("simulationSeconds"), json.getLong("executionNanos"),
				json.getLong("steps"), json.getLong("peakHeapBytes"), json.getString("diagnostic"));
	}
}
