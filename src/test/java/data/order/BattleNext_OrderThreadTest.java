package data.order;

import com.fs.starfarer.api.combat.*;
import data.order.suffix.PersistSuffix;
import data.order.suffix.PrioritySuffix;
import data.scripts.order.BattleNext_OrderProcessor;
import data.scripts.order.BattleNext_OrderThread;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class BattleNext_OrderThreadTest {

	private static List<CombatEntityAPI> candidates;

	@Test
	void orderedScriptsKeepIndependentStateAndFallbackAfterEmptyTargets() throws Exception {

		BattleNext_OrderRegistry registry = registry();
		AssignmentTargetAPI first = objective();
		AssignmentTargetAPI second = objective();
		candidates = new ArrayList<>(List.of((CombatEntityAPI)first, (CombatEntityAPI)second));
		BattleNext_OrderThread empty = thread("engage.empty.nearest", registry);
		thread("engage.cycle.persist.priority(2.5)", registry);
		assertFalse(empty.advance(null, null, null, false));
		PersistSuffix persist = new PersistSuffix();
		String[] noParams = new String[0];
		assertSame(first, persist.selectTarget(first, candidates, false, noParams, null));
		assertSame(first, persist.selectTarget(second, candidates, true, noParams, null));
		assertEquals(2.5f, new PrioritySuffix().resolvePriority(0f, first, new String[]{"2.5"}, null));
		candidates = new ArrayList<>(List.of((CombatEntityAPI)second));
		assertSame(second, persist.selectTarget(second, candidates, true, noParams, null));
	}

	@Test
	void scriptFailureIdentifiesTheRegisteredPart() throws Exception {
		BattleNext_OrderRegistry registry = registry();
		candidates = new ArrayList<>(List.of((CombatEntityAPI)objective()));
		BattleNext_OrderThread broken = thread("engage.fail.nearest", registry);
		BattleNext_OrderThread.OrderScriptExecutionException failure = assertThrows(
				BattleNext_OrderThread.OrderScriptExecutionException.class, () -> broken.advance(null, null, null, false));
		assertEquals("fail", failure.getScriptId());
		assertNotNull(thread("engage.nearest", registry));
	}

	@Test
	void processorReportsOnlyInvalidInstructions() throws Exception {
		BattleNext_OrderRegistry registry = registry();
		List<String> errors = new ArrayList<>();
		BattleNext_OrderProcessor processor = new BattleNext_OrderProcessor(
				"engage.nearest;engage.missing.nearest\nengage.cycle.persist", "ship A", registry, errors::add);
		assertFalse(processor.isEmpty());
		assertEquals(1, errors.size());
		assertTrue(errors.get(0).contains("ship A instruction 2"));
		assertTrue(errors.get(0).contains("missing"));
	}

	private BattleNext_OrderThread thread(String text, BattleNext_OrderRegistry registry) {
		BattleNext_OrderParser.OrderParseResult parsed = BattleNext_OrderParser.parse(text, registry);
		assertTrue(parsed.isValid(), parsed.getError());
		return new BattleNext_OrderThread(parsed.getCommand(), registry);
	}

	private BattleNext_OrderRegistry registry() throws Exception {
		JSONArray rows = new JSONArray();
		rows.put(row("engage", "starter", "starter"));
		rows.put(row("empty", "filter", "empty"));
		rows.put(row("fail", "filter", "fail"));
		rows.put(row("nearest", "picker", "nearest"));
		rows.put(row("cycle", "picker", "cycle"));
		rows.put(row("persist", "suffix", "persist"));
		rows.put(row("priority", "suffix", "priority"));
		BattleNext_OrderRegistry registry = BattleNext_OrderRegistry.getInstance();
		registry.load(rows, name -> switch (name) {
			case "starter" -> new TestStarter();
			case "empty" -> new EmptyFilter();
			case "fail" -> new FailingFilter();
			case "nearest" -> new FirstPicker();
			case "cycle" -> new CyclingPicker();
			case "persist" -> new PersistSuffix();
			case "priority" -> new PrioritySuffix();
			default -> throw new IllegalArgumentException(name);
		});
		return registry;
	}

	private JSONObject row(String id, String type, String script) throws Exception {
		return new JSONObject().put("id", id).put("type", type).put("script", script).put("desc", id);
	}

	private AssignmentTargetAPI objective() {
		return (AssignmentTargetAPI)Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{BattleObjectiveAPI.class}, (proxy, method, args) -> {
					if ("equals".equals(method.getName())) return proxy == args[0];
					if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
					return null;
				});
	}

	public static class TestStarter implements OrderStarter {

		public List<? extends CombatEntityAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
			return candidates;
		}

		public CombatAssignmentType getCombatAssignmentType() {
			return CombatAssignmentType.ENGAGE;
		}
	}

	public static class EmptyFilter implements OrderFilter {

		public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
			targets.clear();
		}
	}

	public static class FailingFilter implements OrderFilter {

		public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
			throw new IllegalStateException("test failure");
		}
	}

	public static class FirstPicker implements OrderPicker {

		public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets,
				CombatAssignmentType type, CombatEngineAPI engine) {
			return (AssignmentTargetAPI)targets.get(0);
		}
	}

	public static class CyclingPicker implements OrderPicker {

		private int next;

		public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets,
				CombatAssignmentType type, CombatEngineAPI engine) {
			return (AssignmentTargetAPI)targets.get(next++ % targets.size());
		}
	}
}
