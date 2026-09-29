package data.scripts.ui.widget.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleNext_VariantSourceProviderTest {

	@Test
	void variantsAreSingleSelection() {
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();

		assertEquals(1, provider.getMaxCount("any_variant"));
	}
}
