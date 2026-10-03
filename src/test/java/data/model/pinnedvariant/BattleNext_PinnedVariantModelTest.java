package data.model.pinnedvariant;

import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
	void snapshotCopiesNestedInputOutputAndBuilderIdentity() throws Exception {
		JSONObject variant = new JSONObject().put("variantId", "original").put("hullId", "wolf")
				.put("modules", new JSONObject().put("slot", new JSONObject().put("variantId", "child").put("hullId", "module")));
		var pin = new BattleNext_PinnedVariantModel("pin", " Name ", variant);
		variant.getJSONObject("modules").getJSONObject("slot").put("hullId", "changed");
		pin.getVariantJson().put("hullId", "changed");
		pin.toJson().getJSONObject("variantJson").put("hullId", "changed");
		assertEquals("wolf", pin.getVariantJson().getString("hullId"));
		assertEquals("module", pin.getVariantJson().getJSONObject("modules").getJSONObject("slot").getString("hullId"));
		assertEquals("pin", pin.toBuilder().getId());
		assertEquals("original", pin.getVariantJson().getString("variantId"));
		assertEquals("Name", pin.getDisplayName());
	}

	@ParameterizedTest
	@ValueSource(strings = {"version", "id", "name", "snapshot", "missing"})
	void failedInitializationAllowsRetryAndSuccessfulInitializationIsImmutable(String failure) throws Exception {
		JSONObject valid = new BattleNext_PinnedVariantModel("pin", "Name", new JSONObject().put("hullId", "wolf")).toJson();
		JSONObject invalid = new JSONObject(valid.toString());
		switch (failure) {
			case "version" -> invalid.put("formatVersion", 2);
			case "id" -> invalid.put("pinId", "../pin");
			case "name" -> invalid.put("displayName", " ");
			case "snapshot" -> invalid.put("variantJson", "invalid");
			case "missing" -> invalid.remove("pinId");
		}
		var pin = new BattleNext_PinnedVariantModel();
		Class<? extends Exception> exceptionType = failure.equals("snapshot") || failure.equals("missing")
				? JSONException.class : IllegalArgumentException.class;
		assertThrows(exceptionType, () -> pin.fromJson(invalid));
		assertEquals("", pin.getPinId());
		assertEquals("", pin.getDisplayName());
		pin.fromJson(valid);
		assertEquals("pin", pin.getPinId());
		assertThrows(IllegalStateException.class, () -> pin.fromJson(valid));
	}

	@Test
	void rejectsReservedPinnedVariantsDirectoryName() {
		assertThrows(IllegalArgumentException.class,
				() -> new BattleNext_PinnedVariantModel("_pinnedVariants", "Pinned", new JSONObject()));
	}
}
