package data.model.report;

import data.model.report.BattleNext_BattleReportStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_BattleReportStoreTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogCanLoadAndReportItsLoadedState() throws Exception {
		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}
}
