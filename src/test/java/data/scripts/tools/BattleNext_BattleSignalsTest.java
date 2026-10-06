package data.scripts.tools;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BattleNext_BattleSignalsTest {

	@AfterEach
	void consumeNotification() {
		BattleNext_BattleSignals.consumePendingResult();
	}

	@Test
	void completedBattlePublishesOneConsumablePendingResult() {
		BattleNext_BattleSignals.markPendingResult();
		assertTrue(BattleNext_BattleSignals.consumePendingResult());
		assertFalse(BattleNext_BattleSignals.consumePendingResult());
	}
}
