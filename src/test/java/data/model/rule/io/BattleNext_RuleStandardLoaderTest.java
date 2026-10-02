package data.model.rule.io;

import data.model.rule.io.BattleNext_RuleStandardLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_RuleStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogLoadsWithoutEntries() throws Exception {
		BattleNext_RuleStandardLoader loader = new BattleNext_RuleStandardLoader();
		assertTrue(loader.load().isEmpty());
	}
}
