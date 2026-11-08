package data.model.fleet;

import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class BattleNext_FleetModelTest {

	@Test
	void roundTripPreservesMetadataOrderAndEliteSkillMultiplicity() throws Exception {

		BattleNext_MemberModel member = new BattleNext_MemberModel(" variant ", " Ship ", " steady ", List.of("skill", "skill"), "portrait", "order", List.of("tag"));
		List<String> skills = new ArrayList<>(Arrays.asList(" skill ", "skill", "", null));
		List<BattleNext_MemberModel> members = new ArrayList<>(List.of(member));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel(" id ", " folder ", " Name ", skills, List.of(" tag "), members);
		fleet.setAuthor(" Author ");
		fleet.setDescription(" Description ");
		skills.clear();
		members.clear();

		BattleNext_FleetModel loaded = new BattleNext_FleetModel();
		loaded.fromJson(fleet.toJson());
		assertEquals("id", loaded.getId());
		assertEquals("Name", loaded.getName());
		assertEquals("Author", loaded.getAuthor());
		assertEquals("Description", loaded.getDescription());
		assertEquals(List.of("skill", "skill"), loaded.getSkillsReadonly());
		assertEquals(List.of("tag"), loaded.getTagsReadonly());
		assertEquals(member.toJson().toString(), loaded.getMembersReadonly().get(0).toJson().toString());

		BattleNext_MemberModel restoredMember = loaded.getMembersReadonly().get(0);
		assertEquals("variant", restoredMember.getVariantId());
		assertEquals("Ship", restoredMember.getName());
		assertEquals("steady", restoredMember.getPersonality());
		assertEquals(List.of("skill", "skill"), restoredMember.getSkillsReadonly());
		assertEquals("portrait", restoredMember.getPortrait());
		assertEquals("order", restoredMember.getOrders());
		assertEquals(List.of("tag"), restoredMember.getTagsReadonly());
		assertEquals("folder", fleet.getFolderName());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getSkillsReadonly().clear());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getTagsReadonly().clear());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getMembersReadonly().clear());
	}

	@Test
	void repeatedJsonReplacesMembersAndClearsOmittedCollections() throws Exception {
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		JSONObject first = new JSONObject().put("id", "first").put("members", new JSONArray()
				.put(new BattleNext_MemberModel("a", "A", "", List.of(), "", "", List.of()).toJson()));
		fleet.fromJson(first);
		assertEquals("a", fleet.getMembersReadonly().get(0).getVariantId());

		fleet.fromJson(new JSONObject().put("id", "second"));
		assertEquals("second", fleet.getId());
		assertEquals(List.of(), fleet.getMembersReadonly());
		assertEquals(List.of(), fleet.getSkillsReadonly());
	}

	@Test
	void localVariantIndexAndLoadErrorsHaveIndependentOrderedViews() throws Exception {

		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		BattleNext_VariantBuilder first = BattleNext_VariantBuilder.fromJson(new JSONObject().put("variantId", "a"), "a");
		BattleNext_VariantBuilder replacement = BattleNext_VariantBuilder.fromJson(new JSONObject().put("variantId", "a").put("fluxVents", 4), "a");
		fleet.putVariant(first);
		List<BattleNext_VariantBuilder> snapshot = fleet.getVariantsCopy();
		fleet.putVariant(replacement);
		assertSame(first, snapshot.get(0));
		assertSame(replacement, fleet.getVariant("a"));
		assertTrue(fleet.hasLocalVariants());

		fleet.addLoadError("first");
		fleet.addLoadError("second");
		assertEquals(List.of("first", "second"), fleet.getLoadErrorsCopy());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getLoadErrorsCopy().clear());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getVariantsCopy().clear());
		assertFalse(fleet.toJson().has("variants"));
		assertFalse(fleet.toJson().has("loadErrors"));
	}

	@Test
	void nestedMembersAndScalarCollectionsKeepValidItemsAndTheirOriginalMalformedInputs() throws Exception {
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), List.of());
		JSONObject member = new BattleNext_MemberModel("wolf_CS", "Wolf", "", List.of(), "", "", List.of()).toJson();
		member.put("skills", new JSONArray().put("valid").put(new JSONObject().put("invalid", true)).put("valid"));
		JSONObject raw = fleet.toJson().put("skills", new JSONArray().put("command").put(new JSONObject()))
				.put("members", new JSONArray().put(member).put("malformed member"));
		fleet.fromJson(raw);
		assertEquals(List.of("command"), fleet.getSkillsReadonly());
		assertEquals(1, fleet.getMembersReadonly().size());
		assertEquals(List.of("valid", "valid"), fleet.getMembersReadonly().get(0).getSkillsReadonly());
		assertEquals(3, fleet.getLoadErrorsCopy().size());
		assertEquals(raw.toString(), fleet.toJson().toString());

		fleet.fromJson(new BattleNext_FleetModel("complete", "Complete", List.of(), List.of(), List.of()).toJson());
		assertTrue(fleet.getLoadErrorsCopy().isEmpty());
	}
}
