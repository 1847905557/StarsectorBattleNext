package data.model.tournament;

import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

class BattleNext_TournamentStoreTest extends BattleNext_TestGameFixture {

	@Test
	void configurationStorageKeepsDirectoryIdentityAndReservesTheInstanceFileName() throws Exception {
		BattleNext_TournamentStore store = new BattleNext_TournamentStore();
		store.reload();
		BattleNext_TournamentModel first = new BattleNext_TournamentModel("first", "First", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		store.save(first);
		store.reload();
		assertEquals(List.of("first"), store.getAll().stream().map(BattleNext_TournamentModel::getId).toList());
		for (String id : List.of("started_tournament", "STARTED_TOURNAMENT")) {
			assertThrows(IllegalArgumentException.class, () -> store.save(new BattleNext_TournamentModel(id, "Reserved",
					BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2)));
		}
		assertFalse(Files.exists(directory.resolve(BattleNext_StartedTournamentStore.PATH)));
		store.delete("first");
		store.reload();
		assertTrue(store.getAll().isEmpty());
	}
}
