package data.model.quickbattle;

import data.missions.BattleNext_MissionBattleMap;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel.QuickBattleRowType;
import data.scripts.tools.BattleNext_CsvLine;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

class BattleNext_QuickBattleModelTest {

	@ParameterizedTest
	@MethodSource("supportedMaps")
	void supportedMapsAndQuotedFleetNamesRoundTrip(int map) {
		var row = BattleNext_QuickBattleModel.fromCsvRow("\"team,one\",enemy," + map + ",1.5");
		assertEquals("team,one", row.getPlayer());
		assertEquals("enemy", row.getEnemy());
		assertEquals(map, row.getMap());
		assertEquals(1.5f, row.getMapScale());
		assertEquals(row.toCsvLine(), BattleNext_QuickBattleModel.fromCsvRow(row.toCsvLine()).toCsvLine());
		var restored = BattleNext_QuickBattleModel.fromCsvRow(row.toCsvLine());
		assertEquals("team,one", restored.getPlayer());
		assertEquals("enemy", restored.getEnemy());
		assertEquals(map, restored.getMap());
		assertEquals(1.5f, restored.getMapScale());
	}

	@ParameterizedTest
	@ValueSource(strings = {"a,b,0,0", "a,b,0,NaN", "a,b,x,1", "a,b,1,x", ",b,1,1", "a,b,1", "a,b,1,1,extra"})
	void invalidCsvKeepsThePriorModelAndProducesEditableRawRows(String line) {

		var row = new BattleNext_QuickBattleModel("original", "enemy", 3, 2f);
		String before = row.toCsvLine();
		var failure = assertThrows(IllegalArgumentException.class, () -> row.fromCsvLine(BattleNext_CsvLine.parse(line)));
		var fields = BattleNext_CsvLine.parse(line);
		if (fields.size() != 4) {
			assertEquals("Quick battle row must have 4 columns", failure.getMessage());
		} else if (line.equals("a,b,x,1") || line.equals("a,b,1,x")) {
			assertEquals("Invalid quick battle row numbers", failure.getMessage());
			assertInstanceOf(NumberFormatException.class, failure.getCause());
		} else {
			assertEquals("Invalid quick battle row", failure.getMessage());
		}
		assertEquals(before, row.toCsvLine());
		var raw = BattleNext_QuickBattleModel.parseLine(line, false);
		assertEquals(QuickBattleRowType.RAW, raw.getRowType());
		assertFalse(raw.isEnabled());
		assertEquals("# " + line, raw.toSourceLine());
	}

	@Test
	void headersCommentsAndNumberedCsvCopyRetainTheirDistinctSemantics() {
		assertNull(BattleNext_QuickBattleModel.parseLine("player,enemy,map,size", false));
		assertNull(BattleNext_QuickBattleModel.fromCsvRow("# a,b,1,1"));
		var row = BattleNext_QuickBattleModel.parseLine("# 01,02,3,2", true);
		row.setId("row");
		row.setSourceType(BattleNext_LoadSource.LEGACY);
		row.setSourcePath("PvP.csv");
		var copy = row.copy();
		assertEquals("01", copy.getPlayer());
		assertEquals("02", copy.getEnemy());
		assertEquals(QuickBattleRowType.COMMENT_BATTLE, copy.getRowType());
		assertEquals(3, copy.getMap());
		assertEquals(2f, copy.getMapScale());
		assertEquals(row.toSourceLine(), copy.toSourceLine());
		assertEquals("row", copy.getId());
		assertEquals(BattleNext_LoadSource.LEGACY, copy.getSourceType());
		assertEquals("PvP.csv", copy.getSourcePath());
		assertTrue(copy.usesLegacyIds());
		copy.setPlayer("changed");
		copy.setEnabled(true);
		assertEquals("01", row.getPlayer());
		assertFalse(row.isEnabled());
	}

	private static IntStream supportedMaps() {
		return IntStream.of(BattleNext_MissionBattleMap.RANDOM_MAP_ID, 0, BattleNext_MissionBattleMap.LAST_MAP_ID);
	}

	@ParameterizedTest
	@MethodSource("unsupportedMaps")
	void mapBoundsFollowTheMissionMapContract(int map) {
		String line = "a,b," + map + ",1";
		invalidCsvKeepsThePriorModelAndProducesEditableRawRows(line);
	}

	private static IntStream unsupportedMaps() {
		return IntStream.of(BattleNext_MissionBattleMap.RANDOM_MAP_ID - 1, BattleNext_MissionBattleMap.LAST_MAP_ID + 1);
	}

	@Test
	void rawPromotionRequiresExplicitEnableAndNormalizesCommentPrefixes() {
		var row = BattleNext_QuickBattleModel.parseLine("# note", true);
		row.promoteRaw("01,02,3,1");
		assertEquals(QuickBattleRowType.BATTLE, row.getRowType());
		assertEquals("01", row.getPlayer());
		assertFalse(row.isEnabled());
		row.setEnabled(true);
		assertEquals("01,02,3,1.0", row.toSourceLine());
		row.promoteRaw("### note");
		assertEquals(QuickBattleRowType.RAW, row.getRowType());
		assertEquals("# note", row.toSourceLine());
		row.promoteRaw("");
		assertEquals("#", row.toSourceLine());
		assertFalse(row.isEnabled());
	}
}
