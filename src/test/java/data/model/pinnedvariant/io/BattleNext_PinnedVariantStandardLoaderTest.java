package data.model.pinnedvariant.io;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_PinnedVariantStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void directoryFailurePreservesPublishedErrorsAndCatalogUntilSuccessfulReload() throws Exception {

		var root = directory.resolve("tournament/_pinnedVariants");
		Files.createDirectories(root);
		Files.writeString(root.resolve("broken.json"), "broken", StandardCharsets.UTF_8);
		var loader = new BattleNext_PinnedVariantStandardLoader();
		loader.load();
		var oldCatalog = loader.getCatalog();
		var oldErrors = loader.getLoadErrors();
		var previous = Global.getSettings();
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> {
			throw new IllegalStateException("injected directory failure");
		}));
		try {
			assertThrows(IOException.class, loader::load);
			assertEquals(oldCatalog, loader.getCatalog());
			assertEquals(oldErrors, loader.getLoadErrors());
		} finally {
			Global.setSettings(previous);
		}
		Files.writeString(root.resolve("broken.json"), new BattleNext_PinnedVariantModel("broken", "Fixed",
				BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson()).toJson().toString(), StandardCharsets.UTF_8);
		assertEquals(1, loader.load().size());
		assertEquals(List.of(), loader.getLoadErrors());
		assertEquals(1, oldErrors.size());
	}

	@Test
	void missingOptionalDirectoryLoadsAnEmptyCatalog() throws Exception {
		BattleNext_PinnedVariantStandardLoader loader = new BattleNext_PinnedVariantStandardLoader();
		assertTrue(loader.load().isEmpty());
	}

	@Test
	void nativeJsonServesBothCatalogAndParseAndRefreshesRenamedSnapshots() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("First", source().getApi());
		String path = pin.getSourcePath();
		BattleNext_PinnedVariantStandardLoader loader = new BattleNext_PinnedVariantStandardLoader();
		assertEquals("First", loader.parse(path).getDisplayName());
		assertEquals("First", loader.load().get(0).getDisplayName());
		store.rename(pin.getPinId(), "Second");
		assertEquals("Second", loader.parse(path).getDisplayName());
		assertEquals("Second", loader.load().get(0).getDisplayName());
		assertEquals(4, dataReads.stream().filter(read -> read.equals("loadJSON:" + path)).count());
		Files.writeString(directory.resolve("tournament/_pinnedVariants/broken.json"), "broken", StandardCharsets.UTF_8);
		assertEquals(1, loader.load().size());
		assertEquals(2, loader.getCatalog().size());
		assertEquals(1, loader.getLoadErrors().size());
		assertTrue(loader.getLoadErrors().get(0).contains("broken.json"));
	}
}
