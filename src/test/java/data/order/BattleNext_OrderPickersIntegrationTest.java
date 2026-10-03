package data.order;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import data.order.picker.ConcentratePicker;
import data.order.picker.NearestPicker;
import data.order.picker.RandomPicker;
import data.order.picker.SpreadPicker;
import data.test.fixture.BattleNext_TestOrderFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_OrderPickersIntegrationTest extends BattleNext_TestOrderFixture {

	@Test
	void nearestAndRandomUsePassedEngineAndStableCandidateOrder() {
		ShipState source = world.ship(0, 0f);
		ShipState first = world.ship(1, 100f);
		ShipState second = world.ship(1, -100f);
		ShipState far = world.ship(1, 200f);
		Global.setCombatEngine(null);
		List<CombatEntityAPI> candidates = new ArrayList<>(List.of(first.getApi(), second.getApi(), far.getApi()));
		assertSame(BattleNext_OrderUtils.toTarget(first.getApi(), world.getEngine()),
				new NearestPicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine()));
		for (int index = 0; index < 100; index++) {
			var selected = new RandomPicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine());
			assertTrue(candidates.contains(BattleNext_OrderUtils.toEntity(selected)));
		}
	}

	@Test
	void spreadSelectsUnusedTargetsAndKeepsItsOwnExclusiveTarget() {
		ShipState source = world.ship(0, 0f);
		ShipState ally = world.ship(0, 10f);
		ShipState near = world.ship(1, 100f);
		ShipState far = world.ship(1, 200f);
		var side = world.side(0, false);
		var nearTarget = BattleNext_OrderUtils.toTarget(near.getApi(), world.getEngine());
		var farTarget = BattleNext_OrderUtils.toTarget(far.getApi(), world.getEngine());
		var nearTask = side.assignment(CombatAssignmentType.ENGAGE, nearTarget);
		side.give(ally, nearTask);
		List<CombatEntityAPI> candidates = new ArrayList<>(List.of(near.getApi(), far.getApi()));
		assertSame(farTarget, new SpreadPicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine()));
		var farTask = side.assignment(CombatAssignmentType.ENGAGE, farTarget);
		side.give(source, farTask);
		side.getTasks().orderSearchAndDestroy((DeployedFleetMemberAPI) BattleNext_OrderUtils.toTarget(ally.getApi(), world.getEngine()), false);
		assertSame(farTarget, new SpreadPicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine()));
		side.give(ally, farTask);
		assertSame(nearTarget, new SpreadPicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine()));
	}

	@Test
	void concentrateCountsLiveSameTypeParticipantsAndIgnoresEmptyTasks() {
		ShipState source = world.ship(0, 0f);
		ShipState ally = world.ship(0, 10f);
		ShipState near = world.ship(1, 100f);
		ShipState far = world.ship(1, 200f);
		var side = world.side(0, false);
		var nearTarget = BattleNext_OrderUtils.toTarget(near.getApi(), world.getEngine());
		var farTarget = BattleNext_OrderUtils.toTarget(far.getApi(), world.getEngine());
		side.assignment(CombatAssignmentType.ENGAGE, nearTarget);
		var farTask = side.assignment(CombatAssignmentType.ENGAGE, farTarget);
		side.give(ally, farTask);
		List<CombatEntityAPI> candidates = new ArrayList<>(List.of(near.getApi(), far.getApi()));
		assertSame(farTarget, new ConcentratePicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine()));
		assertSame(nearTarget, new ConcentratePicker().pick(source.getApi(), candidates, CombatAssignmentType.HARASS, world.getEngine()));
		ally.setAlive(false);
		assertSame(nearTarget, new ConcentratePicker().pick(source.getApi(), candidates, CombatAssignmentType.ENGAGE, world.getEngine()));
	}

	@Test
	void starterTargetsIncludeNeutralObjectivesAndUseCurrentDeployment() {
		ShipState source = world.ship(0, 0f);
		ShipState ally = world.ship(0, 10f);
		ShipState enemy = world.ship(1, 20f);
		var owned = world.objective(0, 100f);
		var hostile = world.objective(1, 200f);
		var neutral = world.objective(100, 300f);
		Global.setCombatEngine(null);
		assertEquals(List.of(hostile, neutral), BattleNext_OrderUtils.enemyObjectives(source.getApi(), world.getEngine()));
		assertEquals(List.of(owned), BattleNext_OrderUtils.allyObjectives(source.getApi(), world.getEngine()));
		assertEquals(List.of(ally.getApi()), BattleNext_OrderUtils.allies(source.getApi(), world.getEngine()));
		assertEquals(List.of(enemy.getApi()), BattleNext_OrderUtils.enemies(source.getApi(), world.getEngine()));
		world.leave(enemy);
		assertTrue(BattleNext_OrderUtils.enemies(source.getApi(), world.getEngine()).isEmpty());
	}
}
