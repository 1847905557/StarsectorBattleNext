package data.scripts.tools;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BattleNext_BattleSignalsTest {

	@AfterEach
	void clearAuthorization() {
		BattleNext_BattleSignals.clearAuthorization();
		BattleNext_BattleSignals.consumePendingResult();
	}

	@Test
	void tournamentAuthorizationBelongsToOneExactMatchAndIsConsumedOnce() {
		BattleNext_BattleSignals.authorizeMatch("tournament-a", "W1-1");

		assertFalse(BattleNext_BattleSignals.consumeAuthorization("tournament-a", "W1-2"));
		assertFalse(BattleNext_BattleSignals.consumeAuthorization("tournament-b", "W1-1"));
		assertTrue(BattleNext_BattleSignals.consumeAuthorization("tournament-a", "W1-1"));
		assertFalse(BattleNext_BattleSignals.consumeAuthorization("tournament-a", "W1-1"));
	}

	@Test
	void completedBattleSignalsOnePendingResultAndRevokesTheMatchAuthorization() {
		BattleNext_BattleSignals.authorizeMatch("tournament-a", "W1-1");
		BattleNext_BattleSignals.markPendingResult();

		assertTrue(BattleNext_BattleSignals.consumePendingResult());
		assertFalse(BattleNext_BattleSignals.consumePendingResult());
		assertFalse(BattleNext_BattleSignals.consumeAuthorization("tournament-a", "W1-1"));
	}
}
