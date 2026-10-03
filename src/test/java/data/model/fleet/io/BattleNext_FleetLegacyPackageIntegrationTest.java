package data.model.fleet.io;

import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class BattleNext_FleetLegacyPackageIntegrationTest extends BattleNext_TestFleetIoFixture {

	@Test
	void tranquilityPackageUsesFormalCatalogAndAssemblyEntrypoints() throws Exception {

		Path packageRoot;
		try (var folders = Files.list(Path.of("tournament"))) {
			packageRoot = folders.filter(Files::isDirectory)
					.filter(folder -> Files.exists(folder.resolve("player740_data.csv"))).findFirst().orElseThrow();
		}
		try (var files = Files.list(packageRoot)) {
			for (var file : files.filter(Files::isRegularFile).toList()) {
				write(packageRoot.resolve(file.getFileName()).toString(), Files.readString(file, StandardCharsets.UTF_8));
			}
		}
		hull("brawler_tritachyon", "Brawler", List.of());
		hull("anubis", "Anubis", List.of());
		hull("onslaught_mk1", "Onslaught", List.of("WS 026", "WS 027"));
		hull("module_onslaught_armor_left", "Left", List.of());
		hull("module_onslaught_armor_right", "Right", List.of());
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		var fleet = store.get("ab_player740");
		assertEquals(1, store.getAll().size());
		assertEquals("宁静之海", fleet.getName());
		assertEquals("Scythe", fleet.getAuthor());
		assertEquals(12, fleet.getSkills().size());
		assertEquals(List.of("full_assault"), fleet.getTags());
		assertEquals(28, fleet.getMembers().size());
		assertEquals(5, fleet.getVariants().size());
		assertEquals(Map.of("brawler_tritachyon_Rangers", 14L, "anubis_STTA", 13L, "onslaught_mk1_STTN", 1L),
				fleet.getMembers().stream().collect(Collectors.groupingBy(member -> member.getVariantId(), Collectors.counting())));
		assertTrue(fleet.getLoadErrors().isEmpty());
		var assembled = new BattleNext_FleetBuilder().build(fleet);
		assertTrue(assembled.isComplete(), assembled.getMemberErrors().toString());
		assertEquals(28, assembled.getMembers().size());
		var onslaught = assembled.getMembers().get(4).getVariant();
		assertEquals("module_onslaught_armor_left_STTN", onslaught.getModuleVariant("WS 026").getHullVariantId());
		assertEquals("module_onslaught_armor_right_STTN", onslaught.getModuleVariant("WS 027").getHullVariantId());
		assertEquals(2, dataReads.stream().filter(read -> read.startsWith("loadCSV:")).count());
		assertEquals(5, dataReads.stream().filter(read -> read.startsWith("loadJSON:")).count());
		standard("alpha", "alpha", "parent_variant");
		standard("beta", "beta", "parent_variant");
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,-1,1\nbeta,alpha,-1,1\n"
				+ "ab_player740,ab_player740,-1,1\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n740,740,8,1\n");
		var provider = new BattleNext_BattleSourceProvider();
		var matches = provider.load();
		assertEquals(4, matches.size(), provider.getWarnings().toString());
		assertTrue(provider.getWarnings().isEmpty());
		assertEquals("ab_player740", matches.get(2).getPlayer());
		assertEquals("ab_player740", matches.get(2).getEnemy());
		assertEquals("ab_player740", matches.get(3).getPlayer());
		assertEquals("ab_player740", matches.get(3).getEnemy());
	}
}
