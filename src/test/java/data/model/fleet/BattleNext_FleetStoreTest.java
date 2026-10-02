package data.model.fleet;

import data.model.fleet.BattleNext_FleetStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_FleetStoreTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogCanLoadAndReportItsLoadedState() throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}
}
