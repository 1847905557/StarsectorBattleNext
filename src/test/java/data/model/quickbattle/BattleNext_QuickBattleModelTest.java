package data.model.quickbattle;

import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel.QuickBattleRowType;
import data.scripts.tools.BattleNext_CsvLine;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleNext_QuickBattleModelTest {

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 11})
	void supportedMapsAndQuotedFleetNamesRoundTrip(int map) {
		var row = BattleNext_QuickBattleModel.fromCsvRow("\"team,one\",enemy," + map + ",1.5");
		assertEquals("team,one", row.getPlayer());
		assertEquals(map, row.getMap());
		assertEquals(1.5f, row.getMapScale());
		assertEquals(row.toCsvLine(), BattleNext_QuickBattleModel.fromCsvRow(row.toCsvLine()).toCsvLine());
	}

	@ParameterizedTest
	@ValueSource(strings = {"a,b,-2,1", "a,b,12,1", "a,b,0,0", "a,b,0,-1", "a,b,0,NaN", "a,b,0,Infinity", "a,b,x,1", "a,b,1,x", ",b,1,1", "a,b,1", "a,b,1,1,extra"})
	void invalidCsvKeepsThePriorModelAndProducesEditableRawRows(String line) {
		var row = new BattleNext_QuickBattleModel("original", "enemy", 3, 2f);
		String before = row.toCsvLine();
		assertThrows(IllegalArgumentException.class, () -> row.fromCsvLine(BattleNext_CsvLine.parse(line)));
		assertEquals(before, row.toCsvLine());
		var raw = BattleNext_QuickBattleModel.parseLine(line, false);
		assertEquals(QuickBattleRowType.RAW, raw.getRowType());
		assertFalse(raw.isEnabled());
		assertTrue(raw.toSourceLine().startsWith("# "));
	}

	@Test
	void headersCommentsAndLegacyCopyRetainTheirDistinctSemantics() {
		assertNull(BattleNext_QuickBattleModel.parseLine("player,enemy,map,size", false));
		assertNull(BattleNext_QuickBattleModel.fromCsvRow("# a,b,1,1"));
		var row = BattleNext_QuickBattleModel.parseLine("# 01,02,3,2", true);
		row.setId("row");
		row.setSourceType(BattleNext_LoadSource.LEGACY);
		row.setSourcePath("PvP.csv");
		var copy = row.copy();
		assertEquals("ab_player01", copy.getPlayer());
		assertEquals("ab_player02", copy.getEnemy());
		assertEquals(QuickBattleRowType.COMMENT_BATTLE, copy.getRowType());
		assertEquals(row.toSourceLine(), copy.toSourceLine());
		assertEquals("row", copy.getId());
		assertEquals(BattleNext_LoadSource.LEGACY, copy.getSourceType());
		assertEquals("PvP.csv", copy.getSourcePath());
		assertTrue(copy.usesLegacyIds());
		copy.setPlayer("changed");
		copy.setEnabled(true);
		assertEquals("ab_player01", row.getPlayer());
		assertFalse(row.isEnabled());
	}

	@Test
	void rawPromotionRequiresExplicitEnableAndNormalizesCommentPrefixes() {
		var row = BattleNext_QuickBattleModel.parseLine("# note", true);
		row.promoteRaw("01,02,3,1");
		assertEquals(QuickBattleRowType.BATTLE, row.getRowType());
		assertEquals("ab_player01", row.getPlayer());
		assertFalse(row.isEnabled());
		row.setEnabled(true);
		assertEquals("ab_player01,ab_player02,3,1.0", row.toSourceLine());
		row.promoteRaw("### note");
		assertEquals(QuickBattleRowType.RAW, row.getRowType());
		assertEquals("# note", row.toSourceLine());
		row.promoteRaw("");
		assertEquals("#", row.toSourceLine());
		assertFalse(row.isEnabled());
	}
}
