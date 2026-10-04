package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import data.test.fixture.BattleNext_TestCampaignFleetFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

class BattleNext_CampaignFleetSnapshotTest extends BattleNext_TestCampaignFleetFixture {

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\u3000", "Named ship"})
	void optionalMemberNamesPersistAndBlankNamesResolveToHullNames(String name) throws Exception {

		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		for (boolean expanded : List.of(false, true)) {
			String id = "fleet_" + expanded;
			BattleNext_MemberModel member = new BattleNext_MemberModel("parent_variant", name, "", List.of(), "", "", List.of());
			BattleNext_FleetModel source = new BattleNext_FleetModel(id, "Fleet", List.of(), List.of(), List.of(member));
			source.putVariant(BattleNext_VariantBuilder.fromVariant(selectedVariant.getApi()));
			BattleNext_FleetEditor editor = new BattleNext_FleetEditor(store);
			editor.open(source, false);
			attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
			if (expanded) editor.buttonPressed("toggle_member:0");
			assertTrue(editor.save(), editor.getStatusText());
			store.reload();
			BattleNext_FleetModel saved = store.get(id);
			assertEquals(member.getName(), saved.getMembers().get(0).getName());
			var assembled = new BattleNext_FleetBuilder().build(saved);
			assertTrue(assembled.isComplete(), assembled.getMemberErrors().toString());
			String expected = name.isBlank() ? selectedVariant.getApi().getHullSpec().getHullName() : name;
			assertEquals(expected, assembled.getMembers().get(0).getShipName());
			editor.close();
		}
	}

	@Test
	void capturesAllMembersAndUsesActiveRefitAndActualCaptains() throws Exception {

		String before = BattleNext_VariantBuilder.fromVariant(activeVariant.getApi()).toJson().toString();
		BattleNext_FleetModel snapshot = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		assertEquals(5, snapshot.getMembers().size());
		assertEquals(List.of("Officer ship", "Player ship", "Core ship", "Civilian mothballed", "Unassigned"),
				snapshot.getMembers().stream().map(member -> member.getName()).toList());
		assertEquals(2, snapshot.getSkills().stream().filter("combat"::equals).count());
		assertTrue(snapshot.getSkills().containsAll(List.of("fleet", "industry")));
		assertFalse(snapshot.getSkills().contains("unlearned"));
		assertEquals(5, snapshot.getVariants().size());
		assertEquals(5, snapshot.getMembers().stream().map(member -> member.getVariantId()).distinct().count());
		JSONObject first = snapshot.getVariants().get(0).toJson();
		JSONObject second = snapshot.getVariants().get(1).toJson();
		assertEquals(42, first.getInt("fluxVents"));
		assertEquals(13, second.getInt("fluxVents"));
		assertEquals("ordinary", first.getJSONArray("hullMods").getString(0));
		assertEquals("built", first.getJSONArray("sModdedBuiltIns").getString(0));
		assertEquals("d", first.getJSONArray("suppressedMods").getString(0));
		assertEquals("sm", first.getJSONArray("sMods").getString(0));
		assertEquals("active_dmod", first.getJSONArray("permaMods").getString(1));
		assertEquals("wing", first.getJSONArray("wings").getString(0));
		assertEquals("laser", first.getJSONArray("weaponGroups").getJSONObject(0).getJSONObject("weapons").getString("weapon"));
		assertEquals("officer.png", snapshot.getMembers().get(0).getPortrait());
		assertEquals("reckless", snapshot.getMembers().get(0).getPersonality());
		assertEquals(2, snapshot.getMembers().get(0).getSkills().stream().filter("gunnery"::equals).count());
		assertEquals("player.png", snapshot.getMembers().get(1).getPortrait());
		assertEquals(snapshot.getSkills(), snapshot.getMembers().get(1).getSkills());
		assertEquals("core.png", snapshot.getMembers().get(2).getPortrait());
		assertEquals(List.of("core_skill", "core_skill"), snapshot.getMembers().get(2).getSkills());
		for (int index : List.of(3, 4)) {
			assertEquals("", snapshot.getMembers().get(index).getPortrait());
			assertEquals("", snapshot.getMembers().get(index).getPersonality());
			assertTrue(snapshot.getMembers().get(index).getSkills().isEmpty());
		}
		assertEquals(before, BattleNext_VariantBuilder.fromVariant(activeVariant.getApi()).toJson().toString());
		activeVariant.getApi().setNumFluxVents(99);
		officer.getStats().setSkillLevel("gunnery", 0f);
		assertEquals(42, snapshot.getVariants().get(0).toJson().getInt("fluxVents"));
		assertEquals(2, snapshot.getMembers().get(0).getSkills().stream().filter("gunnery"::equals).count());
		assertEquals("Player", snapshot.getAuthor());
		assertEquals("", snapshot.getDescription());
		assertTrue(snapshot.getTags().isEmpty());
	}

