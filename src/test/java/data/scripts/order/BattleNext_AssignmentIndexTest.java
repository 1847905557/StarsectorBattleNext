package data.scripts.order;

import data.order.BattleNext_OrderUtils;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import data.order.BattleNext_OrderStarter.Action;
import data.test.fixture.BattleNext_TestOrderFixture;
import data.test.fixture.BattleNext_TestPerformance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import data.scripts.order.BattleNext_FleetOrderBehavior.FleetOrderMode;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_AssignmentIndexTest extends BattleNext_TestOrderFixture {

	@Test
	void retargetedAndRemovedAssignmentsUpdateTheSameRoundSnapshot() {
		var ship = world.ship(0, 0f);
		var side = world.side(0, false);
		var first = world.objective(1, 100f);
		var second = world.objective(1, 200f);
		AtomicReference<AssignmentTargetAPI> target = new AtomicReference<>(first);
		AssignmentInfo assignment = new AssignmentInfo() {
			@Override
			public CombatAssignmentType getType() {
				return CombatAssignmentType.DEFEND;
			}
			@Override
			public AssignmentTargetAPI getTarget() {
				return target.get();
			}
			@Override
			public List<DeployedFleetMemberAPI> getAssignedMembers() {
				return List.of(world.getEngine().getFleetManager(0).getDeployedFleetMember(ship.getApi()));
			}
		};
		side.getAssignments().add(assignment);
		side.give(ship, assignment);
		try (var index = BattleNext_AssignmentIndex.begin(world.getEngine())) {
			assertEquals(1, BattleNext_OrderUtils.assignmentCount(first, null, side.getTasks(), world.getEngine()));
			target.set(second);
			BattleNext_AssignmentIndex.changed(side.getTasks(), ship.getApi());
			assertEquals(0, BattleNext_OrderUtils.assignmentCount(first, null, side.getTasks(), world.getEngine()));
			assertEquals(1, BattleNext_OrderUtils.assignmentCount(second, null, side.getTasks(), world.getEngine()));
			side.getTasks().removeAssignment(assignment);
			BattleNext_AssignmentIndex.removed(side.getTasks(), assignment);
			assertEquals(0, BattleNext_OrderUtils.assignmentCount(second, null, side.getTasks(), world.getEngine()));
			assertTrue(BattleNext_AssignmentIndex.assignments(side.getTasks(), world.getEngine()).isEmpty());
		}
	}

	@Test
	void full128ShipRoundsShareOneListAcrossReconciliationSelectionCreationReleaseAndPruning() throws Exception {
		List<ShipState> ships = new ArrayList<>();
		for (int index = 0; index < 128; index++) {
			ships.add(world.ship(0, index * 100f));
			world.objective(1, index * 100f);
		}
		var side = world.side(0, false);
		var fleet = assembled(0, ships, Collections.nCopies(128, "capture.nearest"));
		var plugin = new BattleNext_FleetOrderBehavior(FleetOrderMode.ORDERS, FleetOrderMode.AUTO_COMMAND, fleet, null);
		plugin.init(world.getEngine());
		int reads = side.getAssignmentReads();
		plugin.advance(0.16f, List.of());
		assertEquals(1, side.getAssignmentReads() - reads);
		assertEquals(128, side.getAssignments().size());
		reads = side.getAssignmentReads();
		var measurements = BattleNext_TestPerformance.measure(20, () -> plugin.advance(0.16f, List.of()));
		assertEquals(20, side.getAssignmentReads() - reads);
		BattleNext_TestPerformance.write("command-round-128", measurements.put("ships", 128).put("targets", 128)
				.put("assignment_list_reads_per_sample", 1));
		plugin.cleanup();
		assertTrue(side.getAssignments().isEmpty());
	}

	@Test
	void counts128TargetsFor128ShipsUsingOneAssignmentListRead() throws Exception {
		var side = world.side(0, false);
		List<ShipState> ships = new ArrayList<>();
		for (int index = 0; index < 128; index++) {
			ShipState ship = world.ship(0, index * 10f);
			ships.add(ship);
			side.give(ship, side.assignment(CombatAssignmentType.DEFEND, world.objective(1, index * 100f)));
		}
		AtomicInteger reads = new AtomicInteger();
		CombatTaskManagerAPI tasks = (CombatTaskManagerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{CombatTaskManagerAPI.class}, (instance, method, args) -> {
					if (method.getName().equals("getAllAssignments")) reads.incrementAndGet();
					try {
						return method.invoke(side.getTasks(), args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				});
		try (BattleNext_AssignmentIndex index = BattleNext_AssignmentIndex.begin(world.getEngine())) {
			for (ShipState ship : ships) {
				for (var assignment : side.getAssignments()) {
					assertEquals(1, BattleNext_OrderUtils.assignmentCount(assignment.getTarget(), null, tasks, world.getEngine()));
					assertEquals(0, BattleNext_OrderUtils.assignmentCount(assignment.getTarget(), CombatAssignmentType.ENGAGE, tasks, world.getEngine()));
				}
			}
			assertEquals(1, reads.get());
		}
		var assignments = List.copyOf(side.getAssignments());
		reads.set(0);
		var measurements = BattleNext_TestPerformance.measure(20, () -> {
			int assigned = 0;
			try (BattleNext_AssignmentIndex index = BattleNext_AssignmentIndex.begin(world.getEngine())) {
				for (ShipState ship : ships) {
					for (var assignment : assignments) {
						assigned += BattleNext_OrderUtils.assignmentCount(assignment.getTarget(), null, tasks, world.getEngine());
						assigned += BattleNext_OrderUtils.assignmentCount(assignment.getTarget(), CombatAssignmentType.ENGAGE, tasks, world.getEngine());
					}
				}
			}
			assertEquals(128 * 128, assigned);
		});
		assertEquals(20, reads.get());
		BattleNext_TestPerformance.write("command-index-128", measurements.put("ships", 128).put("targets", 128)
				.put("queries_per_sample", 128 * 128 * 2).put("assignment_list_reads_per_sample", 1));
	}

	@Test
	void createdReleasedAndReassignedCommandsUpdateTheSameRoundIndex() {
		ShipState ship = world.ship(0, 0f);
		var side = world.side(0, false);
		var first = world.objective(1, 100f);
		var second = world.objective(1, 200f);
		var field = world.field(ship);
		try (BattleNext_AssignmentIndex index = BattleNext_AssignmentIndex.begin(world.getEngine())) {
			assertEquals(0, BattleNext_OrderUtils.assignmentCount(first, null, side.getTasks(), world.getEngine()));
			assertTrue(field.apply(ship.getApi(), Action.ASSIGN, CombatAssignmentType.DEFEND, first, 0f, "first"));
			assertEquals(1, BattleNext_OrderUtils.assignmentCount(first, null, side.getTasks(), world.getEngine()));
			assertTrue(field.apply(ship.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, second, 0f, "second"));
			assertEquals(0, BattleNext_OrderUtils.assignmentCount(first, null, side.getTasks(), world.getEngine()));
			assertEquals(1, BattleNext_OrderUtils.assignmentCount(second, CombatAssignmentType.ENGAGE, side.getTasks(), world.getEngine()));
			field.releaseShip(ship.getApi());
			assertEquals(0, BattleNext_OrderUtils.assignmentCount(second, null, side.getTasks(), world.getEngine()));
		}
	}
}
