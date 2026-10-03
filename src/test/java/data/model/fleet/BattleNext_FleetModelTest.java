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

		var member = new BattleNext_MemberModel(" variant ", " Ship ", " steady ", List.of("skill", "skill"), "portrait", "order", List.of("tag"));
		List<String> skills = new ArrayList<>(Arrays.asList(" skill ", "skill", "", null));
		List<BattleNext_MemberModel> members = new ArrayList<>(List.of(member));
		var fleet = new BattleNext_FleetModel(" id ", " folder ", " Name ", skills, List.of(" tag "), members);
		fleet.setAuthor(" Author ");
		fleet.setDescription(" Description ");
		skills.clear();
		members.clear();
		var loaded = new BattleNext_FleetModel();
		loaded.fromJson(fleet.toJson());
		assertEquals("id", loaded.getId());
		assertEquals("Name", loaded.getName());
		assertEquals("Author", loaded.getAuthor());
		assertEquals("Description", loaded.getDescription());
		assertEquals(List.of("skill", "skill"), loaded.getSkills());
		assertEquals(List.of("tag"), loaded.getTags());
		assertEquals(member.toJson().toString(), loaded.getMembers().get(0).toJson().toString());
		assertEquals("folder", fleet.getFolderName());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getSkills().clear());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getTags().clear());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getMembers().clear());
	}

	@Test
	void repeatedJsonReplacesMembersAndFailurePreservesThePublishedModel() throws Exception {
		var fleet = new BattleNext_FleetModel();
		JSONObject first = new JSONObject().put("id", "first").put("members", new JSONArray()
				.put(new BattleNext_MemberModel("a", "A", "", List.of(), "", "", List.of()).toJson()));
		fleet.fromJson(first);
		String original = fleet.toJson().toString();
		JSONObject invalid = new JSONObject(first.toString()).put("id", "invalid").put("formatVersion", 2);
		assertThrows(IllegalArgumentException.class, () -> fleet.fromJson(invalid));
		assertEquals(original, fleet.toJson().toString());
		invalid.put("formatVersion", 1).put("members", new JSONArray().put(new JSONObject().put("formatVersion", 2)));
		assertThrows(IllegalArgumentException.class, () -> fleet.fromJson(invalid));
		assertEquals(original, fleet.toJson().toString());
		fleet.fromJson(new JSONObject().put("id", "second"));
		assertEquals("second", fleet.getId());
		assertEquals(List.of(), fleet.getMembers());
		assertEquals(List.of(), fleet.getSkills());
	}

	@Test
	void localVariantIndexAndLoadErrorsHaveIndependentOrderedViews() throws Exception {
		var fleet = new BattleNext_FleetModel();
		var first = BattleNext_VariantBuilder.fromJson(new JSONObject().put("variantId", "a"), "a");
		var replacement = BattleNext_VariantBuilder.fromJson(new JSONObject().put("variantId", "a").put("fluxVents", 4), "a");
		fleet.putVariant(first);
		List<BattleNext_VariantBuilder> snapshot = fleet.getVariants();
		fleet.putVariant(replacement);
		assertSame(first, snapshot.get(0));
		assertSame(replacement, fleet.getVariant("a"));
		assertTrue(fleet.hasLocalVariants());
		fleet.addLoadError("first");
		fleet.addLoadError("second");
		assertEquals(List.of("first", "second"), fleet.getLoadErrors());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getLoadErrors().clear());
		assertThrows(UnsupportedOperationException.class, () -> fleet.getVariants().clear());
		assertFalse(fleet.toJson().has("variants"));
		assertFalse(fleet.toJson().has("loadErrors"));
	}
}
