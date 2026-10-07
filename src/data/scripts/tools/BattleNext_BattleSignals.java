package data.scripts.tools;

public class BattleNext_BattleSignals {

	private static final BattleNext_BattleSignals INSTANCE = new BattleNext_BattleSignals();

	private String reportId = "";

	private BattleNext_BattleSignals() {

	}

	public static synchronized void markPendingResult(String reportId) {
		INSTANCE.reportId = reportId;
	}

	public static synchronized String consumePendingResult() {
		String value = INSTANCE.reportId;
		INSTANCE.reportId = "";
		return value;
	}

	public static synchronized void clearPendingResult() {
		INSTANCE.reportId = "";
	}
}
