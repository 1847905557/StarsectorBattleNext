package data.model.fleet.io;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_CsvLine;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BattleNext_FleetLegacyPackageIntegrationTest extends BattleNext_TestFleetIoFixture {

	@ParameterizedTest
	@ValueSource(ints = {3, 7})
	void generatedPackageUsesFormalCatalogAssemblyAndBattleSourceEntrypoints(int memberCount) throws Exception {

		String folder = "中文舰队包";
		String number = "41";
		String id = "ab_player" + number;
		String name = "测试,舰队";
		String author = "测试作者";
		List<String> skills = List.of("fleet_skill", "fleet_skill", "support_skill");
		List<String> tags = List.of("full_assault");
		List<BattleNext_MemberModel> inputs = new ArrayList<>();
		for (int index = 0; index < memberCount; index++) {
			inputs.add(new BattleNext_MemberModel(index % 2 == 0 ? "local_parent" : "local_child",
					"舰船," + index, "reckless", List.of("gunnery", "gunnery"), "officer.png", "", List.of("tag")));
		}
		List<String> memberRows = new ArrayList<>();
		for (int index = 0; index < inputs.size(); index++) {
			BattleNext_MemberModel input = inputs.get(index);
			memberRows.add(List.of(Integer.toString(index + 1), input.getVariantId(), input.getPersonality(),
					String.join(",", input.getSkills()), input.getName(), input.getPortrait(), input.getOrders(),
					String.join(",", input.getTags())).stream().map(BattleNext_CsvLine::escape)
					.reduce((left, right) -> left + "," + right).orElseThrow());
		}
		legacy(folder, number, String.join("\n", memberRows) + "\n");
		String metadataPath = "tournament/" + folder + "/player" + number + "_data.csv";
		String membersPath = "tournament/" + folder + "/player" + number + "_fleet.csv";
		write(metadataPath, "name,author,skills,tags\n" + List.of(name, author, String.join(",", skills), String.join(",", tags))
				.stream().map(BattleNext_CsvLine::escape).reduce((left, right) -> left + "," + right).orElseThrow() + "\n");
		hull("middle", "Middle", List.of("nested"));
		Map<String, JSONObject> variants = new LinkedHashMap<>();
		variants.put("local_parent", new JSONObject().put("variantId", "local_parent").put("hullId", "parent")
				.put("fluxVents", 21).put("modules", new JSONArray().put(new JSONObject().put("module", "local_middle"))));
		variants.put("local_middle", new JSONObject().put("variantId", "local_middle").put("hullId", "middle")
				.put("fluxVents", 17).put("modules", new JSONObject().put("nested", "local_child")));
		variants.put("local_child", new JSONObject().put("variantId", "local_child").put("hullId", "child").put("fluxVents", 13));
		List<String> reads = new ArrayList<>(List.of("loadCSV:" + metadataPath, "loadCSV:" + membersPath));
		for (var entry : variants.entrySet()) {
			String path = "tournament/" + folder + "/" + entry.getKey() + ".variant";
			write(path, entry.getValue().toString());
			reads.add("loadJSON:" + path);
		}
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		var fleet = store.get(id);
		assertEquals(List.of(id), store.getAll().stream().map(entry -> entry.getId()).toList());
		assertEquals(name, fleet.getName());
		assertEquals(author, fleet.getAuthor());
		assertEquals(skills, fleet.getSkills());
		assertEquals(tags, fleet.getTags());
		assertEquals(inputs.stream().map(BattleNext_MemberModel::getVariantId).toList(),
				fleet.getMembers().stream().map(BattleNext_MemberModel::getVariantId).toList());
		assertEquals(inputs.stream().map(BattleNext_MemberModel::getName).toList(),
				fleet.getMembers().stream().map(BattleNext_MemberModel::getName).toList());
		assertEquals(variants.keySet(), new HashSet<>(fleet.getVariants().stream().map(entry -> entry.getId()).toList()));
		assertEquals(new HashSet<>(reads), new HashSet<>(dataReads));
		assertTrue(fleet.getLoadErrors().isEmpty());
		var assembled = new BattleNext_FleetBuilder().build(fleet);
		assertTrue(assembled.isComplete(), assembled.getMemberErrors().toString());
		assertEquals(inputs.stream().map(BattleNext_MemberModel::getName).toList(),
				assembled.getMembers().stream().map(FleetMemberAPI::getShipName).toList());
		assertEquals(2f, assembled.getCommander().getStats().getSkillLevel(skills.get(0)));
		for (int index = 0; index < inputs.size(); index++) {
			var member = assembled.getMembers().get(index);
			BattleNext_MemberModel input = inputs.get(index);
			assertEquals(input.getVariantId(), member.getVariant().getHullVariantId());
			assertEquals(input.getPersonality(), member.getCaptain().getPersonalityAPI().getId());
			assertEquals(input.getPortrait(), member.getCaptain().getPortraitSprite());
			assertEquals(2f, member.getCaptain().getStats().getSkillLevel(input.getSkills().get(0)));
			if (input.getVariantId().equals("local_parent")) {
				var middle = member.getVariant().getModuleVariant("module");
				assertEquals("local_middle", middle.getHullVariantId());
				var nested = middle.getModuleVariant("nested");
				assertEquals("local_child", nested.getHullVariantId());
				assertEquals(variants.get("local_child").getInt("fluxVents"), nested.toJSONObject().getInt("fluxVents"));
			}
		}
		standard("alpha", "alpha", "parent_variant");
		standard("beta", "beta", "parent_variant");
		legacy("", "42", "1,child_variant,,,,,,\n");
		List<List<String>> quickBattles = List.of(List.of("alpha", "beta"), List.of("beta", "alpha"),
				List.of(id, "alpha"), List.of("alpha", id));
		write("tournament/quick_battle.csv", "player,enemy,map,size\n" + quickBattles.stream()
				.map(pair -> String.join(",", pair) + ",-1,1").reduce((left, right) -> left + "\n" + right).orElseThrow() + "\n");
		List<List<String>> legacyBattles = List.of(List.of(number, "42"), List.of("42", number));
		write("tournament/PvP_matches.csv", "player,enemy,map,size\n" + legacyBattles.stream()
				.map(pair -> String.join(",", pair) + ",8,1").reduce((left, right) -> left + "\n" + right).orElseThrow() + "\n");
		List<List<String>> expectedBattles = new ArrayList<>(quickBattles);
		expectedBattles.addAll(legacyBattles.stream().map(pair -> pair.stream().map(value -> "ab_player" + value).toList()).toList());
		var provider = new BattleNext_BattleSourceProvider();
		var matches = provider.load();
		assertTrue(provider.getWarnings().isEmpty(), provider.getWarnings().toString());
		assertEquals(expectedBattles, matches.stream().map(match -> List.of(match.getPlayer(), match.getEnemy())).toList());
	}
}