	@Test
	void recursivelyRenamesModulesAndReloadsLocalSnapshotsWithEliteSkills() throws Exception {

		var childHull = hulls.get("child");
		moduleSlots.put(childHull, List.of("nested"));
		var grandHull = hull("grandchild", "Grandchild", List.of());
		ShipVariantAPI module = activeVariant.getModules().get("module");
		module.setModuleVariant("nested", Global.getSettings().createEmptyVariant("parent_variant", grandHull));
		BattleNext_FleetModel snapshot = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		JSONObject root = snapshot.getVariants().get(0).toJson();
		JSONObject child = root.getJSONObject("modules").getJSONObject("module");
		JSONObject grandchild = child.getJSONObject("modules").getJSONObject("nested");
		assertEquals(3, new HashSet<>(List.of(root.getString("variantId"), child.getString("variantId"),
				grandchild.getString("variantId"))).size());
		assertEquals("child_variant", module.getHullVariantId());
		assertEquals("parent_variant", module.getModuleVariant("nested").getHullVariantId());
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		store.save(snapshot);
		assertTrue(Files.exists(directory.resolve(snapshot.getSourcePath())));
		try (var files = Files.list(directory.resolve("tournament/" + snapshot.getId()))) {
			assertEquals(5, files.filter(path -> path.toString().endsWith(".variant")).count());
		}
		store.reload();
		BattleNext_FleetModel loaded = store.get(snapshot.getId());
		assertJsonEquals(snapshot.toJson(), loaded.toJson());
		assertEquals(snapshot.getName(), loaded.getName());
		assertEquals("Player", loaded.getAuthor());
		assertJsonEquals(root, loaded.getVariants().get(0).toJson());
		var assembled = new BattleNext_FleetBuilder().build(loaded);
		assertTrue(assembled.isComplete(), assembled.getMemberErrors().toString());
		assertEquals(2f, assembled.getCommander().getStats().getSkillLevel("combat"));
		assertEquals(1f, assembled.getCommander().getStats().getSkillLevel("industry"));
		assertEquals(2f, assembled.getMembers().get(0).getCaptain().getStats().getSkillLevel("gunnery"));
		assertEquals("officer.png", assembled.getMembers().get(0).getCaptain().getPortraitSprite());
		assertEquals("reckless", assembled.getMembers().get(0).getCaptain().getPersonalityAPI().getId());
		assertEquals("Officer ship", assembled.getMembers().get(0).getShipName());
		assertEquals(7, assembled.getMembers().get(0).getVariant().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		assertNotNull(assembled.getMembers().get(0).getVariant().getModuleVariant("module").getModuleVariant("nested"));
	}

	@Test
	void separateCapturesCreateUniqueFleetAndVariantIdentities() throws Exception {
		BattleNext_FleetModel first = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		BattleNext_FleetModel second = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		assertNotEquals(first.getId(), second.getId());
		assertEquals(first.getId(), first.getFolderName());
		assertTrue(first.getName().matches("Player-\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
		assertTrue(second.getName().matches("Player-\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
		Set<String> ids = new HashSet<>();
		first.getVariants().forEach(variant -> ids.add(variant.getId()));
		second.getVariants().forEach(variant -> assertTrue(ids.add(variant.getId())));
	}

	@ParameterizedTest
	@ValueSource(strings = {"Player", "Jane Doe", "玩家"})
	void capturesPlayerNameAndCurrentTimeAsFleetMetadata(String playerName) throws Exception {
		player = person(playerName, "player.png", "aggressive", false, true, false, Map.of());
		long before = System.currentTimeMillis() / 1000L;
		BattleNext_FleetModel snapshot = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		long after = System.currentTimeMillis() / 1000L;
		assertTrue(snapshot.getName().startsWith(playerName + "-"));
		String time = snapshot.getName().substring(playerName.length() + 1);
		assertTrue(time.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
		long captured = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(time).getTime() / 1000L;
		assertTrue(captured >= before && captured <= after);
		assertEquals(playerName, snapshot.getAuthor());
		assertEquals(snapshot.getName(), snapshot.toJson().getString("name"));
		assertEquals(playerName, snapshot.toJson().getString("author"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"cycle", "missing", "hull"})
	void invalidSnapshotFailsBeforePublishing(String failure) {
		if ("hull".equals(failure)) {
			failBuild = true;
		} else {
			activeVariant.getApi().setModuleVariant("module", "cycle".equals(failure) ? activeVariant.getApi() : null);
		}
		assertThrows(IllegalArgumentException.class,
				() -> BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi()));
		assertFalse(Files.exists(directory.resolve("tournament")));
	}

	@Test
	void personSkillsRoundTripNormalAndEliteLevels() {
		var target = person("Target", "", "steady", false, false, false, Map.of());
		BattleNext_PersonSkills.apply(target, BattleNext_PersonSkills.capture(player));
		assertEquals(2f, target.getStats().getSkillLevel("combat"));
		assertEquals(1f, target.getStats().getSkillLevel("fleet"));
		assertEquals(1f, target.getStats().getSkillLevel("industry"));
		assertEquals(0f, target.getStats().getSkillLevel("unlearned"));
	}
}
