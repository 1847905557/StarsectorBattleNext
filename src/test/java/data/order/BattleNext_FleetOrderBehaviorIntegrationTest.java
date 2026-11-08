package data.order;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import data.scripts.order.BattleNext_OrderField;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.order.BattleNext_FleetOrderBehavior;
import data.scripts.order.BattleNext_FleetOrderBehavior.FleetOrderMode;
import data.test.fixture.BattleNext_TestOrderFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class BattleNext_FleetOrderBehaviorIntegrationTest extends BattleNext_TestOrderFixture {

	@Test
	void tagsUseDefaultAiThenFullAssaultThenImplicitOrders() {
		assertEquals(FleetOrderMode.ORDERS, BattleNext_FleetOrderBehavior.fromTags(null));
		assertEquals(FleetOrderMode.ORDERS, BattleNext_FleetOrderBehavior.fromTags(List.of()));
		assertEquals(FleetOrderMode.ORDERS, BattleNext_FleetOrderBehavior.fromTags(List.of("other")));
		assertEquals(FleetOrderMode.ORDERS, BattleNext_FleetOrderBehavior.fromTags(List.of("orders")));
		assertEquals(FleetOrderMode.FULL_ASSAULT, BattleNext_FleetOrderBehavior.fromTags(List.of("orders", "full_assault")));
		assertEquals(FleetOrderMode.AUTO_COMMAND, BattleNext_FleetOrderBehavior.fromTags(List.of("orders", "default_ai")));
		assertEquals(FleetOrderMode.AUTO_COMMAND,
				BattleNext_FleetOrderBehavior.fromTags(List.of("default_ai", "orders", "full_assault")));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void fullAssaultAppliesToItsFleetEveryTenthSecondAndWaitsWhilePaused(int owner) {

		FleetOrderMode mode = BattleNext_FleetOrderBehavior.fromTags(List.of("full_assault"));
		BattleNext_FleetOrderBehavior plugin = new BattleNext_FleetOrderBehavior(owner == 0 ? mode : FleetOrderMode.ORDERS,
				owner == 1 ? mode : FleetOrderMode.ORDERS);
		plugin.init(world.getEngine());
		plugin.advance(0.05f, List.of());
		assertFalse(world.side(owner, false).getTasks().isFullAssault());
		plugin.advance(0.05f, List.of());
		assertEquals(owner == 0, world.side(0, false).getTasks().isFullAssault());
		assertEquals(owner == 0, world.side(0, true).getTasks().isFullAssault());
		assertEquals(owner == 1, world.side(1, false).getTasks().isFullAssault());

		world.side(owner, false).setFullAssault(false);
		world.side(0, true).setFullAssault(false);
		world.setPaused(true);
		plugin.advance(1f, List.of());
		assertFalse(world.side(owner, false).getTasks().isFullAssault());
		world.setPaused(false);
		plugin.advance(0.05f, List.of());
		assertFalse(world.side(owner, false).getTasks().isFullAssault());
		plugin.advance(0.05f, List.of());
		assertEquals(owner == 0, world.side(0, false).getTasks().isFullAssault());
		assertEquals(owner == 0, world.side(0, true).getTasks().isFullAssault());
		assertEquals(owner == 1, world.side(1, false).getTasks().isFullAssault());
		plugin.cleanup();
	}

	@Test
	void bothSidesKeepTheirAssignmentsAcrossCompleteBatches() throws Exception {

		ShipState player = world.ship(0, 0f);
		ShipState enemy = world.ship(1, 1000f);
		BattleObjectiveAPI enemyPoint = world.objective(1, 100f);
		BattleObjectiveAPI playerPoint = world.objective(0, 900f);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(player), List.of("assault.nearest"), List.of(enemy), List.of("assault.nearest"));
		for (int index = 0; index < 100; index++) {
			plugin.advance(0.16f, List.of());
			assertSame(enemyPoint, world.side(0, false).current(player).getTarget());
			assertSame(playerPoint, world.side(1, false).current(enemy).getTarget());
			assertEquals(1, world.side(0, false).getAssignments().size());
			assertEquals(1, world.side(1, false).getAssignments().size());
		}

		world.setCombatOver(true);
		plugin.advance(0f, List.of());
		assertTrue(world.side(0, false).getAssignments().isEmpty());
		assertTrue(world.side(1, false).getAssignments().isEmpty());
		assertTrue(world.getCustom().isEmpty());
		assertTrue(((List<?>) state(plugin, "orders")).isEmpty());
		assertEquals(List.of(plugin), world.getRemovedPlugins());
	}

	@Test
	void memberDefinitionOrderWinsEqualPriorityRegardlessOfEngineShipOrder() {
		ShipState first = world.ship(0, 0f);
		ShipState second = world.ship(0, 10f);
		world.ship(1, 100f);
		Collections.reverse(world.getEngine().getShips());
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(first, second), List.of("engage.nearest", "harass.nearest;clean.nearest"), List.of(), List.of());
		plugin.advance(0.16f, List.of());
		assertEquals(CombatAssignmentType.ENGAGE, world.side(0, false).current(first).getType());
		assertNull(world.side(0, false).current(second));
		plugin.cleanup();
	}

	@Test
	void onlyRegisteredMembersParticipateInTaskManagerQueries() throws Exception {
		ShipState player = world.ship(0, 0f);
		world.ship(1, 100f);
		world.ship(100, 200f);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(player), List.of("engage.nearest"), List.of(), List.of());
		plugin.advance(0.16f, List.of());
		assertNotNull(world.side(0, false).current(player));
		assertEquals(1, ((Map<?, ?>) state(world, "sides")).size());
		plugin.cleanup();
	}

	@Test
	void pausePreservesAssignmentsAndDefersTheCompleteBatch() {

		ShipState ship = world.ship(0, 0f);
		ShipState target = world.ship(1, 100f);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(ship), List.of("engage.nearest"), List.of(), List.of());
		world.setPaused(true);
		plugin.advance(10f, List.of());
		assertNull(world.side(0, false).current(ship));

		world.setPaused(false);
		plugin.advance(0.16f, List.of());
		AssignmentInfo task = world.side(0, false).current(ship);

		world.setPaused(true);
		target.setAlive(false);
		plugin.advance(10f, List.of());
		assertSame(task, world.side(0, false).current(ship));

		world.setPaused(false);
		plugin.advance(0.16f, List.of());
		assertNull(world.side(0, false).current(ship));
		assertTrue(world.side(0, false).getAssignments().isEmpty());
		plugin.cleanup();
	}

	@Test
	void allyTransitionReleasesOldFieldAndEstablishesNewOwnership() throws Exception {
		ShipState ship = world.ship(0, 0f);
		world.ship(1, 100f);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(ship), List.of("engage.nearest.persist"), List.of(), List.of());
		plugin.advance(0.16f, List.of());
		assertNotNull(world.side(0, false).current(ship));

		ship.setAlly(true);
		plugin.advance(0.16f, List.of());
		assertNotNull(world.side(0, true).current(ship));
		assertTrue(world.side(0, false).getAssignments().isEmpty());
		Object oldField = world.getCustom().get("BattleNext_OrderField_0");
		assertTrue(((Map<?, ?>) state(oldField, "participants")).isEmpty());

		plugin.cleanup();
		assertTrue(world.getCustom().isEmpty());
		assertNull(world.side(0, true).current(ship));
	}

	@ParameterizedTest
	@ValueSource(strings = {"death", "leave", "owner", "expiry"})
	void inactiveMembersReleaseTheirOwnedStateBeforeOtherMembersAdvance(String transition) throws Exception {

		ShipState ship = world.ship(0, 0f);
		world.ship(1, 100f);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(ship), List.of("engage.nearest"), List.of(), List.of());
		plugin.advance(0.16f, List.of());
		BattleNext_OrderField field = world.field(ship);

		switch (transition) {
			case "death" -> ship.setAlive(false);
			case "leave" -> world.leave(ship);
			case "owner" -> ship.setOwner(1);
			case "expiry" -> ship.setExpired(true);
			default -> throw new IllegalArgumentException(transition);
		}
		plugin.advance(0.16f, List.of());
		assertTrue(((Map<?, ?>) state(field, "participants")).isEmpty());
		assertTrue(((Map<?, ?>) state(field, "targets")).isEmpty());
		plugin.cleanup();
	}

	@Test
	void maximumPriorityDepartureLowersProtectionBeforeTheChallengerRuns() {
		ShipState low = world.ship(0, 0f);
		ShipState high = world.ship(0, 10f);
		ShipState challenger = world.ship(0, 20f);
		world.ship(1, 100f);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(low, high, challenger), List.of("engage.nearest.priority(0)",
				"engage.nearest.priority(10)", "harass.nearest.priority(5)"), List.of(), List.of());
		plugin.advance(0.16f, List.of());
		assertNull(world.side(0, false).current(challenger));

		high.setAlive(false);
		plugin.advance(0.16f, List.of());
		assertEquals(CombatAssignmentType.HARASS, world.side(0, false).current(challenger).getType());
		assertNull(world.side(0, false).current(low));
		plugin.cleanup();
	}

	@Test
	void externalSameTypeAssignmentsStayExternalOnBothSides() {
		ShipState player = world.ship(0, 0f);
		ShipState enemy = world.ship(1, 100f);
		BattleObjectiveAPI point = world.objective(100, 50f);
		AssignmentInfo playerTask = world.side(0, false).assignment(CombatAssignmentType.CAPTURE, point);
		AssignmentInfo enemyTask = world.side(1, false).assignment(CombatAssignmentType.CAPTURE, point);
		world.side(0, false).give(player, playerTask);
		world.side(1, false).give(enemy, enemyTask);
		BattleNext_FleetOrderBehavior plugin = plugin(List.of(player), List.of("capture.nearest"), List.of(enemy), List.of("capture.nearest"));
		plugin.advance(0.16f, List.of());
		plugin.cleanup();
		assertSame(playerTask, world.side(0, false).current(player));
		assertSame(enemyTask, world.side(1, false).current(enemy));
	}

	private BattleNext_FleetOrderBehavior plugin(List<ShipState> player, List<String> playerOrders,
			List<ShipState> enemy, List<String> enemyOrders) {
		BattleNext_AssembledFleet playerFleet = assembled(0, player, playerOrders);
		BattleNext_AssembledFleet enemyFleet = assembled(1, enemy, enemyOrders);
		BattleNext_FleetOrderBehavior plugin = new BattleNext_FleetOrderBehavior(BattleNext_FleetOrderBehavior.fromTags(playerFleet.getDefinition().getTagsReadonly()),
				BattleNext_FleetOrderBehavior.fromTags(enemyFleet.getDefinition().getTagsReadonly()), playerFleet, enemyFleet);
		plugin.init(world.getEngine());
		return plugin;
	}
}
