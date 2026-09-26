package data.model.report.io;

import data.model.BattleNext_BaseSaver;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_BattleReportSaver extends BattleNext_BaseSaver<BattleNext_BattleReportModel> {

	private static final String RESOURCE_ROOT = "tournament/_reports/";

	@Override
	public String save(BattleNext_BattleReportModel report) throws IOException, JSONException {
		if (!BattleNext_EntryId.isValidPathSegment(report.getId())) throw new IOException("Invalid report ID: " + report.getId());
		String path = RESOURCE_ROOT + report.getId() + ".json";
		writeResourceJson(path, report.toJson());
		return path;
	}

	@Override
	public void delete(BattleNext_BattleReportModel report) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(report.getId())) throw new IOException("Invalid report ID: " + report.getId());
		String path = RESOURCE_ROOT + report.getId() + ".json";
		BattleNext_ModFileManager.delete(path);
		if (BattleNext_ModFileManager.exists(path)) throw new IOException("Report file remains after deletion: " + report.getId());
	}
}
