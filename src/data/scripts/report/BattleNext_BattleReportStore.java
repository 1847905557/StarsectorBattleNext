package data.scripts.report;

import data.scripts.report.io.BattleNext_BattleReportCommonLoader;
import data.scripts.report.io.BattleNext_BattleReportSaver;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_BattleReportStore {

	private final BattleNext_BattleReportCommonLoader commonLoader = new BattleNext_BattleReportCommonLoader();
	private final BattleNext_BattleReportSaver saver = new BattleNext_BattleReportSaver();
	private final Map<String, BattleNext_BattleReportModel> reports = new LinkedHashMap<>();
	private String loadError = "Report catalog has not been loaded";

	public void reload() throws IOException, JSONException {
		try {
			List<BattleNext_BattleReportModel> loaded = commonLoader.load();
			reports.clear();
			for (BattleNext_BattleReportModel report : loaded) {
				reports.put(report.getId(), report);
			}
			loadError = "";
		} catch (IOException | JSONException | RuntimeException exception) {
			loadError = exception.getMessage();
			throw exception;
		}
	}

	public String getLoadError() {
		return loadError;
	}
	public boolean isLoaded() {
		return loadError.isEmpty();
	}

	// Recording must persist even when unrelated report files failed to load, so no isLoaded guard here.
	public void save(BattleNext_BattleReportModel report) throws IOException, JSONException {
		if (report == null || report.getId().isEmpty()) {
			throw new IllegalArgumentException("Report id is required");
		}
		saver.save(report);
		report.setSourceType(BattleNext_LoadSource.COMMON);
		report.setSourcePath("AI_Battles_Next/reports/" + report.getId() + ".json");
		reports.put(report.getId(), report);
	}

	public List<BattleNext_BattleReportModel> getAll() {
		return List.copyOf(reports.values());
	}

	public void delete(String id) throws IOException {
		if (!reports.containsKey(id)) return;
		saver.delete(id);
		reports.remove(id);
	}
}
