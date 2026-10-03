package data.order;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import data.order.BattleNext_OrderStarter.Action;
import data.scripts.order.BattleNext_OrderField;
import data.test.fixture.BattleNext_TestOrderFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_OrderFieldIntegrationTest extends BattleNext_TestOrderFixture {

	@Test
	void mergesSameTypeAndRequiresHigherPriorityForDifferentType() {
		ShipState first = world.ship(0, 0f);
		ShipState second = world.ship(0, 10f);
		ShipState third = world.ship(0, 20f);
		var target = world.objective(1, 500f);
		var field = world.field(first);
		var side = world.side(0, false);
		assertTrue(field.apply(first.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, "first"));
		assertTrue(field.apply(second.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 10f, "second"));
		assertSame(side.current(first), side.current(second));
		assertEquals(1, side.getAssignments().size());
		assertEquals(10f, field.getPriorityOnTarget(target));
		assertFalse(field.apply(third.getApi(), Action.ASSIGN, CombatAssignmentType.HARASS, target, 10f, "third"));
		assertTrue(field.apply(third.getApi(), Action.ASSIGN, CombatAssignmentType.HARASS, target, 11f, "third"));
		assertNull(side.current(first));
		assertNull(side.current(second));
		assertEquals(CombatAssignmentType.HARASS, side.current(third).getType());
		field.pruneAssignments();
		assertEquals(1, side.getAssignments().size());
	}

	@Test
	void participantPriorityUpdatesAndDepartureRecomputeTheSharedMaximum() {
		ShipState first = world.ship(0, 0f);
		ShipState second = world.ship(0, 10f);
		var target = world.objective(1, 500f);
		var field = world.field(first);
		field.apply(first.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 2f, "first");
		field.apply(second.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 10f, "second");
		assertEquals(10f, field.getPriorityOnTarget(target));
		field.apply(first.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 20f, "first");
		assertEquals(20f, field.getPriorityOnTarget(target));
		field.apply(first.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 1f, "first");
		assertEquals(10f, field.getPriorityOnTarget(target));
		field.releaseShip(second.getApi());
		assertEquals(1f, field.getPriorityOnTarget(target));
		assertEquals(1, world.side(0, false).getAssignments().size());
		field.releaseShip(first.getApi());
		assertEquals(Float.NEGATIVE_INFINITY, field.getPriorityOnTarget(target));
		assertTrue(world.side(0, false).getAssignments().isEmpty());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void protectsExternalAssignmentsByIdentityOnBothSides(int owner) {
		ShipState automated = world.ship(owner, 0f);
		ShipState manual = world.ship(owner, 10f);
		var target = world.objective(1 - owner, 500f);
		var field = world.field(automated);
		var side = world.side(owner, false);
		field.apply(automated.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, "automatic");
		var external = side.assignment(CombatAssignmentType.ENGAGE, target);
		side.give(automated, external);
		side.give(manual, external);
		assertTrue(field.hasExternalAssignment(automated.getApi()));
		assertFalse(field.apply(automated.getApi(), Action.CLEAN, null, target, 100f, "clean"));
		field.reconcile(List.of(automated.getApi(), manual.getApi()));
		field.releaseShip(automated.getApi());
		assertSame(external, side.current(automated));
		assertSame(external, side.current(manual));
		assertEquals(List.of(external), side.getAssignments());
	}

	@Test
	void externalMembersOfGeneratedTasksSurviveLastAutomaticParticipantRelease() {
		ShipState automated = world.ship(0, 0f);
		ShipState manual = world.ship(0, 10f);
		ShipState challenger = world.ship(0, 20f);
		var target = world.objective(1, 500f);
		var field = world.field(automated);
		var side = world.side(0, false);
		field.apply(automated.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, "automatic");
		var task = side.current(automated);
		side.give(manual, task);
		assertFalse(field.apply(challenger.getApi(), Action.ASSIGN, CombatAssignmentType.HARASS, target, 100f, "challenge"));
		field.releaseShip(automated.getApi());
		assertSame(task, side.current(manual));
		assertEquals(List.of(task), side.getAssignments());
		assertTrue(field.hasExternalAssignment(manual.getApi()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"death", "leave", "expiry", "owner", "ally", "native_remove", "target_remove"})
	void reconciliationReleasesInvalidParticipationAndNativeHistory(String transition) throws Exception {
		ShipState ship = world.ship(0, 0f);
		var target = world.objective(1, 500f);
		var field = world.field(ship);
		var side = world.side(0, false);
		field.apply(ship.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, "command");
		switch (transition) {
			case "death" -> ship.setAlive(false);
			case "leave" -> world.leave(ship);
			case "expiry" -> ship.setExpired(true);
			case "owner" -> ship.setOwner(1);
			case "ally" -> ship.setAlly(true);
			case "native_remove" -> side.getTasks().removeAssignment(side.current(ship));
			case "target_remove" -> world.removeObjective(target);
			default -> throw new IllegalArgumentException(transition);
		}
		field.reconcile(transition.equals("native_remove") || transition.equals("target_remove") ? List.of(ship.getApi()) : List.of());
		assertTrue(((Map<?, ?>) state(field, "participants")).isEmpty());
		assertTrue(((Map<?, ?>) state(field, "targets")).isEmpty());
		assertTrue(side.getAssignments().isEmpty());
		field.cleanup();
		assertTrue(world.getCustom().isEmpty());
	}

	@Test
	void rttTakeoverReleaseResetsOnlyItsOwnedCommandAndAllowsFreshSelection() {
		ShipState automatic = world.ship(0, 0f);
		ShipState manual = world.ship(0, 10f);
		var target = world.objective(1, 500f);
		var field = world.field(automatic);
		var side = world.side(0, false);
		Object command = new Object();
		field.apply(automatic.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, command);
		var external = side.assignment(CombatAssignmentType.DEFEND, world.objective(0, 100f));
		side.give(manual, external);
		assertSame(target, field.getCommandTarget(automatic.getApi(), command));
		field.releaseShip(automatic.getApi());
		field.releaseShip(manual.getApi());
		assertNull(field.getCommandTarget(automatic.getApi(), command));
		assertNull(side.current(automatic));
		assertSame(external, side.current(manual));
		assertTrue(field.apply(automatic.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, command));
		assertSame(target, field.getCommandTarget(automatic.getApi(), command));
	}

	@Test
	void repeatedRetreatUsesItsNativeActionStateAndReleasesItCompletely() throws Exception {
		ShipState ship = world.ship(0, 0f);
		var target = BattleNext_OrderUtils.toTarget(ship.getApi(), world.getEngine());
		var field = world.field(ship);
		var side = world.side(0, false);
		Object command = new Object();
		for (int index = 0; index < 100; index++) {
			assertTrue(field.apply(ship.getApi(), Action.RETREAT, CombatAssignmentType.RETREAT, target, 0f, command));
			field.reconcile(List.of(ship.getApi()));
		}
		assertEquals(1, side.getRetreats());
		assertEquals(1, ((Map<?, ?>) state(field, "retreats")).size());
		assertTrue(((Map<?, ?>) state(field, "targets")).isEmpty());
		assertSame(target, field.getCommandTarget(ship.getApi(), command));
		assertTrue(field.apply(ship.getApi(), Action.CLEAN, null, target, 0f, "clean"));
		assertFalse(ship.getApi().isRetreating());
		assertTrue(side.getAssignments().isEmpty());
		assertTrue(((Map<?, ?>) state(field, "retreats")).isEmpty());
	}

	@Test
	void fullRetreatKeepsTheNativeGlobalActionWhenAutomationIsReleased() {
		ShipState ship = world.ship(0, 0f);
		var field = world.field(ship);
		var side = world.side(0, false);
		field.apply(ship.getApi(), Action.RETREAT, CombatAssignmentType.RETREAT,
				BattleNext_OrderUtils.toTarget(ship.getApi(), world.getEngine()), 0f, "retreat");
		var nativeTask = side.current(ship);
		side.setFullRetreat(true);
		field.releaseShip(ship.getApi());
		assertTrue(ship.getApi().isRetreating());
		assertSame(nativeTask, side.current(ship));
	}

	@Test
	void longRunningCreateRemoveCyclesKeepOnlyLiveOwnership() throws Exception {
		ShipState ship = world.ship(0, 0f);
		var field = world.field(ship);
		var target = world.objective(1, 500f);
		var side = world.side(0, false);
		for (int index = 0; index < 300; index++) {
			field.apply(ship.getApi(), Action.ASSIGN, CombatAssignmentType.ENGAGE, target, 0f, "command");
			if (index % 2 == 0) side.getTasks().removeAssignment(side.current(ship));
			else field.releaseShip(ship.getApi());
			field.reconcile(List.of(ship.getApi()));
			assertTrue(((Map<?, ?>) state(field, "targets")).isEmpty());
			assertTrue(((Map<?, ?>) state(field, "participants")).isEmpty());
			assertTrue(side.getAssignments().isEmpty());
		}
	}
}
