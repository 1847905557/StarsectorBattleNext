package data.scripts.tools;

import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import data.scripts.tools.BattleNext_EntryId.Problem;
import data.scripts.tools.BattleNext_EntryId.Reason;

import java.nio.file.Files;
import java.util.List;

class BattleNext_EntryIdTest extends BattleNext_TestFleetIoFixture {

	@Test
	void idProblemsIdentifyTheFirstReasonAndOneBasedUnicodePosition() {
		assertEquals(new Problem(Reason.REQUIRED, 0, 0), BattleNext_EntryId.problem(null));
		assertEquals(new Problem(Reason.REQUIRED, 0, 0), BattleNext_EntryId.problem(""));
		assertEquals(new Problem(Reason.NUMERIC, 0, 0), BattleNext_EntryId.problem("123"));
		assertEquals(new Problem(Reason.WHITESPACE, 2, 0x3000), BattleNext_EntryId.problem("舰\u3000队"));
		assertEquals(new Problem(Reason.TRAILING_DOT, 0, 0), BattleNext_EntryId.problem("fleet."));
		for (String reserved : List.of("con", "NUL.txt", "COM9", "LPT1.json", "_rules", "_pinnedVariants")) {
			assertEquals(new Problem(Reason.RESERVED, 0, 0), BattleNext_EntryId.problem(reserved));
			assertFalse(BattleNext_EntryId.isValidPathSegment(reserved));
		}
		for (int character : "<>:\"/\\|?*".codePoints().toArray()) {
			String value = "🚀" + Character.toString(character) + "fleet";
			assertEquals(new Problem(Reason.CHARACTER, 2, character), BattleNext_EntryId.problem(value));
			assertFalse(BattleNext_EntryId.isValid(value));
		}
		for (int character = 0; character < 32; character++) {
			String value = "舰" + Character.toString(character) + "队";
			assertEquals(new Problem(Reason.CONTROL, 2, character), BattleNext_EntryId.problem(value));
			assertFalse(BattleNext_EntryId.isValid(value));
		}
		assertNull(BattleNext_EntryId.problem("舰队_🚀-A.1"));
	}

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
