package data.model.quickbattle.io;

import data.model.quickbattle.io.BattleNext_QuickBattleStandardLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BattleNext_QuickBattleStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void loaderReturnsAConcreteResult() throws Exception {
		BattleNext_QuickBattleStandardLoader loader = new BattleNext_QuickBattleStandardLoader();
		assertNotNull(loader.load());
	}
}
