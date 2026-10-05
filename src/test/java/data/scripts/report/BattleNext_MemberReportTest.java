package data.scripts.report;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BattleNext_MemberReportTest {

	@Test
	void sourceBucketsAccumulateFourComponentsAndTotalsExcludeEmp() throws Exception {
		BattleNext_MemberReport member = new BattleNext_MemberReport("wolf_CS", "Wolf");
		member.addDamageDealt("weapon", "lightmg", 10d, 20d, 30d, 40d);
		member.addDamageDealt("weapon", "lightmg", 5d, 6d, 7d, 8d);
		member.addDamageDealt("fighter_wing", "talon_wing", 3d, 4d, 5d, 6d);
		member.addDamageTaken("weapon", "heavyblaster", 1d, 2d, 3d, 4d);

		assertEquals(2, member.getDealtBySource().size());
		assertEquals(90d, member.getDamageDealt());
		assertEquals(30d, member.getArmorDamageDealt());
		assertEquals(54d, member.getEmpDamageDealt());
		assertEquals(6d, member.getDamageTaken());
		BattleNext_MemberReport loaded = BattleNext_MemberReport.fromJson(member.toJson());
		assertEquals(member.getDamageDealt(), loaded.getDamageDealt());
		assertEquals(2, loaded.getDealtBySource().size());
		assertEquals("lightmg", loaded.getDealtBySource().get(0).getId());
	}

}
