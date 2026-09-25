package data.scripts.report;

import data.scripts.report.io.BattleNext_BattleReportCommonLoader;
import data.scripts.report.io.BattleNext_BattleReportSaver;
import data.scripts.tools.BattleNext_LoadSource;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BattleNext_BattleReportStore {

	private static final int MAX_REPORTS = 50;
	private final BattleNext_BattleReportCommonLoader commonLoader = new BattleNext_BattleReportCommonLoader();
	private final BattleNext_BattleReportSaver saver = new BattleNext_BattleReportSaver();
	private final Deque<BattleNext_BattleReport> reports = new ArrayDeque<>();

	public void reload() throws IOException, JSONException {
		reports.clear();
		for (BattleNext_BattleReport report : commonLoader.load()) {
			add(report);
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
		for (BattleNext_BattleReport report : reports) {
			report.setSourceType(BattleNext_LoadSource.COMMON);
			report.setSourcePath("AI_Battles_Next/reports/reports.json");
		}
		saver.save(List.copyOf(reports));
	}

	public int size() {
		return reports.size();
	}
}
