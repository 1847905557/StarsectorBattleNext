package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_VariantBuilderTest extends BattleNext_TestGameFixture {

	@Test
	void emptyVariantHasHullModuleSlotsButOnlyInstalledModulesAppearInTheVariant() throws Exception {
		var hull = hull("parent", "Parent", List.of("left", "right"));
		var variant = Global.getSettings().createEmptyVariant("parent", hull);
		assertTrue(hull.getWeaponSlot("left").isStationModule());
		assertEquals(List.of(), variant.getModuleSlots());
		assertTrue(variant.getStationModules().isEmpty());
		var child = Global.getSettings().createEmptyVariant("child", hull("child", "Child", List.of()));
		variant.setModuleVariant("left", child);
		assertEquals(List.of("left"), variant.getModuleSlots());
		assertEquals("child", variant.getStationModules().get("left"));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void jsonInstallsBothModulesIntoInitiallyEmptyVariants(boolean array) throws Exception {
		hull("parent", "Parent", List.of("WS 026", "WS 027"));
		hull("child", "Child", List.of());
		var left = new JSONObject().put("variantId", "left").put("hullId", "child").put("fluxVents", 7);
		var right = new JSONObject().put("variantId", "right").put("hullId", "child").put("fluxVents", 9);
		Object modules = array ? new JSONArray().put(new JSONObject().put("WS 026", left)).put(new JSONObject().put("WS 027", right))
				: new JSONObject().put("WS 026", left).put("WS 027", right);
		var json = new JSONObject().put("variantId", "parent").put("hullId", "parent").put("modules", modules);
		var variant = BattleNext_VariantBuilder.fromJson(json, "parent").build();
		assertEquals(2, variant.getModuleSlots().size());
		assertEquals(7, variant.getModuleVariant("WS 026").toJSONObject().getInt("fluxVents"));
		assertEquals(9, variant.getModuleVariant("WS 027").toJSONObject().getInt("fluxVents"));
	}

	@Test
	void regularWeaponSlotsCannotAcceptStationModules() throws Exception {
		hull("parent", "Parent", List.of("module"));
		hull("child", "Child", List.of());
		var json = new JSONObject().put("variantId", "parent").put("hullId", "parent").put("modules", new JSONObject()
				.put("weapon", new JSONObject().put("variantId", "child").put("hullId", "child")));
		var failure = assertThrows(IllegalArgumentException.class, () -> BattleNext_VariantBuilder.fromJson(json, "parent").build());
		assertEquals("Unknown module slot: weapon", failure.getMessage());
	}

	@Test
	void absentModuleSlotHasTheSameExplicitErrorAsAnInvalidSlotType() throws Exception {
		hulls.put("parent", proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> "parent";
			default -> null;
		}));
		var json = new JSONObject().put("variantId", "parent").put("hullId", "parent").put("modules", new JSONObject()
				.put("missing", new JSONObject().put("variantId", "child").put("hullId", "child")));
		var failure = assertThrows(IllegalArgumentException.class, () -> BattleNext_VariantBuilder.fromJson(json, "parent").build());
		assertEquals("Unknown module slot: missing", failure.getMessage());
	}
}
