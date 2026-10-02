package data.model.pinnedvariant.io;

import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_PinnedVariantStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void emptyCatalogLoadsWithoutEntries() throws Exception {
		BattleNext_PinnedVariantStandardLoader loader = new BattleNext_PinnedVariantStandardLoader();
		assertTrue(loader.load().isEmpty());
	}
}
