package data.scripts.report;

import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_MemberReportTest {

	@Test
	void sourceBucketsAccumulateFourComponentsAndTotalsExcludeEmp() throws Exception {
		BattleNext_MemberReport member = new BattleNext_MemberReport("local_wolf_CS", "wolf", "Wolf");
		member.addDamageDealt("weapon", "lightmg", 10d, 20d, 30d, 40d);
		member.addDamageDealt("weapon", "lightmg", 5d, 6d, 7d, 8d);
		member.addDamageDealt("fighter_wing", "talon_wing", 3d, 4d, 5d, 6d);
		member.addDamageTaken("weapon", "heavyblaster", 1d, 2d, 3d, 4d);
		member.setFinalState(0.25f, 0.7f, "retreated");

		assertEquals(2, member.getDealtBySource().size());
		assertEquals(90d, member.getDamageDealt());
		assertEquals(30d, member.getArmorDamageDealt());
		assertEquals(54d, member.getEmpDamageDealt());
		assertEquals(6d, member.getDamageTaken());
		BattleNext_MemberReport loaded = BattleNext_MemberReport.fromJson(member.toJson());
		assertEquals("local_wolf_CS", loaded.getVariantId());
		assertEquals("wolf", loaded.getHullId());
		assertEquals(90d, loaded.getDamageDealt());
		assertEquals(54d, loaded.getEmpDamageDealt());
		assertEquals(6d, loaded.getDamageTaken());
		assertEquals(4d, loaded.getEmpDamageTaken());
		assertEquals("Wolf", loaded.getName());
		assertEquals(0.25f, loaded.getFinalHullFraction());
		assertEquals(0.7f, loaded.getFinalCr());
		assertEquals("retreated", loaded.getStatus());
		assertEquals(2, loaded.getDealtBySource().size());
		assertEquals("lightmg", loaded.getDealtBySource().get(0).getId());
		assertEquals("weapon", loaded.getDealtBySource().get(0).getKind());
		assertEquals(15d, loaded.getDealtBySource().get(0).getHull());
		assertEquals(26d, loaded.getDealtBySource().get(0).getArmor());
		assertEquals(37d, loaded.getDealtBySource().get(0).getShields());
		assertEquals(48d, loaded.getDealtBySource().get(0).getEmp());
		assertEquals("fighter_wing", loaded.getDealtBySource().get(1).getKind());
		assertEquals("talon_wing", loaded.getDealtBySource().get(1).getId());
		assertEquals(12d, loaded.getDealtBySource().get(1).getTotal());
		assertEquals("heavyblaster", loaded.getTakenBySource().get(0).getId());
		assertEquals(member.toJson().toString(), loaded.toJson().toString());
	}

	@Test
	void malformedAndConflictingDamageSourcesRetainRawStatisticsAndPublishHealthySources() throws Exception {
		var member = new BattleNext_MemberReport("wolf_CS", "wolf", "Wolf");
		member.addDamageDealt("weapon", "laser", 1, 2, 3, 4);
		JSONObject laser = member.toJson().getJSONArray("dealtBySource").getJSONObject(0);
		JSONObject healthy = new BattleNext_DamageSourceReport("weapon", "healthy").toJson();
		JSONObject raw = member.toJson().put("dealtBySource", new JSONArray().put(laser).put("malformed").put(laser).put(healthy));
		var loaded = BattleNext_MemberReport.fromJson(raw);
		assertEquals(List.of("healthy"), loaded.getDealtBySource().stream().map(BattleNext_DamageSourceReport::getId).toList());
		assertEquals(3, loaded.getLoadErrors().size());
		assertEquals(raw.toString(), loaded.toJson().toString());
		loaded.toJson().getJSONArray("dealtBySource").put("external mutation");
		assertEquals(raw.toString(), loaded.toJson().toString());
	}

}
