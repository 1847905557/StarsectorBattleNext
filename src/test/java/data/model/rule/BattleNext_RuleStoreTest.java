package data.model.rule;

import data.model.rule.BattleNext_RuleStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_RuleStoreTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogCanLoadAndReportItsLoadedState() throws Exception {
		BattleNext_RuleStore store = new BattleNext_RuleStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}
}
