package data.scripts.tools;

public class BattleNext_BattleSignals {

	private static final BattleNext_BattleSignals INSTANCE = new BattleNext_BattleSignals();

	private volatile boolean pendingResult;

	private BattleNext_BattleSignals() {

	}

	public static void markPendingResult() {
		INSTANCE.pendingResult = true;
	}

	public static boolean consumePendingResult() {
		boolean value = INSTANCE.pendingResult;
		INSTANCE.pendingResult = false;
		return value;
	}
}
