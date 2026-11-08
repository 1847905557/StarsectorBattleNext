package data.model.report.io;

import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.report.BattleNext_BattleReportModel;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import data.scripts.tools.BattleNext_ModFileManager;

public class BattleNext_BattleReportStandardLoader extends BattleNext_CatalogLoader<BattleNext_BattleReportModel> {

	private static final String RESOURCE_FOLDER = "tournament/_reports";

	@Override
	public List<BattleNext_BattleReportModel> load() throws IOException, JSONException {
		beginLoad();
		List<BattleNext_BattleReportModel> result = new ArrayList<>();
		for (String path : catalogPaths()) {
			String raw = "";
			String id = "";
			try {
				raw = new String(BattleNext_ModFileManager.readBytes(path), StandardCharsets.UTF_8);
				BattleNext_ReportDirectoryReader.Header header = BattleNext_ReportDirectoryReader.read(raw);
				id = header.metadata().optString("id", "");
				if (id.isEmpty()) throw new IllegalArgumentException("Entry ID is required");
				BattleNext_BattleReportModel model = new BattleNext_BattleReportModel();
				String text = raw;
				model.fromDirectory(header.metadata(), header.members(), () -> readDetails(path, text));
				markSource(model, path);
				recordInput(path, -1, id, raw);
				result.add(model);
			} catch (IOException | JSONException | IllegalArgumentException exception) {
				reject(path, -1, id, raw, exception);
			}
		}
		result = finishLoad(result);
		result.sort(Comparator.comparingLong(BattleNext_BattleReportModel::getCreatedAt).reversed());
		return result;
	}

	private JSONObject readDetails(String path, String text) {
		try {
			return new JSONObject(text);
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to read report: " + path, exception);
		}
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
