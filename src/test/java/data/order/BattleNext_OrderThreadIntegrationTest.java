package data.order;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderStarter.Action;
import data.order.BattleNext_OrderStarter.TargetKind;
import data.order.suffix.PersistSuffix;
import data.order.suffix.PrioritySuffix;
import data.scripts.order.BattleNext_OrderProcessor;
import data.scripts.order.BattleNext_OrderThread;
import data.test.fixture.BattleNext_TestOrderFixture;
import org.json.JSONObject;
import org.json.JSONArray;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_OrderThreadIntegrationTest extends BattleNext_TestOrderFixture {

	@Test
	void orderedScriptsFallbackAfterEmptyTargetsAndRejectedArbitration() {
		ShipState ship = world.ship(0, 0f);
		ShipState competitor = world.ship(0, 0f);
		ShipState near = world.ship(1, 50f);
		ShipState far = world.ship(1, 300f);
		var field = world.field(ship);
		var nearTarget = BattleNext_OrderUtils.toTarget(near.getApi(), world.getEngine());
		field.apply(competitor.getApi(), Action.ASSIGN, CombatAssignmentType.HARASS, nearTarget, 10f, "competitor");
		var errors = new ArrayList<String>();
		var processor = processor("engage.distance(0).nearest;engage.distance(100).nearest.priority(0);"
				+ "harass.distance(100,400).nearest.priority(2)", errors);
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertSame(BattleNext_OrderUtils.toTarget(far.getApi(), world.getEngine()),
				world.side(0, false).current(ship).getTarget());
		assertSame(nearTarget, world.side(0, false).current(competitor).getTarget());
		assertTrue(errors.isEmpty());
	}

	@Test
	void allFailedInstructionsReleaseThePreviousAutomaticTask() {
		ShipState ship = world.ship(0, 0f);
		ShipState enemy = world.ship(1, 50f);
		var field = world.field(ship);
		var processor = processor("engage.distance(100).nearest", new ArrayList<>());
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertNotNull(world.side(0, false).current(ship));
		enemy.getApi().getLocation().x = 500f;
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertNull(world.side(0, false).current(ship));
		assertTrue(world.side(0, false).getAssignments().isEmpty());
	}

	@Test
	void persistKeepsOnlyTheContinuousSuccessfulAndStillEligibleTarget() {
		ShipState ship = world.ship(0, 0f);
		ShipState first = world.ship(1, 50f);
		ShipState second = world.ship(1, 100f);
		var field = world.field(ship);
		var processor = processor("engage.distance(200).nearest.persist", new ArrayList<>());
		var side = world.side(0, false);
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertSame(BattleNext_OrderUtils.toTarget(first.getApi(), world.getEngine()), side.current(ship).getTarget());
		second.getApi().getLocation().x = 10f;
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertSame(BattleNext_OrderUtils.toTarget(first.getApi(), world.getEngine()), side.current(ship).getTarget());
		first.getApi().getLocation().x = 300f;
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertSame(BattleNext_OrderUtils.toTarget(second.getApi(), world.getEngine()), side.current(ship).getTarget());
		second.getApi().getLocation().x = 300f;
		first.getApi().getLocation().x = 20f;
		field.releaseShip(ship.getApi());
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertSame(BattleNext_OrderUtils.toTarget(first.getApi(), world.getEngine()), side.current(ship).getTarget());
	}

	@Test
	void switchingInstructionEndsPersistAndProcessorsKeepIndependentTargets() {
		ShipState firstShip = world.ship(0, 0f);
		ShipState secondShip = world.ship(0, 1000f);
		ShipState firstTarget = world.ship(1, 50f);
		ShipState secondTarget = world.ship(1, 950f);
		var first = processor("engage.selfHp(50%).nearest.persist;clean.nearest", new ArrayList<>());
		var second = processor("engage.nearest.persist", new ArrayList<>());
		var field = world.field(firstShip);
		first.advance(firstShip.getApi(), world.getEngine(), field);
		second.advance(secondShip.getApi(), world.getEngine(), field);
		var side = world.side(0, false);
		assertSame(BattleNext_OrderUtils.toTarget(firstTarget.getApi(), world.getEngine()), side.current(firstShip).getTarget());
		assertSame(BattleNext_OrderUtils.toTarget(secondTarget.getApi(), world.getEngine()), side.current(secondShip).getTarget());
		firstShip.setHp(800f, 0.8f);
		first.advance(firstShip.getApi(), world.getEngine(), field);
		assertNull(side.current(firstShip));
		firstShip.setHp(500f, 0.5f);
		secondTarget.getApi().getLocation().x = 10f;
		first.advance(firstShip.getApi(), world.getEngine(), field);
		assertSame(BattleNext_OrderUtils.toTarget(secondTarget.getApi(), world.getEngine()), side.current(firstShip).getTarget());
	}

	@Test
	void suffixesBindParametersAndUseOnlyTheConfirmedPreviousTarget() {
		var first = world.objective(1, 100f);
		var second = world.objective(1, 200f);
		List<CombatEntityAPI> candidates = List.of(first, second);
		PersistSuffix persist = new PersistSuffix();
		persist.configure(List.of());
		assertSame(first, persist.selectTarget(second, candidates, first, world.getEngine()));
		assertSame(second, persist.selectTarget(second, candidates, null, world.getEngine()));
		assertSame(second, persist.selectTarget(second, List.of(second), first, world.getEngine()));
		PrioritySuffix priority = new PrioritySuffix();
		priority.configure(List.of("2.5"));
		assertEquals(2.5f, priority.resolvePriority(0f, first, world.getEngine()));
		var parsed = BattleNext_OrderParser.parse("assault.nearest.persist.priority(2.5)");
		assertDoesNotThrow(() -> new BattleNext_OrderThread(parsed.getCommand(), BattleNext_OrderRegistry.getInstance()));
	}

	@Test
	void compilerReportsInvalidStatementsWithOriginalNumbersAndLines() {
		var errors = new ArrayList<String>();
		var processor = processor("engage.missing.nearest;engage.nearest\n"
				+ "assault.targetHp(4000).nearest;engage.nearest.priority(NaN)", errors);
		assertFalse(processor.isEmpty());
		assertEquals(3, errors.size());
		assertTrue(errors.get(0).contains("instruction 1 line 1"));
		assertTrue(errors.get(1).contains("instruction 3 line 2 script targetHp"));
		assertTrue(errors.get(2).contains("instruction 4 line 2 script priority"));
	}

	@Test
	void runtimeFailureReportsOnceAndDisablesOnlyItsOriginalStatement() throws Exception {

		var rows = new JSONArray()
				.put(new JSONObject().put("id", "engage").put("name", "engage").put("type", "starter")
						.put("script", "data.order.starter.EngageStarter").put("desc", "Engage"))
				.put(new JSONObject().put("id", "nearest").put("name", "nearest").put("type", "picker")
						.put("script", "data.order.picker.NearestPicker").put("desc", "Nearest"));
		rows.put(new JSONObject().put("id", "fail").put("name", "fail").put("type", "filter")
				.put("script", "fail_script").put("desc", "Failure"));
		AtomicInteger calls = new AtomicInteger();
		var registry = new BattleNext_OrderRegistry();
		registry.load(rows, name -> name.equals("fail_script") ? new FailingFilter(calls) : instantiate(name));
		ShipState ship = world.ship(0, 0f);
		world.ship(1, 100f);
		var field = world.field(ship);
		var errors = new ArrayList<String>();
		var processor = new BattleNext_OrderProcessor("engage.missing.nearest;\nengage.fail.nearest;engage.nearest",
				"ship A", registry, errors::add);
		for (int index = 0; index < 100; index++) {
			processor.advance(ship.getApi(), world.getEngine(), field);
		}
		assertEquals(1, calls.get());
		assertEquals(2, errors.size());
		assertTrue(errors.get(1).contains("instruction 2 line 2 script fail"));
		assertEquals(CombatAssignmentType.ENGAGE, world.side(0, false).current(ship).getType());
	}

	@Test
	void nativeRefusalCountsAsFailureAndEmptyTasksArePruned() throws Exception {
		ShipState ship = world.ship(0, 0f);
		world.ship(1, 100f);
		var side = world.side(0, false);
		side.setRefuse(true);
		var processor = processor("engage.nearest;clean.nearest", new ArrayList<>());
		var field = world.field(ship);
		processor.advance(ship.getApi(), world.getEngine(), field);
		assertNull(side.current(ship));
		assertTrue(side.getAssignments().isEmpty());
		assertTrue(((Map<?, ?>) state(field, "targets")).isEmpty());
	}

	private BattleNext_OrderProcessor processor(String text, List<String> errors) {
		return new BattleNext_OrderProcessor(text, "ship A", BattleNext_OrderRegistry.getInstance(), errors::add);
	}

	private static class FailingFilter implements BattleNext_OrderFilter {

		private final AtomicInteger calls;

		private FailingFilter(AtomicInteger calls) {
			this.calls = calls;
		}

		@Override
		public TargetKind getTargetKind() {
			return TargetKind.ANY;
		}

		@Override
		public void configure(List<String> params) {
			BattleNext_OrderUtils.requireCount(params, 0, 0);
		}

		@Override
		public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatEngineAPI engine) {
			calls.incrementAndGet();
			throw new IllegalStateException("Runtime failure");
		}
	}
}
