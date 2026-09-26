package data.scripts.tools;

public class BattleNext_BattleSignals {

	private static final BattleNext_BattleSignals INSTANCE = new BattleNext_BattleSignals();

	private volatile boolean pendingResult;
	private String authorizedTournamentId = "";
	private String authorizedMatchId = "";

	private BattleNext_BattleSignals() {

	}

	public static void markPendingResult() {
		INSTANCE.pendingResult = true;
		clearAuthorization();
	}

	public static synchronized void authorizeMatch(String tournamentId, String matchId) {
		INSTANCE.authorizedTournamentId = tournamentId;
		INSTANCE.authorizedMatchId = matchId;
	}

	public static synchronized boolean consumeAuthorization(String tournamentId, String matchId) {
		boolean authorized = INSTANCE.authorizedTournamentId.equals(tournamentId)
				&& INSTANCE.authorizedMatchId.equals(matchId);
		if (authorized) clearAuthorization();
		return authorized;
	}

	public static synchronized void clearAuthorization() {
		INSTANCE.authorizedTournamentId = "";
		INSTANCE.authorizedMatchId = "";
	}

	public static boolean consumePendingResult() {
		boolean value = INSTANCE.pendingResult;
		INSTANCE.pendingResult = false;
		return value;
	}
}
