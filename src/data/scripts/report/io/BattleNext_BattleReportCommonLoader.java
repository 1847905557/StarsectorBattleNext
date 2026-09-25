package data.scripts.report.io;

import com.fs.starfarer.api.Global;
import data.scripts.report.BattleNext_BattleReport;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_BattleReportCommonLoader {

	private static final String PATH = "AI_Battles_Next/reports/reports.json";

	public List<BattleNext_BattleReport> load() throws IOException, JSONException {
		List<BattleNext_BattleReport> result = new ArrayList<>();
		JSONArray values = new JSONArray(Global.getSettings().readTextFileFromCommon(PATH));
		for (int index = 0; index < values.length(); index++) {
			if (values.optJSONObject(index) == null) continue;
			result.add(BattleNext_BattleReport.fromJson(values.optJSONObject(index)));
		}
		return result;
	}
}
