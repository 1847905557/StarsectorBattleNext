package data.scripts.fleet;

import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.test.fixture.BattleNext_TestGameFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_FleetVariantFormatterTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@MethodSource("prefixProblems")
	void prefixValidationReportsTheExistingIdReasonBeforeResolvingSources(String prefix, String reason) {
		var result = BattleNext_FleetVariantFormatter.preview(fleet(List.of("source")), prefix,
				id -> { throw new AssertionError("Invalid prefixes must finish before source resolution"); });
		assertNull(result.candidate());
		assertEquals(1, result.errors().size());
		assertTrue(result.errors().get(0).contains(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_" + reason).split("%", 2)[0]), result.errors().toString());
	}

	private static Stream<Arguments> prefixProblems() {
		return Stream.of(Arguments.of("", "error_required"), Arguments.of(" \u3000 ", "error_required"),
				Arguments.of("123", "error_id_numeric"), Arguments.of("bad id", "error_id_whitespace"),
				Arguments.of("bad/id", "error_id_character"), Arguments.of("bad\u0001id", "error_id_control"),
				Arguments.of("\u0001ctb", "error_id_control"), Arguments.of("ctb\u0001", "error_id_control"),
				Arguments.of("bad.", "error_id_trailing_dot"), Arguments.of("CON", "error_id_reserved"),
				Arguments.of("_reports", "error_id_reserved"));
	}

	@Test
	void tenThousandMembersShareOneMappingAndHullCountersAvoidRetainedNames() throws Exception {

		hull("wolf", "Wolf", List.of());
		hull("lasher", "Lasher", List.of());
		List<String> ids = new ArrayList<>(Collections.nCopies(10000, "wolf_999"));
		ids.addAll(List.of("other_wolf", "lasher_old", "ctb_custom", "CTB_old", "ctb_bad?"));
		var fleet = fleet(ids);
		for (String id : List.of("wolf_999", "other_wolf", "ctb_custom", "CTB_old", "ctb_bad?", "ctb_wolf_1", "CTB_WOLF_2")) {
			fleet.putVariant(builder(id, "wolf"));
		}
		fleet.putVariant(builder("lasher_old", "lasher"));
		var result = BattleNext_FleetVariantFormatter.preview(fleet, " \u3000ctb\t", id -> null);
		assertTrue(result.errors().isEmpty(), result.errors().toString());
		assertEquals(4, result.renamedIds());
		assertEquals(10003, result.affectedMembers());
		assertEquals("ctb_wolf_3", result.mapping().get("wolf_999"));
		assertEquals("ctb_wolf_4", result.mapping().get("other_wolf"));
		assertEquals("ctb_lasher_1", result.mapping().get("lasher_old"));
		assertEquals("ctb_wolf_5", result.mapping().get("CTB_old"));
		assertTrue(result.candidate().getMembers().subList(0, 10000).stream().allMatch(member -> member.getVariantId().equals("ctb_wolf_3")));
		assertEquals("ctb_custom", result.candidate().getMembers().get(10002).getVariantId());
		assertEquals("ctb_bad?", result.candidate().getMembers().get(10004).getVariantId());
		assertSame(fleet.getVariant("CTB_WOLF_2"), result.candidate().getVariant("CTB_WOLF_2"));
		assertEquals("wolf_999", fleet.getMembers().get(0).getVariantId());
		assertEquals("wolf_999", fleet.getVariant("wolf_999").toJson().getString("variantId"));
		var repeated = BattleNext_FleetVariantFormatter.preview(result.candidate(), "ctb", id -> null);
		assertTrue(repeated.errors().isEmpty());
		assertEquals(0, repeated.renamedIds());
		assertEquals(0, repeated.affectedMembers());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void recursiveModulesShareMappingsWithMembersAndKeepEachOccurrencesConfiguration(boolean array) throws Exception {

		hull("carrier", "Carrier", List.of("a", "b"));
		hull("wolf", "Wolf", List.of("nested"));
		hull("lasher", "Lasher", List.of());
		JSONObject shared = builder("shared", "wolf").toJson().put("fluxVents", 7)
				.put("modules", new JSONObject().put("nested", "external"));
		JSONObject other = builder("shared", "wolf").toJson().put("fluxVents", 19);
		Object modules = array ? new JSONArray().put(new JSONObject().put("a", shared)).put(new JSONObject().put("b", other))
				: new JSONObject().put("b", other).put("a", shared);
		var root = BattleNext_VariantBuilder.fromJson(builder("ctb_carrier_custom", "carrier").toJson().put("modules", modules), "");
		var fleet = fleet(List.of("ctb_carrier_custom", "shared"));
		fleet.putVariant(root);
		fleet.putVariant(BattleNext_VariantBuilder.fromJson(shared, ""));
		fleet.putVariant(builder("unreferenced", "unavailable_hull"));
		var result = BattleNext_FleetVariantFormatter.preview(fleet, "ctb", id -> id.equals("external") ? builder("external", "lasher") : null);
		assertTrue(result.errors().isEmpty(), result.errors().toString());
		assertEquals(Map.of("ctb_carrier_custom", "ctb_carrier_custom", "shared", "ctb_wolf_1", "external", "ctb_lasher_1"), result.mapping());
		assertEquals(2, result.renamedIds());
		assertEquals(2, result.affectedMembers());
		JSONObject renamed = result.candidate().getVariant("ctb_carrier_custom").toJson();
		JSONObject a = array ? renamed.getJSONArray("modules").getJSONObject(0).getJSONObject("a") : renamed.getJSONObject("modules").getJSONObject("a");
		JSONObject b = array ? renamed.getJSONArray("modules").getJSONObject(1).getJSONObject("b") : renamed.getJSONObject("modules").getJSONObject("b");
		assertEquals("ctb_wolf_1", a.getString("variantId"));
		assertEquals("ctb_wolf_1", b.getString("variantId"));
		assertEquals(7, a.getInt("fluxVents"));
		assertEquals(19, b.getInt("fluxVents"));
		assertEquals("ctb_lasher_1", a.getJSONObject("modules").getJSONObject("nested").getString("variantId"));
		assertEquals("ctb_wolf_1", result.candidate().getMembers().get(1).getVariantId());
		assertNull(result.candidate().getVariant("shared"));
		assertSame(fleet.getVariant("unreferenced"), result.candidate().getVariant("unreferenced"));
		assertEquals("shared", fleet.getVariant("shared").toJson().getString("variantId"));
	}

	@Test
	void previewAggregatesModuleFailuresInSlotOrderAndRejectsTheCompleteCandidate() throws Exception {

		hull("wolf", "Wolf", List.of());
		hull("lasher", "Lasher", List.of());
		var modules = new JSONObject().put("a_missing", "missing")
				.put("b_cycle", "parent")
				.put("c_conflict", builder("parent", "lasher").toJson())
				.put("d_hull", builder("child", "missing_hull").toJson())
				.put("e_structure", 42);
		var fleet = fleet(List.of("parent", "missing_root"));
		fleet.putVariant(BattleNext_VariantBuilder.fromJson(builder("parent", "wolf").toJson().put("modules", modules), ""));
		String original = fleet.getVariant("parent").toJson().toString();
		var result = BattleNext_FleetVariantFormatter.preview(fleet, "ctb", id -> null);
		assertNull(result.candidate());
		assertEquals(6, result.errors().size(), result.errors().toString());
		for (int index = 0; index < 5; index++) {
			assertTrue(result.errors().get(index).contains(List.of("a_missing", "b_cycle", "c_conflict", "d_hull", "e_structure").get(index)));
		}
		assertTrue(result.errors().get(5).contains("Member 2"));
		assertTrue(result.errors().get(1).contains("parent -> parent"));
		assertTrue(result.errors().get(2).contains("both hull wolf and hull lasher"));
		assertEquals(original, fleet.getVariant("parent").toJson().toString());
		assertEquals(List.of("parent", "missing_root"), fleet.getMembers().stream().map(BattleNext_MemberModel::getVariantId).toList());
	}

	@Test
	void generatedIdsAndFinalCaseInsensitiveFilenamesAreCheckedBeforeApplication() throws Exception {

		hull("bad/hull", "Bad hull", List.of());
		var fleet = fleet(List.of("source"));
		fleet.putVariant(builder("source", "bad/hull"));
		var invalid = BattleNext_FleetVariantFormatter.preview(fleet, "ctb", id -> null);
		assertNull(invalid.candidate());
		assertEquals(1, invalid.errors().size());
		assertTrue(invalid.errors().get(0).contains("ctb_bad/hull_1"));
		hull("wolf", "Wolf", List.of());
		fleet.setVariants(List.of(builder("source", "wolf"), builder("ctb_keep", "wolf"), builder("CTB_KEEP", "wolf")));
		var collision = BattleNext_FleetVariantFormatter.preview(fleet, "ctb", id -> null);
		assertNull(collision.candidate());
		assertEquals(1, collision.errors().size());
		assertTrue(collision.errors().get(0).contains("CTB_KEEP"));
		assertEquals("source", fleet.getMembers().get(0).getVariantId());
	}

	private BattleNext_FleetModel fleet(List<String> ids) {
		return new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), ids.stream()
				.map(id -> new BattleNext_MemberModel(id, id, "", List.of(), "", "", List.of())).toList());
	}

	private BattleNext_VariantBuilder builder(String id, String hull) throws JSONException {
		return BattleNext_VariantBuilder.fromJson(new JSONObject().put("variantId", id).put("hullId", hull), id);
	}
}
