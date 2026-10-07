package data.missions.BattleNext_pve;

import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.missions.BattleNext_MissionBattleMap;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.test.fixture.BattleNext_TestMissionFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

class MissionDefinitionTest extends BattleNext_TestMissionFixture {

	@Test
	void pvpAndPveReadTheSameSharedKeyboardInput() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		MissionDefinitionPlugin pvp = (MissionDefinitionPlugin) Class.forName("data.missions.BattleNext_pvp.MissionDefinition")
				.getDeclaredConstructor().newInstance();
		keys.add(Keyboard.KEY_RIGHT);
		var pvpNext = new MissionState();
		pvp.defineMission(pvpNext.getApi());
		var pveNext = new MissionState();
		new MissionDefinition().defineMission(pveNext.getApi());
		assertEquals("child_variant", pvpNext.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("hegemony", generatedParameters.get(0).factionId);
		keys.clear();
		keys.add(Keyboard.KEY_LEFT);
		var pvpPrevious = new MissionState();
		pvp.defineMission(pvpPrevious.getApi());
		var pvePrevious = new MissionState();
		new MissionDefinition().defineMission(pvePrevious.getApi());
		assertEquals("parent_variant", pvpPrevious.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("mod_faction", generatedParameters.get(1).factionId);
	}

	@Test
	void randomMapsUseAvailableMapsForQuickBattlesPveAndEachTournamentMission() throws Exception {

		quickBattles("alpha,beta,-1,1.5\n");
		for (int map = 0; map <= BattleNext_MissionBattleMap.LAST_MAP_ID; map++) {
			assertEquals(map, BattleNext_MissionBattleMap.resolve(map));
		}
		for (int sample = 0; sample < 16; sample++) {
			int actual = BattleNext_MissionBattleMap.resolve(-1);
			assertTrue(actual >= 0 && actual <= BattleNext_MissionBattleMap.LAST_MAP_ID);
		}
		List<float[]> maps = IntStream.range(0, BattleNext_MissionBattleMap.MAP_COUNT).mapToObj(map -> {
			MissionState expected = new MissionState();
			BattleNext_MissionBattleMap.apply(expected.getApi(), map, 1.5f);
			return expected.getMap();
		}).toList();
		MissionDefinitionPlugin pvp = (MissionDefinitionPlugin) Class.forName("data.missions.BattleNext_pvp.MissionDefinition")
				.getDeclaredConstructor().newInstance();
		for (MissionDefinitionPlugin definition : List.of(pvp, new MissionDefinition())) {
			MissionState mission = new MissionState();
			definition.defineMission(mission.getApi());
			assertTrue(mission.getBriefing().contains(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), -1, 1.5f)));
			assertTrue(maps.stream().anyMatch(map -> Arrays.equals(map, mission.getMap())));
		}
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.setId("random_cup");
		tournament.setName("Random Cup");
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(UUID.randomUUID().toString(), tournament.getId(), BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("alpha", "beta"));
		started.addGeneratedMatch(new BattleNext_Match("pending", "alpha", "beta", -1, 1.5f));
		BattleNext_TournamentStore tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		tournaments.save(tournament);
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		instances.save(started);
		instances.activate(started.getId());
		for (int creation = 0; creation < 2; creation++) {
			MissionState mission = new MissionState();
			pvp.defineMission(mission.getApi());
			assertTrue(maps.stream().anyMatch(map -> Arrays.equals(map, mission.getMap())));
			assertTrue(mission.getBriefing().contains(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), -1, 1.5f)));
		}
		tournaments.reload();
		assertEquals(-1, instances.get(started.getId()).findMatch("pending").getMap());
	}

	@Test
	void cyclesEachValidRowsSidesWithDuplicatesAndTheirMaps() throws Exception {

		quickBattles("alpha,beta,2,1.5\nalpha,alpha,-1,2\n");
		List<String> variants = List.of("parent_variant", "child_variant", "parent_variant", "parent_variant");
		for (int index = 0; index < variants.size(); index++) {
			var mission = new MissionState();
			new MissionDefinition().defineMission(mission.getApi());
			assertEquals(variants.get(index), mission.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
			assertEquals(List.of(index < 2 ? String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), 2, 1.5f) : String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), -1, 2.0f),
					String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 2, index + 1, variants.size())), mission.getBriefing());
			assertEquals("Standard", mission.getNames().get(FleetSide.PLAYER));
			assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
					"mod_faction", 100, 45), mission.getNames().get(FleetSide.ENEMY));
			for (FleetSide side : FleetSide.values()) {
				assertEquals(1L, mission.getEvents().stream().filter(event -> event.equals("tagline:" + side)).count());
			}
			if (index < 2) {
				assertArrayEquals(new float[]{-9000f, 9000f, -6750f, 6750f}, mission.getMap());
			} else {
				assertTrue(IntStream.range(0, BattleNext_MissionBattleMap.MAP_COUNT).anyMatch(map -> {
					MissionState expected = new MissionState();
					BattleNext_MissionBattleMap.apply(expected.getApi(), map, 2f);
					return Arrays.equals(expected.getMap(), mission.getMap());
				}));
			}
			keys.add(Keyboard.KEY_D);
		}
		var wrapped = new MissionState();
		new MissionDefinition().defineMission(wrapped.getApi());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 2, 1, 4), wrapped.getBriefing().get(1));
		keys.clear();
		keys.add(Keyboard.KEY_A);
		var previous = new MissionState();
		new MissionDefinition().defineMission(previous.getApi());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 2, 4, 4), previous.getBriefing().get(1));
		assertEquals(variants.size() + 2, generatedParameters.size());
	}

	@Test
	void combinesIndependentKeysCancelsOppositesAndClampsBudgets() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		keys.addAll(List.of(Keyboard.KEY_D, Keyboard.KEY_RIGHT, Keyboard.KEY_UP));
		var selected = new MissionState();
		new MissionDefinition().defineMission(selected.getApi());
		assertEquals("child_variant", selected.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("hegemony", generatedParameters.get(0).factionId);
		assertEquals(110f, generatedParameters.get(0).combatPts);
		assertEquals(List.of(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), 0, 1.0f), String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 2, 2, 2, 2)), selected.getBriefing());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
				"hegemony", 110, 45), selected.getNames().get(FleetSide.ENEMY));
		keys.addAll(List.of(Keyboard.KEY_A, Keyboard.KEY_LEFT, Keyboard.KEY_DOWN));
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals("hegemony", generatedParameters.get(1).factionId);
		assertEquals(110f, generatedParameters.get(1).combatPts);
		keys.clear();
		select("expectedFp", 10);
		keys.add(Keyboard.KEY_DOWN);
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals(10f, generatedParameters.get(2).combatPts);
		select("expectedFp", 2000);
		keys.clear();
		keys.add(Keyboard.KEY_UP);
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals(2000f, generatedParameters.get(3).combatPts);
		keys.clear();
		keys.add(Keyboard.KEY_RIGHT);
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals("mod_faction", generatedParameters.get(4).factionId);
	}

	@Test
	void reloadNormalizesSelectionAgainstCurrentRowsAndFactions() throws Exception {
		quickBattles("alpha,beta,2,1\nalpha,alpha,3,1\n");
		select("quickBattleCurrentIndex", 3);
		select("factionIndex", 1);
		new MissionDefinition().defineMission(new MissionState().getApi());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,4,2\n");
		factionSpecs.removeIf(faction -> faction.getId().equals("hegemony"));
		var current = new MissionState();
		new MissionDefinition().defineMission(current.getApi());
		assertEquals("child_variant", current.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals(List.of(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), 4, 2.0f), String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 1, 2, 2)), current.getBriefing());
		assertEquals("mod_faction", generatedParameters.get(1).factionId);
		assertArrayEquals(new float[]{-10000f, 10000f, -12000f, 12000f}, current.getMap());
	}

	@Test
	void successfulBriefingPlacesWarningsBeforeMapAndSelectionLikePvp() throws Exception {
		quickBattles("missing,beta,0,1\nalpha,beta,2,1.5\n");
		var mission = new MissionState();
		new MissionDefinition().defineMission(mission.getApi());
		assertTrue(mission.getBriefing().get(0).contains("missing player/enemy fleet reference"));
		assertEquals(List.of(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_summary"), 2, 1.5f), String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 2, 1, 2)), mission.getBriefing().subList(1, 3));
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
				"mod_faction", 100, 45), mission.getNames().get(FleetSide.ENEMY));
	}

}
