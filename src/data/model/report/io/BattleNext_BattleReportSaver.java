package data.scripts.report.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_BaseSaver;
import data.scripts.report.BattleNext_BattleReportModel;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_BattleReportSaver extends BattleNext_BaseSaver<BattleNext_BattleReportModel> {

	private static final String COMMON_ROOT = "AI_Battles_Next/reports/";

	@Override
	public String save(BattleNext_BattleReportModel report) throws IOException, JSONException {
		String path = COMMON_ROOT + report.getId() + ".json";
		writeCommonJson(path, report.toJson());
		return path;
	}

	@Override
	public void delete(BattleNext_BattleReportModel report) throws IOException {
		String path = COMMON_ROOT + report.getId() + ".json";
		Global.getSettings().deleteTextFileFromCommon(path);
		if (Global.getSettings().fileExistsInCommon(path)) {
			throw new IOException("Report file remains after deletion: " + report.getId());
		}
	}
}
