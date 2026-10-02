package data.model.tournament.io;

import data.model.tournament.io.BattleNext_TournamentStandardLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_TournamentStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogLoadsWithoutEntries() throws Exception {
		BattleNext_TournamentStandardLoader loader = new BattleNext_TournamentStandardLoader();
		assertTrue(loader.load().isEmpty());
	}
}
