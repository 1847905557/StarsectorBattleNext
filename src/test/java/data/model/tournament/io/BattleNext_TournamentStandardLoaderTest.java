package data.model.tournament.io;

import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.test.fixture.BattleNext_TestGameFixture;
import org.json.JSONException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_TournamentStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void catalogFiltersMetadataAndPropagatesItsOriginToNestedMatches() throws Exception {
		var root = directory.resolve("tournament/_tournaments");
		Files.createDirectories(root.resolve("nested"));
		var tournament = new BattleNext_TournamentModel("event", "Event",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		tournament.addMatch(new BattleNext_Match("match", "a", "b", 1, 1));
		Files.writeString(root.resolve("event.json"), tournament.toJson().toString(), StandardCharsets.UTF_8);
		Files.writeString(root.resolve("meta.json"), "broken metadata", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("nested/ignored.json"), "broken", StandardCharsets.UTF_8);
		var loaded = new BattleNext_TournamentStandardLoader().load();
		assertEquals(1, loaded.size());
		assertEquals("event", loaded.get(0).getId());
		assertEquals("tournament/_tournaments/event.json", loaded.get(0).getSourcePath());
		assertEquals(loaded.get(0).getSourcePath(), loaded.get(0).getMatches().get(0).getSourcePath());
		assertEquals(BattleNext_LoadSource.STANDARD, loaded.get(0).getMatches().get(0).getSourceType());
		assertEquals(List.of("loadJSON:tournament/_tournaments/event.json"), dataReads);
	}

	@Test
	void missingOptionalDirectoryLoadsAnEmptyCatalog() throws Exception {
		BattleNext_TournamentStandardLoader loader = new BattleNext_TournamentStandardLoader();
		assertTrue(loader.load().isEmpty());
	}

	@Test
	void nativeJsonReadsFreshActiveStateAndPreservesVersionSemantics() throws Exception {

		var root = directory.resolve("tournament/_tournaments");
		Files.createDirectories(root);
		var meta = root.resolve("meta.json");
		BattleNext_TournamentStandardLoader loader = new BattleNext_TournamentStandardLoader();
		assertEquals("", loader.loadActiveId());
		Files.writeString(meta, "{\"formatVersion\":1,\"activeTournamentId\":\"first\"}", StandardCharsets.UTF_8);
		assertEquals("first", loader.loadActiveId());
		Files.writeString(meta, "{\"formatVersion\":1,\"activeTournamentId\":\"second\"}", StandardCharsets.UTF_8);
		assertEquals("second", loader.loadActiveId());
		Files.writeString(meta, "{\"formatVersion\":2,\"activeTournamentId\":\"unsupported\"}", StandardCharsets.UTF_8);
		assertEquals("", loader.loadActiveId());
		assertEquals(3, dataReads.stream().filter(read -> read.equals("loadJSON:tournament/_tournaments/meta.json")).count());
		Files.writeString(meta, "broken", StandardCharsets.UTF_8);
		assertThrows(JSONException.class, loader::loadActiveId);
	}
}
