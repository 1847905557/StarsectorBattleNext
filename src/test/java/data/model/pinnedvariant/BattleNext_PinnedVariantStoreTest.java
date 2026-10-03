package data.model.pinnedvariant;

import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader;
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
		assertEquals(1, store.getAll().size());
		assertEquals(3, store.getLoadErrors().size());
		var missing = store.getCatalog().stream().filter(entry -> entry.path().endsWith("missing.json")).findFirst().orElseThrow();
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
		var pin = store.saveVariant("Original", source().getApi());
		byte[] original = Files.readAllBytes(directory.resolve(pin.getSourcePath()));
		JSONObject changed = pin.getVariantJson().put("fluxVents", 99);
		assertThrows(IllegalArgumentException.class, () -> store.save(new BattleNext_PinnedVariantModel(pin.getPinId(), "Changed", changed)));
		assertArrayEquals(original, Files.readAllBytes(directory.resolve(pin.getSourcePath())));
		assertThrows(IllegalArgumentException.class, () -> store.rename(pin.getPinId(), " "));
		assertEquals("Original", store.get(pin.getPinId()).getDisplayName());
		store.rename(pin.getPinId(), "Renamed");
		assertEquals(pin.getVariantJson().toString(), store.get(pin.getPinId()).getVariantJson().toString());
		var first = store.resolve(pin.getPinId());
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
		var store = new BattleNext_PinnedVariantStore();
		store.reload();
		var collision = BattleNext_PinnedVariantModel.fromVariant("collision", "First", source().getApi());
		Files.createDirectories(directory.resolve("tournament/_pinnedVariants"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants/collision.json"), collision.toJson().toString(), StandardCharsets.UTF_8);
		assertThrows(IllegalArgumentException.class, () -> store.save(collision));
		assertNull(store.get("collision"));
		store.reload();
		var before = store.get("collision");
		Files.createDirectory(directory.resolve("tournament/_pinnedVariants/blocked.json"));
		var blocked = BattleNext_PinnedVariantModel.fromVariant("blocked", "Blocked", source().getApi());
		assertThrows(IllegalArgumentException.class, () -> store.save(blocked));
		assertSame(before, store.get("collision"));
		assertNull(store.get("blocked"));
	}

	@Test
	void catalogSortUsesNameIdAndPath() {
		var a = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("z", "Same", "b", "", null, "");
		var b = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("a", "Same", "b", "", null, "");
		var c = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("c", "Same", "a", "", null, "");
		var d = new BattleNext_PinnedVariantStandardLoader.CatalogEntry("d", "Alpha", "z", "", null, "");
		assertEquals(List.of(d, c, b, a), BattleNext_PinnedVariantPage.sorted(List.of(a, b, c, d)));
	}

}
