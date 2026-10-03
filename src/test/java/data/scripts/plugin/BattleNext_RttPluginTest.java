package data.scripts.plugin;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_RttPluginTest {

	@Test
	void inputRequiresFreeCameraAndUncoveredBattle() {
		assertTrue(BattleNext_RttPlugin.acceptsMouseInput(engine(true, false, false, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(false, false, false, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(true, true, false, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(true, false, true, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(true, false, false, true)));
	}

	@Test
	void advanceClearsUiCoveredGestureWhilePreservingSelectedShips() throws ReflectiveOperationException {

		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		ShipAPI ship = commandShip(new AtomicInteger(), new AtomicInteger());
		plugin.init(commandEngine(List.of(ship), true, new AtomicInteger()));
		select(plugin, ship);
		for (String name : List.of("pressing", "dragging", "addSelection")) {
			Field field = BattleNext_RttPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setBoolean(plugin, true);
		}
		plugin.advance(0f, List.of());
		for (String name : List.of("pressing", "dragging", "addSelection")) {
			Field field = BattleNext_RttPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			assertFalse(field.getBoolean(plugin), name);
		}
		Field selected = BattleNext_RttPlugin.class.getDeclaredField("selected");
		selected.setAccessible(true);
		assertEquals(Set.of(ship), selected.get(plugin));
	}

	@Test
	void advancePreservesUncoveredGestureAndSelection() throws ReflectiveOperationException {

		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		ShipAPI ship = commandShip(new AtomicInteger(), new AtomicInteger());
		plugin.init(commandEngine(List.of(ship), false, new AtomicInteger()));
		select(plugin, ship);
		for (String name : List.of("pressing", "dragging", "addSelection")) {
			Field field = BattleNext_RttPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setBoolean(plugin, true);
		}
		plugin.advance(0f, List.of());
		for (String name : List.of("pressing", "dragging", "addSelection")) {
			Field field = BattleNext_RttPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			assertTrue(field.getBoolean(plugin), name);
		}
		Field selected = BattleNext_RttPlugin.class.getDeclaredField("selected");
		selected.setAccessible(true);
		assertEquals(Set.of(ship), selected.get(plugin));
	}

	@Test
	void ventOrdersEverySelectedShipWithoutReplacingAi() throws ReflectiveOperationException {
		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		AtomicInteger firstCommands = new AtomicInteger();
		AtomicInteger secondCommands = new AtomicInteger();
		AtomicInteger aiChanges = new AtomicInteger();
		ShipAPI first = commandShip(firstCommands, aiChanges);
		ShipAPI second = commandShip(secondCommands, aiChanges);
		plugin.init(commandEngine(List.of(first, second), false, new AtomicInteger()));
		select(plugin, first, second);
		AtomicBoolean consumed = new AtomicBoolean();

		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", false, consumed)));

		assertTrue(consumed.get());
		assertEquals(1, firstCommands.get());
		assertEquals(1, secondCommands.get());
		assertEquals(0, aiChanges.get());
	}

	@Test
	void systemKeySkipsShipsWithoutSystemAndUsesMouseEventPosition() throws ReflectiveOperationException {
		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		AtomicInteger commands = new AtomicInteger();
		AtomicInteger aiChanges = new AtomicInteger();
		AtomicInteger coordinateConversions = new AtomicInteger();
		ShipAPI ship = commandShip(commands, aiChanges);
		plugin.init(commandEngine(List.of(ship), false, coordinateConversions));
		select(plugin, ship);
		AtomicBoolean consumed = new AtomicBoolean();

		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_USE_SYSTEM", false, consumed)));

		assertTrue(consumed.get());
		assertEquals(2, coordinateConversions.get());
		assertEquals(0, commands.get());
		assertEquals(0, aiChanges.get());
	}

	@Test
	void shortcutGateAndRepeatLeaveEventsForCoreControls() throws ReflectiveOperationException {
		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		AtomicInteger commands = new AtomicInteger();
		ShipAPI ship = commandShip(commands, new AtomicInteger());
		plugin.init(commandEngine(List.of(ship), true, new AtomicInteger()));
		select(plugin, ship);
		AtomicBoolean covered = new AtomicBoolean();
		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", false, covered)));
		assertFalse(covered.get());
		assertEquals(0, commands.get());

		plugin.init(commandEngine(List.of(ship), false, new AtomicInteger()));
		AtomicBoolean repeat = new AtomicBoolean();
		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", true, repeat)));
		assertFalse(repeat.get());
		assertEquals(0, commands.get());
	}

	@Test
	void cancellationRestoresSavedWrapperAi() {
		AtomicReference<ShipAIPlugin> installed = new AtomicReference<>();
		ShipAIPlugin wrapper = proxy(ShipAIPlugin.class, name -> null);
		ShipAPI ship = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) -> {
					if ("setShipAI".equals(method.getName())) installed.set((ShipAIPlugin) args[0]);
					return null;
				});

		BattleNext_RttPlugin.restoreShipAI(ship, wrapper);

		assertSame(wrapper, installed.get());
	}

	@SuppressWarnings("unchecked")
	private void select(BattleNext_RttPlugin plugin, ShipAPI... ships) throws ReflectiveOperationException {
		Field field = BattleNext_RttPlugin.class.getDeclaredField("selected");
		field.setAccessible(true);
		Set<ShipAPI> selected = (Set<ShipAPI>) field.get(plugin);
		selected.addAll(List.of(ships));
	}

	private ShipAPI commandShip(AtomicInteger commands, AtomicInteger aiChanges) {
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipAPI.class},
				(instance, method, args) -> switch (method.getName()) {
					case "hashCode" -> System.identityHashCode(instance);
					case "equals" -> instance == args[0];
					case "isAlive" -> true;
					case "isExpired", "isFighter", "isDrone", "isStation", "isStationModule" -> false;
					case "getOwner", "getOriginalOwner" -> 0;
					case "giveCommand" -> {
						assertSame(ShipCommand.VENT_FLUX, args[0]);
						commands.incrementAndGet();
						yield null;
					}
					case "setShipAI" -> {
						aiChanges.incrementAndGet();
						yield null;
					}
					default -> null;
				});
	}

	private CombatEngineAPI commandEngine(List<ShipAPI> ships, boolean commandUi, AtomicInteger conversions) {
		ShipAPI player = proxy(ShipAPI.class, name -> "isShuttlePod".equals(name));
		ViewportAPI viewport = (ViewportAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ViewportAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isExternalControl" -> true;
					case "convertScreenXToWorldX", "convertScreenYToWorldY" -> {
						conversions.incrementAndGet();
						yield ((Number) args[0]).floatValue();
					}
					default -> null;
				});
		CombatUIAPI ui = proxy(CombatUIAPI.class, name -> "isShowingCommandUI".equals(name) && commandUi);
		return proxy(CombatEngineAPI.class, name -> switch (name) {
			case "getPlayerShip" -> player;
			case "getViewport" -> viewport;
			case "getCombatUI" -> ui;
			case "getShips" -> ships;
			case "isCombatOver", "isUIShowingDialog" -> false;
			default -> null;
		});
	}

	private InputEventAPI controlEvent(String key, boolean repeat, AtomicBoolean consumed) {
		return (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InputEventAPI.class},
				(instance, method, args) -> switch (method.getName()) {
					case "isConsumed" -> consumed.get();
					case "consume" -> {
						consumed.set(true);
						yield null;
					}
					case "isControlDownEvent" -> key.equals(args[0]);
					case "isRepeat" -> repeat;
					case "getX" -> 120;
					case "getY" -> 80;
					default -> method.getReturnType() == boolean.class ? false : null;
				});
	}

	private CombatEngineAPI engine(boolean freeCamera, boolean commandUi, boolean deployment, boolean dialog) {
		ShipAPI player = proxy(ShipAPI.class, (name) -> switch (name) {
			case "isAlive" -> false;
			case "isShuttlePod" -> true;
			default -> null;
		});
		ViewportAPI viewport = proxy(ViewportAPI.class, (name) ->
				"isExternalControl".equals(name) && freeCamera);
		CombatUIAPI ui = proxy(CombatUIAPI.class, (name) -> switch (name) {
			case "isShowingCommandUI" -> commandUi;
			case "isShowingDeploymentDialog" -> deployment;
			default -> null;
		});
		return proxy(CombatEngineAPI.class, (name) -> switch (name) {
			case "getPlayerShip" -> player;
			case "getViewport" -> viewport;
			case "getCombatUI" -> ui;
			case "isUIShowingDialog" -> dialog;
			default -> null;
		});
	}

	private interface Call {
		Object invoke(String name);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> call.invoke(method.getName())));
	}
}
