package data.model.quickbattle;

import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_QuickBattleStoreTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogCanLoadAndReportItsLoadedState() throws Exception {
		BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
		store.reload();
		assertTrue(store.isLoaded());
	}
}
