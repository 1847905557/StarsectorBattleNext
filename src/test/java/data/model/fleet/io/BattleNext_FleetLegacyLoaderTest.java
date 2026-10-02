package data.model.fleet.io;

import data.model.fleet.io.BattleNext_FleetLegacyLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BattleNext_FleetLegacyLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void loaderReturnsAConcreteResult() throws Exception {
		BattleNext_FleetLegacyLoader loader = new BattleNext_FleetLegacyLoader();
		assertNotNull(loader.load());
	}
}
