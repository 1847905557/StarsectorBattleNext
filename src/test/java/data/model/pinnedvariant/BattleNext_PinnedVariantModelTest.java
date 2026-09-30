package data.model.pinnedvariant;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleNext_PinnedVariantModelTest {

	@Test
	void roundTripPreservesCoreFieldsAndCopiesVariantJson() throws Exception {
		JSONObject variant = new JSONObject().put("hullId", "wolf").put("variantId", "wolf1");
		BattleNext_PinnedVariantModel model = new BattleNext_PinnedVariantModel("pin_alpha", "Alpha", variant);
		variant.put("hullId", "falcon");

		BattleNext_PinnedVariantModel loaded = new BattleNext_PinnedVariantModel();
		loaded.fromJson(model.toJson());

		assertEquals("pin_alpha", loaded.getPinId());
		assertEquals("Alpha", loaded.getDisplayName());
		assertEquals("wolf", loaded.getVariantJson().getString("hullId"));
	}

	@Test
	void rejectsReservedPinnedVariantsDirectoryName() {
		assertThrows(IllegalArgumentException.class,
				() -> new BattleNext_PinnedVariantModel("_pinnedVariants", "Pinned", new JSONObject()));
	}
}
