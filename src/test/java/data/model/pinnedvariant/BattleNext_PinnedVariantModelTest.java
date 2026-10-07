package data.model.pinnedvariant;

import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleNext_PinnedVariantModelTest {

	@Test
	void roundTripCopiesNestedSnapshotsAndRetainsTheBuilderIdentity() throws Exception {

		JSONObject variant = new JSONObject().put("variantId", "original").put("hullId", "wolf")
				.put("modules", new JSONObject().put("slot", new JSONObject().put("variantId", "child").put("hullId", "module")));
		var pin = new BattleNext_PinnedVariantModel("pin", " Name ", variant);
		variant.getJSONObject("modules").getJSONObject("slot").put("hullId", "changed");
		pin.getVariantJson().put("hullId", "changed");
		pin.toJson().getJSONObject("variantJson").put("hullId", "changed");
		var loaded = new BattleNext_PinnedVariantModel();
		loaded.fromJson(pin.toJson());
		assertEquals(pin.toJson().toString(), loaded.toJson().toString());
		assertEquals("pin", loaded.getPinId());
		assertEquals("Name", loaded.getDisplayName());
		assertEquals("wolf", pin.getVariantJson().getString("hullId"));
		assertEquals("module", loaded.getVariantJson().getJSONObject("modules").getJSONObject("slot").getString("hullId"));
		assertEquals("wolf", loaded.getVariantJson().getString("hullId"));
		assertEquals("original", loaded.getVariantJson().getString("variantId"));
		assertEquals("child", loaded.getVariantJson().getJSONObject("modules").getJSONObject("slot").getString("variantId"));
		assertEquals("pin", pin.toBuilder().getId());
		assertEquals("original", pin.getVariantJson().getString("variantId"));
		assertEquals("Name", pin.getDisplayName());
	}

	@ParameterizedTest
	@ValueSource(strings = {"id", "name", "snapshot", "missing"})
	void failedInitializationAllowsRetryAndSuccessfulInitializationIsImmutable(String failure) throws Exception {

		JSONObject valid = new BattleNext_PinnedVariantModel("pin", "Name", new JSONObject().put("hullId", "wolf")).toJson();
		JSONObject invalid = new JSONObject(valid.toString());
		switch (failure) {
			case "id" -> invalid.put("pinId", "../pin");
			case "name" -> invalid.put("displayName", " ");
			case "snapshot" -> invalid.put("variantJson", "invalid");
			case "missing" -> invalid.remove("pinId");
		}
		var pin = new BattleNext_PinnedVariantModel();
		Class<? extends Exception> exceptionType = failure.equals("snapshot") || failure.equals("missing")
				? JSONException.class : IllegalArgumentException.class;
		var exception = assertThrows(exceptionType, () -> pin.fromJson(invalid));
		String identity = switch (failure) {
			case "id" -> "Invalid pin ID: ../pin";
			case "name" -> "Pinned variant display name is required";
			case "snapshot" -> "variantJson";
			default -> "pinId";
		};
		assertTrue(exception.getMessage().contains(identity));
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
