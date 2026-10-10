package data.missions.BattleNext_pve;

import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.AdmiralAIPlugin;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_Match;
import data.scripts.plugin.BattleNext_RttPlugin;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.test.fixture.BattleNext_TestMissionFixture;
import data.test.fixture.BattleNext_TestDeploymentFixture.FleetManager;
import com.fs.starfarer.api.combat.MutableStat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.EnumMap;
import java.util.Map;

class MissionReportTest extends BattleNext_TestMissionFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void pvpAndPveRegisterSamePluginsAndApplyCommanderSkills(boolean rtt) throws Exception {

		quickBattles("alpha,beta,2,1.5\n");
		fleetSkills("alpha", List.of("combat", "combat", "fleet"), List.of("full_assault"));
		fleetSkills("beta", List.of("gunnery", "gunnery", "industry"), List.of("default_ai"));
		int deploymentPointLimit = rtt ? 120 : -5;
		BattleNext_Settings.save(false, true, false, rtt, 123f, 1f, 3f, 50, deploymentPointLimit);
		var tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		var tournament = new BattleNext_TournamentModel();
		tournament.setId("active");
		tournament.setName("Active");
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(UUID.randomUUID().toString(), tournament.getId(), BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("alpha", "beta"));
		started.addGeneratedMatch(new BattleNext_Match("match", "alpha", "beta", 2, 1.5f));
		tournaments.save(tournament);
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		instances.save(started);
		instances.activate(started.getId());
		var pvp = new MissionState();
		MissionDefinitionPlugin pvpDefinition = (MissionDefinitionPlugin) Class.forName("data.missions.BattleNext_pvp.MissionDefinition")
				.getDeclaredConstructor().newInstance();
		pvpDefinition.defineMission(pvp.getApi());
		var pve = new MissionState();
		new MissionDefinition().defineMission(pve.getApi());
		assertEquals(pvp.getPlugins().stream().map(Object::getClass).toList(), pve.getPlugins().stream().map(Object::getClass).toList());
		assertEquals(rtt, pve.getPlugins().stream().anyMatch(plugin -> plugin instanceof BattleNext_RttPlugin));
		for (MissionState mission : List.of(pvp, pve)) {
			assertEquals(2f, mission.getCommanders().get(FleetSide.PLAYER).getStats().getSkillLevel("combat"));
			assertEquals(1f, mission.getCommanders().get(FleetSide.PLAYER).getStats().getSkillLevel("fleet"));
			assertTrue(mission.getDefaultAi().get(FleetSide.PLAYER));
			assertTrue(mission.getDefaultAi().get(FleetSide.ENEMY));
			assertEquals(123f, getField(mission.getPlugins().get(2), "timeLimit"));
			for (FleetSide side : FleetSide.values()) {
				assertTrue(mission.getEvents().indexOf("init:" + side) < mission.getEvents().indexOf("member:" + side));
				assertTrue(mission.getEvents().lastIndexOf("member:" + side) < mission.getEvents().indexOf("commander:" + side));
			}
		}
		assertEquals(2f, pvp.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("gunnery"));
		assertEquals(1f, pvp.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("industry"));
		assertEquals(2f, pve.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("combat"));
		assertEquals("mod_faction patrol", getField(pve.getPlugins().get(pve.getPlugins().size() - 1), "enemyName"));
		assertSame(generatedFleets.get(0).getMembers().get(0), pve.getMembers().get(FleetSide.ENEMY).get(0));
		var lab = new MissionState();
		MissionDefinitionPlugin labDefinition = (MissionDefinitionPlugin) Class.forName("data.missions.BattleNext_lab.MissionDefinition")
				.getDeclaredConstructor().newInstance();
		labDefinition.defineMission(lab.getApi());
		BattleNext_Settings.save(false, true, false, rtt, 123f, 1f, 3f, 50, 200);
		for (MissionState mission : List.of(pvp, pve, lab)) {
			Map<FleetSide, Integer> caps = new EnumMap<>(FleetSide.class);
			CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
				case "getContext" -> new BattleCreationContext(null, null, null, null);
				case "setMaxFleetPoints" -> caps.put((FleetSide) args[0], (Integer) args[1]);
				case "getFleetManager" -> proxy(FleetManager.class, (name, values) -> switch (name) {
					case "getMaxFleetPoints" -> new MutableStat(100f);
					case "getAdmiralAI" -> proxy(AdmiralAIPlugin.class, (key, input) -> null);
					case "getTaskManager" -> proxy(CombatTaskManagerAPI.class, (key, input) -> null);
					case "getReservesCopy" -> List.of();
					default -> null;
				});
				default -> null;
			});
			mission.getPlugins().stream().filter(BattleNext_DeploymentPlugin.class::isInstance).findFirst().orElseThrow().init(engine);
			int expected = deploymentPointLimit > 0 ? deploymentPointLimit : 999;
			assertEquals(Map.of(FleetSide.PLAYER, expected, FleetSide.ENEMY, expected), caps);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void pveReportUsesNativeMembersMarkerAndRetentionWithoutAdvancingTournament(int keepCount) throws Exception {

		quickBattles("alpha,beta,0,1\n");
		write(BattleNext_StartedTournamentStore.PATH, "{\"formatVersion\":1,\"activeTournamentId\":\"missing\",\"instances\":[]}");
		var meta = directory.resolve(BattleNext_StartedTournamentStore.PATH);
		byte[] original = Files.readAllBytes(meta);
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, keepCount, 0);
		var mission = new MissionState();
		new MissionDefinition().defineMission(mission.getApi());
		var combat = new CombatState(mission);
		BattleNext_ScorekeeperPlugin scorekeeper = mission.getPlugins().stream()
				.filter(plugin -> plugin instanceof BattleNext_ScorekeeperPlugin).map(plugin -> (BattleNext_ScorekeeperPlugin) plugin).findFirst().orElseThrow();
		BattleNext_BattleResultRecorder recorder = mission.getPlugins().stream()
				.filter(plugin -> plugin instanceof BattleNext_BattleResultRecorder).map(plugin -> (BattleNext_BattleResultRecorder) plugin).findFirst().orElseThrow();
		scorekeeper.init(combat.getApi());
		scorekeeper.advance(0.1f, List.of());
		assertFalse(scorekeeper.isFrozen());
		combat.destroySide(FleetSide.ENEMY, 24f);
		scorekeeper.advance(0.1f, List.of());
		assertTrue(scorekeeper.isFrozen());
		recorder.init(combat.getApi());
		recorder.advance(0.1f, List.of());
		recorder.advance(0.1f, List.of());
		var reports = new BattleNext_BattleReportStore();
		reports.reload();
		assertEquals(keepCount, reports.getAll().size());
		var report = scorekeeper.getFrozenReport();
		assertTrue(report.getName().contains("PvE row 1 (PLAYER), faction mod_faction, budget 100 FP, actual 45 FP"));
		assertTrue(report.getName().startsWith("Standard vs mod_faction patrol - "));
		assertEquals("player", report.getWinner());
		assertEquals(24000L, report.getDurationMillis());
		assertEquals(generatedFleets.get(0).getMembers().stream().map(member -> member.getShipName()).toList(),
				report.getEnemyMembers().stream().map(member -> member.getName()).toList());
		assertTrue(report.getEnemyMembers().stream().allMatch(member -> member.getStatus().equals("destroyed")));
		assertEquals(1, combat.getNotices().size());
		assertArrayEquals(original, Files.readAllBytes(meta));
		assertEquals(keepCount == 0 ? "" : report.getId(), BattleNext_BattleSignals.consumePendingResult());
	}

}
