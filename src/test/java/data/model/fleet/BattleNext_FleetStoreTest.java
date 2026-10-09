package data.model.fleet;

import data.model.BattleNext_LoadSource;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class BattleNext_FleetStoreTest extends BattleNext_TestFleetIoFixture {

	@Test
	void managedDeletionReleasesItsDirectoryAndNumberedCsvDeletionPreservesTheSource() throws Exception {
		csvFleet("", "1", "1,parent_variant,,,,,,\n");
		standard("managed", "managed", "parent_variant");
		var store = new BattleNext_FleetStore();
		store.reload();
		assertFalse(store.isManaged("1"));
		assertTrue(store.isManaged("managed"));
		assertThrows(IllegalStateException.class, () -> store.delete("1"));
		assertTrue(Files.exists(directory.resolve("tournament/player1_data.csv")));
		store.delete("managed");
		assertFalse(Files.exists(directory.resolve("tournament/managed")));
		store.reload();
		assertEquals(1, store.getAll().size());
		assertEquals("1", store.getAll().get(0).getId());
	}

	@Test
	void missingOptionalDirectoryPublishesAnEmptyLoadedCatalog() throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}

	@Test
	void conflictingSourcesPublishDiagnosticsForEveryIdentityAndKeepOtherFleets() throws Exception {

		csvFleet("", "1", "1,parent_variant,,,,,,\n");
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		standard("standard", "1", "parent_variant");
		standard("healthy", "healthy", "parent_variant");
		store.reload();
		assertTrue(store.isLoaded());
		assertEquals(1, store.getAll().size());
		assertNotNull(store.get("healthy"));
		assertNull(store.get("1"));
		assertEquals(2, store.getErrors().size());
		assertEquals(Set.of("tournament/player1_data.csv", "tournament/standard/fleet_data.json"),
				store.getErrors().stream().map(error -> error.path()).collect(Collectors.toSet()));
	}

	@Test
	void rootAndFolderConflictUsesTheSameNumberedCsvIdentity() throws Exception {
		csvFleet("", "01", "1,parent_variant,,,,,,\n");
		csvFleet("package", "01", "1,parent_variant,,,,,,\n");
		var store = new BattleNext_FleetStore();
		store.reload();
		assertTrue(store.getAll().isEmpty());
		assertEquals(2, store.getErrors().size());
		assertTrue(store.getErrors().stream().allMatch(error -> error.id().equals("01")));
		assertEquals(Set.of("tournament/player01_data.csv", "tournament/package/player01_data.csv"),
				store.getErrors().stream().map(error -> error.path()).collect(Collectors.toSet()));
	}

	@Test
	void leadingZerosKeepDistinctNumberStrings() throws Exception {
		csvFleet("", "01", "1,parent_variant,,,,,,\n");
		csvFleet("package", "1", "1,parent_variant,,,,,,\n");
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		assertEquals(2, store.getAll().size());
		assertNotNull(store.get("01"));
		assertNotNull(store.get("1"));
	}

	@Test
	void distinctPackagesAndStandardDirectoriesReportDuplicateIds() throws Exception {
		csvFleet("alpha", "1", "1,parent_variant,,,,,,\n");
		csvFleet("beta", "1", "1,parent_variant,,,,,,\n");
		var store = new BattleNext_FleetStore();
		store.reload();
		assertTrue(store.getAll().isEmpty());
		assertEquals(Set.of("tournament/alpha/player1_data.csv", "tournament/beta/player1_data.csv"),
				store.getErrors().stream().map(error -> error.path()).collect(Collectors.toSet()));
		standard("alpha", "same", "parent_variant");
		standard("beta", "same", "parent_variant");
		store.reload();
		assertTrue(store.getAll().isEmpty());
		assertEquals(Set.of("tournament/alpha/fleet_data.json", "tournament/beta/fleet_data.json"),
				store.getErrors().stream().map(error -> error.path()).collect(Collectors.toSet()));
	}

	@Test
	void exportPreservesEveryNumberedCsvByteAndReloadsCompleteLocalModules() throws Exception {

		csvFleet("package", "1", "1,parent_local,,,,,,\n");
		csvFleet("other", "2", "1,parent_variant,,,,,,\n");
		variant("package", "parent_local", "parent", 4, new JSONObject().put("module", "child_local"));
		variant("package", "child_local", "child", 27, null);
		write("tournament/package/使用须知.txt", "说明内容\n");
		write("tournament/package/ship_data.csv", "name,id\n船体,example\n");
		Map<String, byte[]> original = new LinkedHashMap<>();
		try (var files = Files.walk(directory.resolve("tournament/package"))) {
			for (var path : files.filter(Files::isRegularFile).toList()) {
				original.put(path.toString(), Files.readAllBytes(path));
			}
		}
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		var source = store.get("1");
		BattleNext_FleetModel copy = new BattleNext_FleetModel();
		copy.fromJson(source.toJson());
		copy.setFolderName(source.getFolderName());
		copy.setSourceType(source.getSourceType());
		copy.setSourcePath(source.getSourcePath());
		source.getVariants().forEach(copy::putVariant);
		assertThrows(IOException.class, () -> store.save(copy));
		copy.setId("exported");
		assertThrows(IOException.class, () -> store.save(copy));
		copy.setFolderName("PACKAGE");
		assertThrows(IOException.class, () -> store.save(copy));
		copy.setFolderName("other");
		assertThrows(IOException.class, () -> store.save(copy));
		copy.setFolderName("exported");
		store.save(copy);
		assertEquals(BattleNext_LoadSource.CREATED, copy.getSourceType());
		store.reload();
		assertEquals(3, store.getAll().size());
		var exported = store.get("exported");
		assertEquals(BattleNext_LoadSource.STANDARD, exported.getSourceType());
		assertEquals(27, exported.getVariant("parent_local").build().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		assertTrue(new BattleNext_FleetBuilder().build(exported).isComplete());
		for (var entry : original.entrySet()) {
			assertArrayEquals(entry.getValue(), Files.readAllBytes(Path.of(entry.getKey())));
		}
		try (var files = Files.walk(directory.resolve("tournament/package"))) {
			assertEquals(original.size(), files.filter(Files::isRegularFile).count());
		}
	}
}
