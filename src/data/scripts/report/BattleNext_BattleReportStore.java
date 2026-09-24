package data.scripts.report;

import data.scripts.report.io.BattleNext_BattleReportLoader;
import data.scripts.report.io.BattleNext_BattleReportSaver;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BattleNext_BattleReportStore {

	private static final int MAX_REPORTS = 50;
	private final BattleNext_BattleReportLoader loader = new BattleNext_BattleReportLoader();
	private final BattleNext_BattleReportSaver saver = new BattleNext_BattleReportSaver();
	private final Deque<BattleNext_BattleReport> reports = new ArrayDeque<>();

	public void reload() throws IOException, JSONException {
		reports.clear();
		for (BattleNext_BattleReport report : loader.load()) {
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
		saver.save(List.copyOf(reports));
	}

	public int size() {
		return reports.size();
	}
}
