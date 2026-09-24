package data.scripts.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class BattleNext_BattleReportStore {

	private static final int MAX_REPORTS = 50;
	private final Deque<BattleNext_BattleReport> reports = new ArrayDeque<>();

	public void add(BattleNext_BattleReport report) {
		if (report == null) return;
		reports.addLast(report);
		while (reports.size() > MAX_REPORTS) reports.removeFirst();
	}

	public List<BattleNext_BattleReport> getRecent() { return List.copyOf(new ArrayList<>(reports)); }
	public int size() { return reports.size(); }
}
