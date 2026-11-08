package data.model.pinnedvariant;

import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.test.fixture.BattleNext_TestGameFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class BattleNext_PinnedVariantStoreTest extends BattleNext_TestGameFixture {

	@Test
	void catalogReusesItsRevisionAndPublishesNewRenamedAndDeletedPinsWithoutReloading() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		BattleNext_PinnedVariantModel first = store.saveVariant("First", source().getApi());
		BattleNext_PinnedVariantModel second = store.saveVariant("Second", source().getApi());
		List<BattleNext_PinnedVariantStandardLoader.CatalogEntry> catalog = store.getCatalog();
		assertSame(catalog, store.getCatalog());
		assertEquals(2, catalog.size());

		store.rename(first.getPinId(), "Renamed");
		assertNotSame(catalog, store.getCatalog());
		assertEquals("Renamed", store.getCatalog().stream().filter(entry -> entry.pinId().equals(first.getPinId())).findFirst().orElseThrow().name());

		int reads = dataReads.size();
		store.deleteFile(first.getSourcePath());
		assertEquals(List.of(second.getPinId()), store.getCatalog().stream().map(entry -> entry.pinId()).toList());

		store.delete(second.getPinId());
		assertTrue(store.getCatalog().isEmpty());
		assertEquals(reads, dataReads.size());
	}

	@Test
	void deletingOnePinPreservesGameValidationFailuresForOtherDefinitions() throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore(model -> { });
		store.reload();
		BattleNext_PinnedVariantModel first = store.saveVariant("First", source().getApi());
		JSONObject rejected = first.toJson().put("pinId", "rejected");
		rejected.getJSONObject("variantJson").put("hullId", "unknown");
		Files.writeString(directory.resolve("tournament/_pinnedVariants/rejected.json"), rejected.toString(), StandardCharsets.UTF_8);
		store.reload();
		store.validateDefinition("rejected");
		assertNull(store.get("rejected"));

		store.deleteFile(first.getSourcePath());
		assertNull(store.get("rejected"));
		assertNull(store.getCatalog().get(0).model());
		assertFalse(store.getCatalog().get(0).error().isEmpty());
	}

	@Test
	void catalogRetainsEveryErrorAndDeletesOnlyTheLoadedSourceFile() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		BattleNext_PinnedVariantModel pin = store.saveVariant("Valid", source().getApi());
		Path root = directory.resolve("tournament/_pinnedVariants");
		Files.writeString(root.resolve("broken.json"), "not json");
		Files.writeString(root.resolve("wrong.json"), pin.toJson().toString());
		JSONObject unavailable = pin.toJson();
		unavailable.put("pinId", "missing");
		unavailable.getJSONObject("variantJson").put("hullId", "unknown");
		Files.writeString(root.resolve("missing.json"), unavailable.toString());
		store.reload();
		assertEquals(4, store.getCatalog().size());
		assertEquals(0, store.getAllCopy().size());
		assertEquals(5, store.getLoadErrorsCopy().size());
		BattleNext_PinnedVariantStandardLoader.CatalogEntry missing = store.getCatalog().stream().filter(entry -> entry.path().endsWith("missing.json")).findFirst().orElseThrow();
		assertEquals("Valid", missing.name());
		assertEquals("unknown", missing.hullId());
		assertNull(missing.model());
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/_pinnedVariants/../wrong.json"));
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/_pinnedVariants/unloaded.json"));
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/other.json"));

		store.deleteFile("tournament/_pinnedVariants/wrong.json");
		assertFalse(Files.exists(root.resolve("wrong.json")));
		assertTrue(Files.exists(root.resolve(pin.getPinId() + ".json")));

		store.deleteFile("tournament/_pinnedVariants/broken.json");
		assertEquals(2, store.getCatalog().size());
	}

	@Test
	void saveRenameResolveAndDeleteKeepTheSnapshotImmutable() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		assertThrows(IllegalStateException.class, () -> store.resolve("unknown"));
		store.reload();
		BattleNext_PinnedVariantModel pin = store.saveVariant("Original", source().getApi());
		byte[] original = Files.readAllBytes(directory.resolve(pin.getSourcePath()));
		JSONObject changed = pin.getVariantJsonCopy().put("fluxVents", 99);
		assertThrows(IllegalArgumentException.class, () -> store.save(new BattleNext_PinnedVariantModel(pin.getPinId(), "Changed", changed)));
		assertArrayEquals(original, Files.readAllBytes(directory.resolve(pin.getSourcePath())));
		assertThrows(IllegalArgumentException.class, () -> store.rename(pin.getPinId(), " "));
		assertEquals("Original", store.get(pin.getPinId()).getDisplayName());

		store.rename(pin.getPinId(), "Renamed");
		assertEquals(pin.getVariantJsonCopy().toString(), store.get(pin.getPinId()).getVariantJsonCopy().toString());
		ShipVariantAPI first = store.resolve(pin.getPinId());
		first.setNumFluxVents(100);
		assertNotEquals(100, store.resolve(pin.getPinId()).toJSONObject().optInt("fluxVents"));
		store.reload();
		assertEquals("Renamed", store.get(pin.getPinId()).getDisplayName());

		store.delete(pin.getPinId());
		assertNull(store.get(pin.getPinId()));
		assertFalse(Files.exists(directory.resolve(pin.getSourcePath())));
		assertThrows(IllegalArgumentException.class, () -> store.resolve(pin.getPinId()));
	}

	@Test
	void persistedCollisionKeepsTheCatalogAndSnapshotUnchanged() throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		BattleNext_PinnedVariantModel collision = BattleNext_PinnedVariantModel.fromVariant("collision", "First", source().getApi());
		Files.createDirectories(directory.resolve("tournament/_pinnedVariants"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants/collision.json"), collision.toJson().toString(), StandardCharsets.UTF_8);
		assertThrows(IllegalArgumentException.class, () -> store.save(collision));
		assertNull(store.get("collision"));

		store.reload();
		BattleNext_PinnedVariantModel before = store.get("collision");
		Files.createDirectory(directory.resolve("tournament/_pinnedVariants/blocked.json"));
		BattleNext_PinnedVariantModel blocked = BattleNext_PinnedVariantModel.fromVariant("blocked", "Blocked", source().getApi());
		assertThrows(IllegalArgumentException.class, () -> store.save(blocked));
		assertSame(before, store.get("collision"));
		assertNull(store.get("blocked"));
	}

	@Test
	void catalogOrdersNamesThenIdentitiesThenPathsWithoutChangingTheInput() {
		BattleNext_PinnedVariantStandardLoader.CatalogEntry pathLast = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("z", "Same", "b", "", null, "", "");
		BattleNext_PinnedVariantStandardLoader.CatalogEntry pathFirst = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("a", "Same", "b", "", null, "", "");
		BattleNext_PinnedVariantStandardLoader.CatalogEntry identityFirst = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("c", "Same", "a", "", null, "", "");
		BattleNext_PinnedVariantStandardLoader.CatalogEntry nameFirst = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("d", "Alpha", "z", "", null, "", "");
		List<BattleNext_PinnedVariantStandardLoader.CatalogEntry> entries = List.of(pathLast, pathFirst, identityFirst, nameFirst);
		assertEquals(List.of(nameFirst, identityFirst, pathFirst, pathLast), BattleNext_PinnedVariantPage.sorted(entries));
		assertEquals(List.of(pathLast, pathFirst, identityFirst, nameFirst), entries);
	}
}
