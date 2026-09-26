package data.model.report.io;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

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
		return BattleNext_ModFileManager.listJson(RESOURCE_FOLDER);
	}

	@Override
	protected BattleNext_BattleReportModel parse(String path) throws IOException, JSONException {
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.fromJson(Global.getSettings().loadJSON(path, MOD_ID));
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
