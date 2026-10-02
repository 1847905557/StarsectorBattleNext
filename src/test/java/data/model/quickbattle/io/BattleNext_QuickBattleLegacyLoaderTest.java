package data.model.quickbattle.io;

import data.model.quickbattle.io.BattleNext_QuickBattleLegacyLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BattleNext_QuickBattleLegacyLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void loaderReturnsAConcreteResult() throws Exception {
		BattleNext_QuickBattleLegacyLoader loader = new BattleNext_QuickBattleLegacyLoader();
		assertNotNull(loader.load());
	}
}
