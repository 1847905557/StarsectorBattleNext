package data.scripts.tools;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BattleNext_BattleSignalsTest {

	@AfterEach
	void consumeNotification() {
		BattleNext_BattleSignals.consumePendingResult();
	}

	@Test
	void completedBattlePublishesOneConsumablePendingResult() {
		BattleNext_BattleSignals.markPendingResult("first");
		BattleNext_BattleSignals.markPendingResult("latest");
		assertEquals("latest", BattleNext_BattleSignals.consumePendingResult());
		assertEquals("", BattleNext_BattleSignals.consumePendingResult());
		BattleNext_BattleSignals.markPendingResult("cleared");
		BattleNext_BattleSignals.clearPendingResult();
		assertEquals("", BattleNext_BattleSignals.consumePendingResult());
	}
}
