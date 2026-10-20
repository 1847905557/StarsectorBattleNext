package data.scripts.tools;

import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;

class BattleNext_EntryIdTest extends BattleNext_TestFleetIoFixture {

	@ParameterizedTest
	@ValueSource(strings = {" ", "\t", "\n", "\u00a0", "\u0085", "\u2007", "\u3000"})
	void sharedEntryIdsRejectAndTrimUnicodeWhitespace(String whitespace) {
		String id = "fleet" + whitespace + "id";
		assertFalse(BattleNext_EntryId.isValid(id));
		assertFalse(BattleNext_EntryId.isValidPathSegment(id));
		assertEquals("fleet", BattleNext_EntryId.trimWhitespace(whitespace + "fleet" + whitespace));
	}

	@ParameterizedTest
	@ValueSource(strings = {" ", "\t", "\n", "\u00a0", "\u0085", "\u2007", "\u3000"})
	void fleetStoreRejectsIdsContainingUnicodeWhitespaceBeforePersistence(String whitespace) throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		String id = "fleet" + whitespace + "id";
		BattleNext_FleetModel fleet = new BattleNext_FleetModel(id, "Fleet", List.of(), List.of(), List.of());
		assertThrows(IllegalArgumentException.class, () -> store.save(fleet));
		assertFalse(Files.exists(directory.resolve("tournament")));
	}
}
