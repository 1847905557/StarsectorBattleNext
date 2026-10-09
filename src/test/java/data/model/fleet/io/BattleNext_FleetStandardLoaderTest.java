package data.model.fleet.io;

import data.scripts.fleet.BattleNext_FleetBuilder;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_FleetStandardLoaderTest extends BattleNext_TestFleetIoFixture {

	@Test
	void numericFleetIdentityBelongsToTheLegacyNamespace() throws Exception {
		standard("reserved", "007", "parent_variant");
		standard("available", "fleet_007", "parent_variant");
		var loader = new BattleNext_FleetStandardLoader();
		assertEquals(List.of("fleet_007"), loader.load().stream().map(fleet -> fleet.getId()).toList());
		assertEquals(1, loader.getErrors().size());
		var error = loader.getErrors().get(0);
		assertEquals("007", error.id());
		assertEquals("tournament/reserved/fleet_data.json", error.path());
		assertTrue(error.diagnostic().contains("Numeric fleet IDs are reserved for legacy fleets: 007"));
	}

	@Test
	void nativeJsonLoadsFleetAndLocalModulesWithMixedRegisteredMembers() throws Exception {

		standard("standard", "standard", "local_parent", "parent_variant");
		variant("standard", "local_parent", "parent", 0, new JSONObject().put("module", "local_child"));
		variant("standard", "local_child", "child", 34, null);
		variant("standard/deep", "outside", "child", 99, null);
		var fleet = new BattleNext_FleetStandardLoader().load().get(0);
		assertEquals(2, fleet.getVariants().size());
		assertTrue(fleet.getLoadErrors().isEmpty());
		assertEquals(List.of("loadJSON:tournament/standard/fleet_data.json", "loadJSON:tournament/standard/local_child.variant",
				"loadJSON:tournament/standard/local_parent.variant"), dataReads);
		var assembled = new BattleNext_FleetBuilder().build(fleet);
		assertTrue(assembled.isComplete(), assembled.getMemberErrors().toString());
		assertEquals(34, assembled.getMembers().get(0).getVariant().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
	}

	@Test
	void malformedVariantReportsItsExactPath() throws Exception {
		standard("broken", "broken", "parent_variant");
		write("tournament/broken/broken.variant", "not json");
		var fleet = new BattleNext_FleetStandardLoader().load().get(0);
		assertEquals("broken", fleet.getId());
		assertTrue(fleet.getLoadErrors().get(0).contains("tournament/broken/broken.variant"));
	}
}
