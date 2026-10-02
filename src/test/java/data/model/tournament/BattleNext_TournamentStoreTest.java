package data.model.tournament;

import data.model.tournament.BattleNext_TournamentStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_TournamentStoreTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogCanLoadAndReportItsLoadedState() throws Exception {
		BattleNext_TournamentStore store = new BattleNext_TournamentStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}
}
