package data.model.report.io;

import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.report.BattleNext_BattleReportModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class BattleNext_BattleReportStandardLoader extends BattleNext_CatalogLoader<BattleNext_BattleReportModel> {

	private static final String RESOURCE_FOLDER = "tournament/_reports";

	@Override
	public List<BattleNext_BattleReportModel> load() throws IOException, JSONException {
		List<BattleNext_BattleReportModel> result = super.load();
		result.sort(Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
		return result;
	}

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return paths(RESOURCE_FOLDER, false).stream().filter(path -> path.endsWith(".json")).toList();
	}

	@Override
	protected BattleNext_BattleReportModel parse(JSONObject json) throws IOException, JSONException {
		return parseReport(json);
	}

	static BattleNext_BattleReportModel parseReport(JSONObject json) throws JSONException {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.fromJson(json);
		return report;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}

	@Override
	protected String kind() {
		return "report";
	}
}
