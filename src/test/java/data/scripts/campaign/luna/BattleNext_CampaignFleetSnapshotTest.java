package data.scripts.campaign.luna;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import data.scripts.fleet.BattleNext_AssembledFleet;
import java.nio.file.Path;
import java.util.stream.Stream;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.ui.editor.BattleNext_FleetEditor;
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

	@Test
	void optionalMemberNamesPersistAndBlankNamesResolveToHullNames() throws Exception {

		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		String id = "fleet";
		List<String> names = List.of("", "   ", "\u3000", "Named ship");
		List<BattleNext_MemberModel> members = new ArrayList<>();
		for (String name : names) {
			members.add(new BattleNext_MemberModel("parent_variant", name, "", List.of(), "", "", List.of()));
		}
		BattleNext_FleetModel source = new BattleNext_FleetModel(id, "Fleet", List.of(), List.of(), members);
		source.putVariant(BattleNext_VariantBuilder.fromVariant(selectedVariant.getApi()));
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(store);
		editor.open(source, false);
		attachEditor(editor, panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		editor.buttonPressed("toggle_member:2");
		assertTrue(editor.save(), editor.getStatusText());
		store.reload();
		BattleNext_FleetModel saved = store.get(id);
		assertEquals(List.of("", "", "\u3000", "Named ship"),
				saved.getMembersReadonly().stream().map(BattleNext_MemberModel::getName).toList());
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(saved);
		assertTrue(assembled.isComplete(), assembled.getMemberErrorsReadonly().toString());
		assertEquals(List.of("Parent", "Parent", "Parent", "Named ship"),
				assembled.getMembersReadonly().stream().map(memberApi -> memberApi.getShipName()).toList());
		editor.close();
	}

	@Test
	void capturesAllMembersAndUsesActiveRefitAndActualOfficers() throws Exception {

		String before = BattleNext_VariantBuilder.fromVariant(activeVariant.getApi()).toJson().toString();
		BattleNext_FleetModel snapshot = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		List<BattleNext_MemberModel> members = snapshot.getMembersReadonly();
		assertEquals(5, members.size());
		assertEquals(List.of("Officer ship", "Player ship", "Core ship", "Civilian mothballed", "Unassigned"),
				members.stream().map(member -> member.getName()).toList());
		assertEquals(2, snapshot.getSkillsReadonly().stream().filter("combat"::equals).count());
		assertTrue(snapshot.getSkillsReadonly().containsAll(List.of("fleet", "industry")));
		assertFalse(snapshot.getSkillsReadonly().contains("unlearned"));
		assertEquals(5, snapshot.getVariantsCopy().size());
		assertEquals(5, members.stream().map(member -> member.getVariantId()).distinct().count());
		JSONObject first = snapshot.getVariantsCopy().get(0).toJson();
		JSONObject second = snapshot.getVariantsCopy().get(1).toJson();
		assertEquals(42, first.getInt("fluxVents"));
		assertEquals(13, second.getInt("fluxVents"));
		assertEquals("ordinary", first.getJSONArray("hullMods").getString(0));
		assertEquals("built", first.getJSONArray("sModdedBuiltIns").getString(0));
		assertEquals("d", first.getJSONArray("suppressedMods").getString(0));
		assertEquals("sm", first.getJSONArray("sMods").getString(0));
		assertEquals("active_dmod", first.getJSONArray("permaMods").getString(1));
		assertEquals("wing", first.getJSONArray("wings").getString(0));
		assertEquals("laser", first.getJSONArray("weaponGroups").getJSONObject(0).getJSONObject("weapons").getString("weapon"));
		assertEquals("officer.png", members.get(0).getPortrait());
		assertEquals("reckless", members.get(0).getPersonality());
		assertEquals(2, members.get(0).getSkillsReadonly().stream().filter("gunnery"::equals).count());
		assertEquals("player.png", members.get(1).getPortrait());
		assertEquals(snapshot.getSkillsReadonly(), members.get(1).getSkillsReadonly());
		assertEquals("core.png", members.get(2).getPortrait());
		assertEquals(List.of("core_skill", "core_skill"), members.get(2).getSkillsReadonly());
		for (int index : List.of(3, 4)) {
			assertEquals("", members.get(index).getPortrait());
			assertEquals("", members.get(index).getPersonality());
			assertTrue(members.get(index).getSkillsReadonly().isEmpty());
		}
		assertEquals(before, BattleNext_VariantBuilder.fromVariant(activeVariant.getApi()).toJson().toString());
		activeVariant.getApi().setNumFluxVents(99);
		officer.getStats().setSkillLevel("gunnery", 0f);
		assertEquals(42, snapshot.getVariantsCopy().get(0).toJson().getInt("fluxVents"));
		assertEquals(2, members.get(0).getSkillsReadonly().stream().filter("gunnery"::equals).count());
		assertEquals("Player", snapshot.getAuthor());
		assertEquals("", snapshot.getDescription());
		assertTrue(snapshot.getTagsReadonly().isEmpty());
	}

	@Test
	void recursivelyRenamesModulesAndReloadsLocalSnapshotsWithEliteSkills() throws Exception {

		ShipHullSpecAPI childHull = hulls.get("child");
		moduleSlots.put(childHull, List.of("nested"));
		ShipHullSpecAPI grandHull = hull("grandchild", "Grandchild", List.of());
		ShipVariantAPI module = activeVariant.getModules().get("module");
		module.setModuleVariant("nested", Global.getSettings().createEmptyVariant("parent_variant", grandHull));
		BattleNext_FleetModel snapshot = BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi());
		JSONObject root = snapshot.getVariantsCopy().get(0).toJson();
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
		try (Stream<Path> files = Files.list(directory.resolve("tournament/" + snapshot.getId()))) {
			assertEquals(5, files.filter(path -> path.toString().endsWith(".variant")).count());
		}
		store.reload();
		BattleNext_FleetModel loaded = store.get(snapshot.getId());
		assertJsonEquals(snapshot.toJson(), loaded.toJson());
		assertEquals(snapshot.getName(), loaded.getName());
		assertEquals("Player", loaded.getAuthor());
		assertJsonEquals(root, loaded.getVariantsCopy().get(0).toJson());
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(loaded);
		assertTrue(assembled.isComplete(), assembled.getMemberErrorsReadonly().toString());
		assertEquals(2f, assembled.getCommander().getStats().getSkillLevel("combat"));
		assertEquals(1f, assembled.getCommander().getStats().getSkillLevel("industry"));
		FleetMemberAPI member = assembled.getMembersReadonly().get(0);
		assertEquals(2f, member.getCaptain().getStats().getSkillLevel("gunnery"));
		assertEquals("officer.png", member.getCaptain().getPortraitSprite());
		assertEquals("reckless", member.getCaptain().getPersonalityAPI().getId());
		assertEquals("Officer ship", member.getShipName());
		assertEquals(7, member.getVariant().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		assertNotNull(member.getVariant().getModuleVariant("module").getModuleVariant("nested"));
	}

	@Test
	void capturesUniqueIdentitiesWithPlayerNameAndCurrentTimeAsFleetMetadata() throws Exception {

		Set<String> fleetIds = new HashSet<>();
		Set<String> variantIds = new HashSet<>();
		List<PersonAPI> commanders = List.of(player, player,
				person("Jane Doe", "player.png", "aggressive", false, true, false, Map.of()),
				person("玩家", "player.png", "aggressive", false, true, false, Map.of()));
		for (PersonAPI commander : commanders) {
			String playerName = commander.getNameString();
			long before = System.currentTimeMillis() / 1000L;
			BattleNext_FleetModel snapshot = BattleNext_CampaignFleetSnapshot.capture(fleet, commander, selected, activeVariant.getApi());
			long after = System.currentTimeMillis() / 1000L;
			assertTrue(fleetIds.add(snapshot.getId()));
			assertEquals(snapshot.getId(), snapshot.getFolderName());
			for (BattleNext_VariantBuilder variant : snapshot.getVariantsCopy()) {
				assertTrue(variantIds.add(variant.getId()));
			}
			assertTrue(snapshot.getName().startsWith(playerName + "-"));
			String time = snapshot.getName().substring(playerName.length() + 1);
			assertTrue(time.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
			long captured = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(time).getTime() / 1000L;
			assertTrue(captured >= before && captured <= after);
			assertEquals(playerName, snapshot.getAuthor());
			assertEquals(snapshot.getName(), snapshot.toJson().getString("name"));
			assertEquals(playerName, snapshot.toJson().getString("author"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"cycle", "missing", "hull"})
	void invalidSnapshotFailsBeforePublishing(String failure) {
		if ("hull".equals(failure)) {
			failBuild = true;
		} else {
			activeVariant.getApi().setModuleVariant("module", "cycle".equals(failure) ? activeVariant.getApi() : null);
		}
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> BattleNext_CampaignFleetSnapshot.capture(fleet, player, selected, activeVariant.getApi()));
		assertEquals(switch (failure) {
			case "cycle" -> "Cyclic variant modules";
			case "missing" -> "Missing module: module";
			default -> "Unknown hull: parent";
		}, exception.getMessage());
		assertFalse(Files.exists(directory.resolve("tournament")));
	}

	@Test
	void personSkillsRoundTripNormalAndEliteLevels() {
		PersonAPI target = person("Target", "", "steady", false, false, false, Map.of());
		BattleNext_PersonSkills.apply(target, BattleNext_PersonSkills.capture(player));
		assertEquals(2f, target.getStats().getSkillLevel("combat"));
		assertEquals(1f, target.getStats().getSkillLevel("fleet"));
		assertEquals(1f, target.getStats().getSkillLevel("industry"));
		assertEquals(0f, target.getStats().getSkillLevel("unlearned"));
	}
}
