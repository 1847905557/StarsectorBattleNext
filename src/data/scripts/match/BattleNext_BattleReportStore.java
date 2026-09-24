package data.scripts.match;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BattleNext_BattleReportStore {

	private static final int MAX_REPORTS = 50;
	private static final String PATH = "AI_Battles_Next/reports/reports.json";
	private final Deque<BattleNext_BattleReport> reports = new ArrayDeque<>();

	public void reload() throws IOException, JSONException {
		reports.clear();
		JSONArray values = new JSONArray(Global.getSettings().readTextFileFromCommon(PATH));
		for (int index = 0; index < values.length(); index++) {
			if (values.optJSONObject(index) != null) add(BattleNext_BattleReport.fromJson(values.optJSONObject(index)));
		}
	}

	public void add(BattleNext_BattleReport report) {
		if (report == null) return;
		reports.addLast(report);
		while (reports.size() > MAX_REPORTS) {
			reports.removeFirst();
		}
	}

	public List<BattleNext_BattleReport> getRecent() {
		return List.copyOf(new ArrayList<>(reports));
	}

	public void save() throws IOException, JSONException {
		JSONArray values = new JSONArray();
		for (BattleNext_BattleReport report : reports) values.put(report.toJson());
		Global.getSettings().writeTextFileToCommon(PATH, values.toString());
	}

	public int size() {
		return reports.size();
	}
}
