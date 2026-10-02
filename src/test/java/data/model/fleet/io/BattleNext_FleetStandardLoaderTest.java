package data.model.fleet.io;

import data.model.fleet.io.BattleNext_FleetStandardLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BattleNext_FleetStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void loaderReturnsAConcreteResult() throws Exception {
		BattleNext_FleetStandardLoader loader = new BattleNext_FleetStandardLoader();
		assertNotNull(loader.load());
	}
}
