package data.model.quickbattle;

import data.model.BattleNext_LoadSource;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_QuickBattleStoreTest extends BattleNext_TestFleetIoFixture {

	@Test
	void headerOnlyStandardAndUnavailableLegacyPublishAnEmptyCatalog() throws Exception {
		write("tournament/quick_battle.csv", "player,enemy,map,size\n");
		BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertEquals(List.of(), store.getAll());
	}

	@Test
	void updatingAppendingAndDeletingRowsPreserveSourceRoutingAndOtherFileBytes() throws Exception {

		write("tournament/quick_battle.csv", "player,enemy,map,size\na,b,1,1\n");
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n01,02,3,1\n# note\n");
		byte[] standard = Files.readAllBytes(directory.resolve("tournament/quick_battle.csv"));
		var store = new BattleNext_QuickBattleStore();
		store.reload();
		var edited = store.get("pvp-legacy-0").copy();
		edited.setMap(4);
		store.save(edited);
		assertEquals(BattleNext_LoadSource.LEGACY, edited.getSourceType());
		assertEquals("tournament/PvP_matches.csv", edited.getSourcePath());
		assertArrayEquals(standard, Files.readAllBytes(directory.resolve("tournament/quick_battle.csv")));
		var added = new BattleNext_QuickBattleModel("c", "d", 2, 1);
		added.setId("new");
		byte[] legacy = Files.readAllBytes(directory.resolve("tournament/PvP_matches.csv"));
		store.save(added);
		assertEquals("tournament/quick_battle.csv", added.getSourcePath());
		assertArrayEquals(legacy, Files.readAllBytes(directory.resolve("tournament/PvP_matches.csv")));
		store.delete("pvp-legacy-0");
		assertEquals(List.of("player,enemy,map,size", "# note"), Files.readAllLines(directory.resolve("tournament/PvP_matches.csv"), StandardCharsets.UTF_8));
		store.reload();
		assertEquals(3, store.getAll().size());
		assertEquals("c", store.get("quick-standard-1").getPlayer());
	}

	@Test
	void failedFileWriteKeepsPublishedRowsAndReplacementSource() throws Exception {
		write("tournament/quick_battle.csv", "player,enemy,map,size\na,b,1,1\n");
		var store = new BattleNext_QuickBattleStore();
		store.reload();
		var original = store.get("quick-standard-0");
		var replacement = original.copy();
		replacement.setPlayer("replacement");
		Files.delete(directory.resolve("tournament/quick_battle.csv"));
		Files.createDirectory(directory.resolve("tournament/quick_battle.csv"));
		assertThrows(IOException.class, () -> store.save(replacement));
		assertSame(original, store.get(original.getId()));
		assertEquals(BattleNext_LoadSource.STANDARD, replacement.getSourceType());
		assertEquals(original.getSourcePath(), replacement.getSourcePath());
		assertThrows(IOException.class, () -> store.delete(original.getId()));
		assertSame(original, store.get(original.getId()));
	}
}
