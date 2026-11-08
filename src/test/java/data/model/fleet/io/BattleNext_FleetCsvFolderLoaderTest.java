package data.model.fleet.io;

import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_AssembledFleet;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_FleetCsvFolderLoaderTest extends BattleNext_TestFleetIoFixture {

	@Test
	void chinesePackageLoadsAllPairsAndResolvesLocalModulesBeforeGlobalResources() throws Exception {

		csvFleet("中文目录", "1", "1,local_parent,,,,,,\n2,parent_variant,,,,,,\n");
		csvFleet("中文目录", "02", "1,local_child,,,,,,\n");
		variant("中文目录", "local_parent", "parent", 21, new JSONArray().put(new JSONObject().put("module", "local_child")));
		variant("中文目录", "local_child", "child", 17, null);
		registeredVariants.put("local_child", selectedVariant.getModules().get("module"));
		List<BattleNext_FleetModel> fleets = new BattleNext_FleetLegacyFolderLoader().load();
		assertEquals(List.of("02", "1"), fleets.stream().map(fleet -> fleet.getId()).toList());
		BattleNext_FleetModel fleet = fleets.get(1);
		assertEquals("中文目录", fleet.getFolderName());
		assertEquals("tournament/中文目录/player1_data.csv", fleet.getSourcePath());
		assertEquals(BattleNext_LoadSource.LEGACY, fleet.getSourceType());
		assertEquals(2, fleet.getVariantsCopy().size());
		assertTrue(fleet.getLoadErrorsCopy().isEmpty());
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(fleet);
		assertTrue(assembled.isComplete(), assembled.getMemberErrorsReadonly().toString());
		assertEquals(17, assembled.getMembersReadonly().get(0).getVariant().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		assertEquals("parent_variant", assembled.getMembersReadonly().get(1).getVariant().getHullVariantId());
	}

	@Test
	void sourceBoundariesAndStandardMetadataDetermineDirectoryOwnership() throws Exception {

		csvFleet("", "1", "1,parent_variant,,,,,,\n");
		csvFleet("included", "2", "1,parent_variant,,,,,,\n");
		csvFleet("included/deep", "3", "1,parent_variant,,,,,,\n");
		csvFleet("_rules", "4", "1,parent_variant,,,,,,\n");
		csvFleet("standard", "5", "1,parent_variant,,,,,,\n");
		standard("standard", "standard_id", "parent_variant");
		write("tournament/incomplete/player6_data.csv", "name,author,skills,tags\nTeam,,,\n");
		assertEquals(List.of("2"), new BattleNext_FleetLegacyFolderLoader().load().stream().map(fleet -> fleet.getId()).toList());
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		assertEquals(3, store.getAllCopy().size());
		assertNotNull(store.get("standard_id"));
	}

	@Test
	void localModulesRemainIsolatedBetweenPackagesAndAcrossReloads() throws Exception {

		csvFleet("alpha", "1", "1,parent_local,,,,,,\n");
		csvFleet("beta", "2", "1,parent_local,,,,,,\n");
		for (String folder : List.of("alpha", "beta")) {
			variant(folder, "parent_local", "parent", 0, new JSONObject().put("module", "child_local"));
			variant(folder, "child_local", "child", folder.equals("alpha") ? 11 : 22, null);
		}
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		for (int iteration = 0; iteration < 2; iteration++) {
			store.reload();
			assertEquals(11, store.get("1").getVariant("parent_local").build().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
			assertEquals(22, store.get("2").getVariant("parent_local").build().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		}
	}

	@Test
	void nestedInlineModulesResolveLocalReferencesAndGlobalExternalModules() throws Exception {

		hull("middle", "Middle", List.of("nested"));
		csvFleet("nested", "1", "1,parent_local,,,,,,\n");
		JSONObject inline = new JSONObject().put("variantId", "inline").put("hullId", "middle")
				.put("modules", new JSONObject().put("nested", "child_local"));
		variant("nested", "parent_local", "parent", 0, new JSONObject().put("module", inline));
		variant("nested", "child_local", "child", 31, null);
		ShipVariantAPI parent = new BattleNext_FleetLegacyFolderLoader().load().get(0).getVariant("parent_local").build();
		assertEquals(31, parent.getModuleVariant("module").getModuleVariant("nested").toJSONObject().getInt("fluxVents"));
		variant("nested", "parent_local", "parent", 0, new JSONObject().put("module", "child_variant"));
		parent = new BattleNext_FleetLegacyFolderLoader().load().get(0).getVariant("parent_local").build();
		assertEquals(7, parent.getModuleVariant("module").toJSONObject().getInt("fluxVents"));
	}

	@Test
	void cyclicAndDuplicateVariantsReportTheirSourcePaths() throws Exception {

		csvFleet("cycle", "1", "1,parent_local,,,,,,\n");
		variant("cycle", "parent_local", "parent", 0, new JSONObject().put("module", "child_local"));
		variant("cycle", "child_local", "child", 0, new JSONObject().put("back", "parent_local"));
		BattleNext_FleetModel cyclic = new BattleNext_FleetLegacyFolderLoader().load().get(0);
		assertTrue(String.join(";", cyclic.getLoadErrorsCopy()).contains("Cyclic module variant"));
		assertTrue(String.join(";", cyclic.getLoadErrorsCopy()).contains("tournament/cycle/"));
		variant("cycle", "child_local", "child", 0, null);
		write("tournament/cycle/duplicate.variant", "{\"variantId\":\"child_local\",\"hullId\":\"child\"}");
		BattleNext_FleetModel duplicate = new BattleNext_FleetLegacyFolderLoader().load().get(0);
		assertTrue(String.join(";", duplicate.getLoadErrorsCopy()).contains("child_local.variant"));
		assertTrue(String.join(";", duplicate.getLoadErrorsCopy()).contains("duplicate.variant"));
		assertNull(duplicate.getVariant("child_local"));
	}

	@Test
	void missingMembersAndModulesUseThePublishedErrorChannels() throws Exception {

		csvFleet("missing", "1", "1,parent_local,,,,,,\n2,unknown_member,,,,,,\n");
		variant("missing", "parent_local", "parent", 0, new JSONObject().put("module", "unknown_module"));
		BattleNext_FleetModel fleet = new BattleNext_FleetLegacyFolderLoader().load().get(0);
		assertEquals(1, fleet.getLoadErrorsCopy().size());
		assertTrue(fleet.getLoadErrorsCopy().get(0).contains("unknown_member"));
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(fleet);
		assertEquals(3, assembled.getMemberErrorsReadonly().size());
		assertTrue(assembled.getMemberErrorsReadonly().get(0).contains("unknown_member"));
		assertTrue(assembled.getMemberErrorsReadonly().get(1).contains("unknown_module"));
		assertTrue(assembled.getMemberErrorsReadonly().get(2).contains("unknown_member"));
	}
}
