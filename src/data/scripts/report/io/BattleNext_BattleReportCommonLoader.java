package data.scripts.report.io;

import com.fs.starfarer.api.Global;
import data.scripts.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_JsonCatalog;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_BattleReportCommonLoader {

	public List<BattleNext_BattleReportModel> load() throws IOException, JSONException {
		List<BattleNext_BattleReportModel> result = new ArrayList<>();
		for (String path : BattleNext_JsonCatalog.commonFiles("AI_Battles_Next/reports")) {
			BattleNext_BattleReportModel value = BattleNext_BattleReportModel.fromJson(Global.getSettings().readJSONFromCommon(path, false));
			if (value.getId().isEmpty()) continue;
			value.setSourceType(BattleNext_LoadSource.COMMON);
			value.setSourcePath(path);
			result.add(value);
		}
		result.sort(Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
		return result;
	}
}
