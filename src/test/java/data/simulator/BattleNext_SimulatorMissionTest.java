package data.simulator;

import com.fs.starfarer.api.mission.FleetSide;
import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.order.BattleNext_FleetOrderBehavior;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_EnvironmentPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.test.fixture.BattleNext_TestMissionFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class BattleNext_SimulatorMissionTest extends BattleNext_TestMissionFixture {

	@Test
	void duplicateRegisteredVariantsProduceIndependentMembersWithCompleteModulesAndDefaultCaptains() {
		registeredVariants.put("parent_variant", selectedVariant.getApi());
		registeredVariants.put("child_variant", selectedVariant.getModules().get("module"));
		MissionState mission = new MissionState();
		BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(BattleNext_SimulationConfig.Strategy.AGGRESSIVE,
				12000f, 9000f, 500f, 3600f, 120f, 32, true, false);
		BattleNext_SimulatorMission.define(mission.getApi(), BattleNext_SimulationRequest.fromVariants(
				List.of("parent_variant", "parent_variant"), List.of("child_variant"), config));
		assertArrayEquals(new float[]{-6000f, 6000f, -4500f, 4500f}, mission.getMap());
		assertTrue(mission.getDefaultAi().get(FleetSide.PLAYER));
		assertFalse(mission.getDefaultAi().get(FleetSide.ENEMY));
		var members = mission.getMembers().get(FleetSide.PLAYER);
		assertEquals(2, members.size());
		assertNotSame(members.get(0), members.get(1));
		assertNotSame(members.get(0).getVariant(), members.get(1).getVariant());
		assertEquals(selectedVariant.getApi().getStationModules().keySet(), members.get(0).getVariant().getStationModules().keySet());
		assertEquals(selectedVariant.getApi().getHullSpec().getHullName(), members.get(0).getShipName());
		assertNotNull(members.get(0).getCaptain());
		assertEquals(List.of(BattleNext_DeploymentPlugin.class, BattleNext_EnvironmentPlugin.class, BattleNext_ScorekeeperPlugin.class),
				mission.getPlugins().stream().map(Object::getClass).toList());
	}

	@Test
	void unknownVariantRejectsTheTaskDuringFormalAssembly() {
		MissionState mission = new MissionState();
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> BattleNext_SimulatorMission.define(mission.getApi(), BattleNext_SimulationRequest.fromVariants(
						List.of("missing_variant"), List.of("other"), BattleNext_SimulationConfig.defaults())));
		assertTrue(failure.getMessage().contains("missing_variant"));
		assertTrue(mission.getPlugins().isEmpty());
	}

	@Test
	void matchFreezesCompleteLocalVariantsOfficersCommandersAndSourceBeforeWorkerAssembly() throws Exception {

		quickBattles("alpha,beta,-1,1.5\n");
		BattleNext_FleetStore fleets = new BattleNext_FleetStore();
		fleets.reload();
		BattleNext_FleetModel playerFleet = fleets.get("alpha");
		JSONObject local = BattleNext_VariantBuilder.fromVariant(selectedVariant.getApi()).toJson();
		local.put("variantId", "local_snapshot").put("fluxVents", 41);
		hull("child", "Child", List.of("nested"));
		hull("grand", "Nested module", List.of());
		local.getJSONObject("modules").getJSONObject("module").put("modules", new JSONObject().put("nested",
				new JSONObject().put("variantId", "nested_variant").put("hullId", "grand").put("fluxVents", 11)));
		playerFleet.putVariant(BattleNext_VariantBuilder.fromJson(local, "local_snapshot"));
		playerFleet.setName("Player 中文 50%");
		playerFleet.setSkills(List.of("combat", "combat", "fleet"));
		playerFleet.setTags(List.of("full_assault"));
		BattleNext_MemberModel member = new BattleNext_MemberModel("local_snapshot", "Named ship", "reckless",
				List.of("gunnery", "gunnery", "helmsmanship"), "officer.png", "", List.of("member_tag"));
		playerFleet.setMembers(List.of(member, member));
		fleets.save(playerFleet);
		BattleNext_FleetModel enemyFleet = fleets.get("beta");
		enemyFleet.setName("Enemy fleet");
		enemyFleet.setSkills(List.of("industry"));
		enemyFleet.setTags(List.of("default_ai"));
		fleets.save(enemyFleet);
		BattleNext_BattleSourceProvider sources = new BattleNext_BattleSourceProvider();
		sources.reloadQuickBattleCatalog();
		var entry = sources.getQuickBattleStore().getAll().get(0);
		BattleNext_Match match = BattleNext_Match.fromQuickBattle(entry);
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromMatch(match, sources.getFleetStore(),
				BattleNext_SimulationConfig.quickBattle(123f));
		assertEquals(BattleNext_SimulationRequest.Kind.MATCH, request.kind());
		assertEquals(request, BattleNext_SimulationRequest.fromJson(request.toJson()));
		assertEquals(entry.getSourcePath(), request.source().getString("sourcePath"));
		assertEquals(entry.getSourceType().name(), request.source().getString("sourceType"));
		assertEquals(-1, request.source().getJSONObject("match").getInt("map"));
		int actualMap = request.source().getInt("actualMap");
		assertTrue(actualMap >= 0 && actualMap <= BattleNext_MissionBattleMap.LAST_MAP_ID);
		assertEquals(List.of("local_snapshot", "local_snapshot"), request.player());
		JSONObject frozen = request.source();
		sources.getFleetStore().get("alpha").setName("Changed after submission");
		sources.getFleetStore().get("alpha").setSkills(List.of());
		sources.getFleetStore().get("alpha").setMembers(List.of());
		frozen.put("actualMap", -1);
		registeredVariants.clear();
		MissionState mission = new MissionState();
		BattleNext_ScorekeeperPlugin scorekeeper = BattleNext_SimulatorMission.define(mission.getApi(), request);
		MissionState expectedMap = new MissionState();
		BattleNext_MissionBattleMap.apply(expectedMap.getApi(), actualMap, 1.5f);
		assertArrayEquals(expectedMap.getMap(), mission.getMap());
		assertEquals("Player 中文 50%", mission.getNames().get(FleetSide.PLAYER));
		assertFalse(mission.getDefaultAi().get(FleetSide.PLAYER));
		assertTrue(mission.getDefaultAi().get(FleetSide.ENEMY));
		assertEquals(2f, mission.getCommanders().get(FleetSide.PLAYER).getStats().getSkillLevel("combat"));
		assertEquals(1f, mission.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("industry"));
		var restored = mission.getMembers().get(FleetSide.PLAYER);
		assertEquals(2, restored.size());
		assertNotSame(restored.get(0), restored.get(1));
		assertEquals("Named ship", restored.get(0).getShipName());
		assertEquals("reckless", restored.get(0).getCaptain().getPersonalityAPI().getId());
		assertEquals("officer.png", restored.get(0).getCaptain().getPortraitSprite());
		assertEquals(2f, restored.get(0).getCaptain().getStats().getSkillLevel("gunnery"));
		assertEquals(41, restored.get(0).getVariant().getNumFluxVents());
		JSONObject module = request.source().getJSONObject("player").getJSONObject("variants").getJSONObject("0").getJSONObject("modules").getJSONObject("module");
		assertEquals("child", module.getString("hullId"));
		assertEquals(7, module.getInt("fluxVents"));
		assertEquals(11, module.getJSONObject("modules").getJSONObject("nested").getInt("fluxVents"));
		assertEquals(List.of("wing"), restored.get(0).getVariant().getFittedWings());
		assertEquals(11, restored.get(0).getVariant().getModuleVariant("module").getModuleVariant("nested").getNumFluxVents());
		assertEquals(List.of(BattleNext_DeploymentPlugin.class, BattleNext_EnvironmentPlugin.class,
				BattleNext_ScorekeeperPlugin.class, BattleNext_FleetOrderBehavior.class), mission.getPlugins().stream().map(Object::getClass).toList());
		assertEquals(123f, getField(mission.getPlugins().get(1), "timeLimit"));
		assertSame(scorekeeper, mission.getPlugins().get(2));
		assertEquals(BattleNext_FleetOrderBehavior.FleetOrderMode.FULL_ASSAULT, getField(mission.getPlugins().get(3), "playerMode"));
		CombatState combat = new CombatState(mission);
		scorekeeper.init(combat.getApi());
		scorekeeper.advance(0.1f, List.of());
		var report = scorekeeper.getFrozenReport();
		report.setId(request.id());
		report.setName("Complete local fleet report");
		BattleNext_SimulationResult result = new BattleNext_SimulationResult(request.id(), BattleNext_SimulationResult.Outcome.COMPLETED,
				report.toJson().toString(), 24f, 100000000L, 1440L, 1024L, "");
		var received = BattleNext_SimulationResult.fromJson(result.toJson()).report();
		assertEquals(List.of("parent", "parent"), received.getPlayerMembers().stream().map(value -> value.getHullId()).toList());
		assertEquals("local_snapshot", received.getPlayerMembers().get(0).getVariantId());
		assertEquals("child", received.getEnemyMembers().get(0).getHullId());
		scorekeeper.cleanup();
	}

	@Test
	void tournamentRequestReferencesItsConfigurationAndCarriesTheExecutionConfig() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_FleetStore fleets = new BattleNext_FleetStore();
		fleets.reload();
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		rules.reload();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3, 4);
		List<String> slots = new ArrayList<>(List.of("", "alpha", "beta", ""));
		BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromTournament(configuration, slots, fleets, rules, BattleNext_SimulationConfig.defaults());
		slots.clear();
		assertEquals(BattleNext_SimulationRequest.Kind.TOURNAMENT, request.kind());
		assertEquals("cup", request.source().getString("sourceTournamentId"));
		assertEquals(List.of("", "alpha", "beta", ""), request.tournamentFleetIds());
		assertThrows(UnsupportedOperationException.class, () -> request.tournamentFleetIds().clear());
		assertEquals(request, BattleNext_SimulationRequest.fromJson(request.toJson()));
		configuration.setName("Changed");
		assertEquals("Cup", request.label());
	}

	@Test
	void matchRequestRejectsMissingReferencesAndIncompleteAssemblyBeforeSubmission() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		BattleNext_FleetStore fleets = new BattleNext_FleetStore();
		fleets.reload();
		BattleNext_Match match = new BattleNext_Match("source", "missing", "beta", 0, 1f);
		IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
				() -> BattleNext_SimulationRequest.fromMatch(match, fleets, BattleNext_SimulationConfig.defaults()));
		assertEquals("Simulation fleet reference is unavailable: missing", missing.getMessage());
		fleets.get("alpha").setMembers(List.of(new BattleNext_MemberModel("unavailable_variant", "Ship", "", List.of(), "", "", List.of())));
		match.setSides("alpha", "beta");
		IllegalArgumentException assembly = assertThrows(IllegalArgumentException.class,
				() -> BattleNext_SimulationRequest.fromMatch(match, fleets, BattleNext_SimulationConfig.defaults()));
		assertTrue(assembly.getMessage().contains("alpha"));
		assertTrue(assembly.getMessage().contains("unavailable_variant"));
		assertEquals("unavailable_variant", fleets.get("alpha").getMembers().get(0).getVariantId());
	}
}
