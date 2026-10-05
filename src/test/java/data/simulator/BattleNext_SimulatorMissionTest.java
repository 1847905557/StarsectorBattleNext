package data.simulator;

import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_EnvironmentPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.test.fixture.BattleNext_TestMissionFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorMissionTest extends BattleNext_TestMissionFixture {

	@Test
	void duplicateRegisteredVariantsProduceIndependentMembersWithCompleteModulesAndDefaultCaptains() {
		registeredVariants.put("parent_variant", selectedVariant.getApi());
		registeredVariants.put("child_variant", selectedVariant.getModules().get("module"));
		MissionState mission = new MissionState();
		BattleNext_SimulationConfig config = new BattleNext_SimulationConfig(BattleNext_SimulationConfig.Strategy.AGGRESSIVE,
				12000f, 9000f, 500f, 3600f, 120f, 32, true, false);
		BattleNext_SimulatorMission.define(mission.getApi(), List.of("parent_variant", "parent_variant"), List.of("child_variant"), config);
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
				() -> BattleNext_SimulatorMission.define(mission.getApi(), List.of("missing_variant"), List.of("other"), BattleNext_SimulationConfig.lab()));
		assertTrue(failure.getMessage().contains("missing_variant"));
		assertTrue(mission.getPlugins().isEmpty());
	}
}
