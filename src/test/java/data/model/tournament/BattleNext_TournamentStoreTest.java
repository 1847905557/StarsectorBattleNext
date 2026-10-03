package data.model.tournament;

import data.model.tournament.io.BattleNext_TournamentSaver;
import java.io.IOException;
import org.json.JSONException;

import data.model.tournament.BattleNext_TournamentStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

class BattleNext_TournamentStoreTest extends BattleNext_TestGameFixture {

	@Test
	void activeSelectionSurvivesReloadAndDeletingItClearsPersistedMetadata() throws Exception {

		var store = new BattleNext_TournamentStore();
		assertThrows(IllegalStateException.class, () -> store.setActiveId(""));
		store.reload();
		var first = new BattleNext_TournamentModel("first", "First", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		var second = new BattleNext_TournamentModel("second", "Second", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		store.save(first);
		store.save(second);
		assertThrows(IllegalArgumentException.class, () -> store.setActiveId("missing"));
		store.setActiveId("first");
		store.reload();
		assertEquals("first", store.getActiveId());
		assertTrue(store.isManaged("first"));
		store.delete("second");
		assertEquals("first", store.getActiveId());
		store.delete("first");
		assertEquals("", store.getActiveId());
		assertEquals("", new JSONObject(Files.readString(directory.resolve("tournament/_tournaments/meta.json"), StandardCharsets.UTF_8)).getString("activeTournamentId"));
		store.reload();
		assertTrue(store.getAll().isEmpty());
		assertEquals("", store.getActiveId());
	}

	@Test
	void malformedActiveStateKeepsBothPriorCatalogAndSelection() throws Exception {
		var store = new BattleNext_TournamentStore();
		store.reload();
		var original = new BattleNext_TournamentModel("original", "Original", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		store.save(original);
		store.setActiveId("original");
		var meta = directory.resolve("tournament/_tournaments/meta.json");
		Files.writeString(meta, "broken", StandardCharsets.UTF_8);
		assertThrows(JSONException.class, store::reload);
		assertSame(original, store.get("original"));
		assertEquals("original", store.getActiveId());
		assertFalse(store.isLoaded());
		new BattleNext_TournamentSaver().saveActiveId("");
		store.reload();
		assertTrue(store.isLoaded());
		assertEquals("", store.getActiveId());
	}

	@Test
	void failedActiveWriteKeepsThePreviouslyPublishedSelection() throws Exception {
		var store = new BattleNext_TournamentStore();
		store.reload();
		var original = new BattleNext_TournamentModel("original", "Original", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		store.save(original);
		store.setActiveId("original");
		var meta = directory.resolve("tournament/_tournaments/meta.json");
		Files.delete(meta);
		Files.createDirectory(meta);
		assertThrows(IOException.class, () -> store.setActiveId(""));
		assertEquals("original", store.getActiveId());
		assertSame(original, store.get("original"));
	}

	@Test
	void missingOptionalDirectoryPublishesAnEmptyLoadedCatalog() throws Exception {
		BattleNext_TournamentStore store = new BattleNext_TournamentStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}
}
