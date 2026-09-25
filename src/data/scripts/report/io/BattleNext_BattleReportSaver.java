package data.scripts.report.io;

import com.fs.starfarer.api.Global;
import data.scripts.report.BattleNext_BattleReportModel;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_BattleReportSaver {

	public void save(BattleNext_BattleReportModel report) throws IOException, JSONException {
		Global.getSettings().writeJSONToCommon("AI_Battles_Next/reports/" + report.getId() + ".json", report.toJson(), false);
	}

	public void delete(String id) throws IOException {
		Global.getSettings().deleteTextFileFromCommon("AI_Battles_Next/reports/" + id + ".json");
		if (Global.getSettings().fileExistsInCommon("AI_Battles_Next/reports/" + id + ".json")) {
			throw new IOException("Report file remains after deletion: " + id);
		}
	}
}
