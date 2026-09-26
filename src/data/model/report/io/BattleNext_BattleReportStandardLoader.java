package data.model.report.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import org.json.JSONException;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class BattleNext_BattleReportCommonLoader extends BattleNext_CatalogLoader<BattleNext_BattleReportModel> {

	private static final String COMMON_ROOT = "AI_Battles_Next/reports";

	@Override
	public List<BattleNext_BattleReportModel> load() throws IOException, JSONException {
		List<BattleNext_BattleReportModel> result = super.load();
		result.sort(Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
		return result;
	}

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_JsonCatalog.commonFiles(COMMON_ROOT);
	}

	@Override
	protected BattleNext_BattleReportModel parse(String path) throws IOException, JSONException {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.fromJson(Global.getSettings().readJSONFromCommon(path, false));
		return report;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}

	@Override
	protected String kind() {
		return "common report";
	}
}
