package data.model.fleet.io;

import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.model.fleet.BattleNext_FleetModel;
import data.model.BattleNext_BaseLoader.LoadError;
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
		BattleNext_FleetStandardLoader loader = new BattleNext_FleetStandardLoader();
		assertEquals(List.of("fleet_007"), loader.load().stream().map(fleet -> fleet.getId()).toList());
		assertEquals(1, loader.getErrorsCopy().size());
		LoadError error = loader.getErrorsCopy().get(0);
		assertEquals("007", error.id());
		assertEquals("tournament/reserved/fleet_data.json", error.path());
		assertTrue(error.diagnostic().contains("Numeric fleet IDs are reserved for legacy fleets: 007"));
	}

	@Test
	void fleetIdentityRejectsWhitespaceEvenWhenItsFolderIsValid() throws Exception {
		standard("invalid", "fleet id", "parent_variant");
		standard("valid", "fleet_id", "parent_variant");
		BattleNext_FleetStandardLoader loader = new BattleNext_FleetStandardLoader();
		assertEquals(List.of("fleet_id"), loader.load().stream().map(fleet -> fleet.getId()).toList());
		assertEquals(1, loader.getErrorsCopy().size());
		assertTrue(loader.getErrorsCopy().get(0).diagnostic().contains("Invalid fleet ID: fleet id"));
	}

	@Test
	void nativeJsonLoadsFleetAndLocalModulesWithMixedRegisteredMembers() throws Exception {

		standard("standard", "standard", "local_parent", "parent_variant");
		variant("standard", "local_parent", "parent", 0, new JSONObject().put("module", "local_child"));
		variant("standard", "local_child", "child", 34, null);
		variant("standard/deep", "outside", "child", 99, null);
		BattleNext_FleetModel fleet = new BattleNext_FleetStandardLoader().load().get(0);
		assertEquals(2, fleet.getVariantsCopy().size());
		assertTrue(fleet.getLoadErrorsCopy().isEmpty());
		assertEquals(List.of("loadJSON:tournament/standard/fleet_data.json", "loadJSON:tournament/standard/local_child.variant",
				"loadJSON:tournament/standard/local_parent.variant"), dataReads);
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(fleet);
		assertTrue(assembled.isComplete(), assembled.getMemberErrorsReadonly().toString());
		assertEquals(34, assembled.getMembersReadonly().get(0).getVariant().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
	}

	@Test
	void malformedVariantReportsItsExactPath() throws Exception {
		standard("broken", "broken", "parent_variant");
		write("tournament/broken/broken.variant", "not json");
		BattleNext_FleetModel fleet = new BattleNext_FleetStandardLoader().load().get(0);
		assertEquals("broken", fleet.getId());
		assertTrue(fleet.getLoadErrorsCopy().get(0).contains("tournament/broken/broken.variant"));
	}
}
