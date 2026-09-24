package data.scripts.report.io;

import com.fs.starfarer.api.Global;
import data.scripts.report.BattleNext_BattleReport;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNext_BattleReportSaver {

	private static final String PATH = "AI_Battles_Next/reports/reports.json";

	public void save(List<BattleNext_BattleReport> reports) throws IOException, JSONException {
		JSONArray values = new JSONArray();
		for (BattleNext_BattleReport report : reports) {
			values.put(report.toJson());
		}
		Global.getSettings().writeTextFileToCommon(PATH, values.toString());
	}
}
