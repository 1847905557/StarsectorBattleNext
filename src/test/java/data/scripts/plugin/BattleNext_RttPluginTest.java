package data.scripts.plugin;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_RttPluginTest {

	private final Map<ShipAPI, String> shipStates = new IdentityHashMap<>();
	private boolean freeCamera = true;
	private boolean playerPlaying;
	private boolean deployment;
	private boolean dialog;
	private boolean combatOver;
	private boolean paused;
	private int removals;

	@Test
	void inputRequiresFreeCameraAndUncoveredBattle() {
		assertTrue(BattleNext_RttPlugin.acceptsMouseInput(engine(true, false, false, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(false, false, false, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(true, true, false, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(true, false, true, false)));
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine(true, false, false, true)));
	}

	@ParameterizedTest
	@CsvSource({"false,false", "false,true", "true,false", "true,true"})
	void clickAndBoxSelectionUseTheConfiguredStrafeControlAtGestureStart(boolean additive, boolean dragging) throws ReflectiveOperationException {
		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		ShipAPI first = commandShip(new AtomicInteger(), new AtomicInteger());
		ShipAPI second = commandShip(new AtomicInteger(), new AtomicInteger());
		second.getLocation().set(100f, 0f);
		plugin.init(commandEngine(List.of(first, second), false, new AtomicInteger()));
		select(plugin, first);
		plugin.processInputPreCoreControls(0f, List.of(
				mouseEvent("down", dragging ? 90 : 100, dragging ? -10 : 0, additive),
				mouseEvent("move", dragging ? 110 : 100, dragging ? 10 : 0, false),
				mouseEvent("up", dragging ? 110 : 100, dragging ? 10 : 0, false)));
		Field selected = BattleNext_RttPlugin.class.getDeclaredField("selected");
		selected.setAccessible(true);
		assertEquals(additive ? Set.of(first, second) : Set.of(second), selected.get(plugin));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void advanceUpdatesGestureCoverageWhilePreservingSelectedShips(boolean covered) throws ReflectiveOperationException {

		BattleNext_RttPlugin plugin = new BattleNext_RttPlugin();
		ShipAPI ship = commandShip(new AtomicInteger(), new AtomicInteger());
		AtomicBoolean commandUi = new AtomicBoolean();
		plugin.init(commandEngine(List.of(ship), commandUi, new AtomicInteger()));
		select(plugin, ship);
		plugin.processInputPreCoreControls(0f, List.of(mouseEvent("down", -10, -10, true),
				mouseEvent("move", 10, 10, true)));
		commandUi.set(covered);
		plugin.advance(0f, List.of());
		for (String name : List.of("pressing", "dragging", "addSelection")) {
			Field field = BattleNext_RttPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			assertEquals(!covered, field.getBoolean(plugin), name);
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

		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", false, false, consumed)));

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
		coordinateConversions.set(0);
		AtomicBoolean consumed = new AtomicBoolean();

		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_USE_SYSTEM", false, false, consumed)));

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
		AtomicBoolean commandUi = new AtomicBoolean();
		plugin.init(commandEngine(List.of(ship), commandUi, new AtomicInteger()));
		select(plugin, ship);
		commandUi.set(true);
		AtomicBoolean covered = new AtomicBoolean();
		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", false, false, covered)));
		assertFalse(covered.get());
		assertEquals(0, commands.get());

		commandUi.set(false);
		AtomicBoolean repeat = new AtomicBoolean();
		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", true, false, repeat)));
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

	@Test
	void allSevenGroupsKeepIndependentSnapshotsAndRecallTheirOwnMembers() throws ReflectiveOperationException {

		var plugin = new BattleNext_RttPlugin();
		ShipAPI first = commandShip(new AtomicInteger(), new AtomicInteger());
		ShipAPI second = commandShip(new AtomicInteger(), new AtomicInteger());
		second.getLocation().set(100f, 0f);
		plugin.init(commandEngine(List.of(first, second), false, new AtomicInteger()));
		for (int number = 1; number <= 7; number++) {
			select(plugin, number % 2 == 0 ? second : first);
			pressGroup(plugin, "SHIP_TOGGLE_GROUP_" + number, false);
		}
		for (int number = 7; number >= 1; number--) {
			pressGroup(plugin, "SHIP_SELECT_GROUP_" + number, false);
			assertEquals(List.of(number % 2 == 0 ? second : first), new ArrayList<>(selection(plugin)));
		}
	}

	@Test
	void groupsOverwriteShareShipsAndMergeSelectionsWithoutAliasing() throws ReflectiveOperationException {

		var plugin = new BattleNext_RttPlugin();
		AtomicInteger firstCommands = new AtomicInteger();
		AtomicInteger secondCommands = new AtomicInteger();
		ShipAPI first = commandShip(firstCommands, new AtomicInteger());
		ShipAPI second = commandShip(secondCommands, new AtomicInteger());
		second.getLocation().set(100f, 0f);
		plugin.init(commandEngine(List.of(first, second), false, new AtomicInteger()));
		select(plugin, first, second);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		select(plugin, second);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_2", false);
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", false);
		assertEquals(List.of(first, second), new ArrayList<>(selection(plugin)));
		select(plugin, first);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		pressGroup(plugin, "SHIP_SELECT_GROUP_2", false);
		assertEquals(Set.of(second), selection(plugin));
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", true);
		assertEquals(List.of(second, first), new ArrayList<>(selection(plugin)));
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", true);
		assertEquals(List.of(second, first), new ArrayList<>(selection(plugin)));
		AtomicBoolean consumed = new AtomicBoolean();
		plugin.processInputPreCoreControls(0f, List.of(controlEvent("SHIP_VENT_FLUX", false, false, consumed)));
		assertTrue(consumed.get());
		assertEquals(1, firstCommands.get());
		assertEquals(1, secondCommands.get());
	}

	@Test
	void emptyAssignmentAndRecallPreserveTheSavedGroupAndCurrentSelection() throws ReflectiveOperationException {
		var plugin = new BattleNext_RttPlugin();
		ShipAPI first = commandShip(new AtomicInteger(), new AtomicInteger());
		plugin.init(commandEngine(List.of(first), false, new AtomicInteger()));
		select(plugin, first);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		plugin.processInputPreCoreControls(0f, List.of(mouseEvent("down", 500, 500, false), mouseEvent("up", 500, 500, false)));
		assertTrue(selection(plugin).isEmpty());
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", false);
		assertEquals(Set.of(first), selection(plugin));
		pressGroup(plugin, "SHIP_SELECT_GROUP_7", false);
		assertEquals(Set.of(first), selection(plugin));
		pressGroup(plugin, "SHIP_SELECT_GROUP_7", true);
		assertEquals(Set.of(first), selection(plugin));
	}

	@ParameterizedTest
	@ValueSource(strings = {"SHIP_TOGGLE_GROUP_2", "SHIP_SELECT_GROUP_1"})
	void groupShortcutsEndPendingGesturesBeforeMouseRelease(String control) throws ReflectiveOperationException {
		var plugin = new BattleNext_RttPlugin();
		ShipAPI first = commandShip(new AtomicInteger(), new AtomicInteger());
		ShipAPI second = commandShip(new AtomicInteger(), new AtomicInteger());
		second.getLocation().set(100f, 0f);
		plugin.init(commandEngine(List.of(first, second), false, new AtomicInteger()));
		select(plugin, first);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		plugin.processInputPreCoreControls(0f, List.of(mouseEvent("down", 90, -10, true), mouseEvent("move", 110, 10, true)));
		pressGroup(plugin, control, false);
		plugin.processInputPreCoreControls(0f, List.of(mouseEvent("up", 110, 10, false)));
		assertEquals(Set.of(first), selection(plugin));
		for (String name : List.of("pressing", "dragging", "addSelection")) {
			Field field = BattleNext_RttPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			assertFalse(field.getBoolean(plugin), name);
		}
	}

	@Test
	void invalidMembersArePrunedAndBattleEndReleasesEveryGroup() throws ReflectiveOperationException {

		var plugin = new BattleNext_RttPlugin();
		List<ShipAPI> ships = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			ShipAPI ship = commandShip(new AtomicInteger(), new AtomicInteger());
			ship.getLocation().set(index * 100f, 0f);
			ships.add(ship);
		}
		var engine = commandEngine(ships, false, new AtomicInteger());
		plugin.init(engine);
		select(plugin, ships.get(0), ships.get(1), ships.get(2), ships.get(3));
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		ShipAPI survivor = ships.get(3);
		ShipAPI other = ships.get(4);
		shipStates.put(ships.get(0), "destroyed");
		shipStates.put(ships.get(1), "expired");
		ships.remove(2);
		plugin.advance(0f, List.of());
		select(plugin, other);
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", false);
		assertEquals(Set.of(survivor), selection(plugin));
		shipStates.put(survivor, "destroyed");
		plugin.advance(0f, List.of());
		select(plugin, other);
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", false);
		assertEquals(Set.of(other), selection(plugin));
		combatOver = true;
		plugin.advance(0f, List.of());
		assertTrue(selection(plugin).isEmpty());
		Field groups = BattleNext_RttPlugin.class.getDeclaredField("groups");
		groups.setAccessible(true);
		assertTrue(((Map<?, ?>) groups.get(plugin)).isEmpty());
		assertEquals(1, removals);
		combatOver = false;
		var nextBattle = new BattleNext_RttPlugin();
		nextBattle.init(engine);
		select(nextBattle, other);
		pressGroup(nextBattle, "SHIP_SELECT_GROUP_1", false);
		assertEquals(Set.of(other), selection(nextBattle));
		assertTrue(((Map<?, ?>) groups.get(nextBattle)).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"camera", "player", "command", "deployment", "dialog"})
	void inactiveRttPreservesGroupsAndLeavesTheirEventsForCoreControls(String reason) throws ReflectiveOperationException {

		var plugin = new BattleNext_RttPlugin();
		ShipAPI first = commandShip(new AtomicInteger(), new AtomicInteger());
		ShipAPI second = commandShip(new AtomicInteger(), new AtomicInteger());
		second.getLocation().set(100f, 0f);
		AtomicBoolean commandUi = new AtomicBoolean();
		plugin.init(commandEngine(List.of(first, second), commandUi, new AtomicInteger()));
		select(plugin, first);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		select(plugin, second);
		freeCamera = !reason.equals("camera");
		playerPlaying = reason.equals("player");
		commandUi.set(reason.equals("command"));
		deployment = reason.equals("deployment");
		dialog = reason.equals("dialog");
		for (String key : List.of("SHIP_TOGGLE_GROUP_1", "SHIP_SELECT_GROUP_1")) {
			AtomicBoolean consumed = new AtomicBoolean();
			plugin.processInputPreCoreControls(0f, List.of(controlEvent(key, false, false, consumed)));
			assertFalse(consumed.get());
			assertEquals(Set.of(second), selection(plugin));
		}
		freeCamera = true;
		playerPlaying = false;
		commandUi.set(false);
		deployment = dialog = false;
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", false);
		assertEquals(Set.of(first), selection(plugin));
	}

	@Test
	void pauseAllowsGroupsAndRepeatOrConsumedEventsPreserveTheirContents() throws ReflectiveOperationException {

		var plugin = new BattleNext_RttPlugin();
		ShipAPI first = commandShip(new AtomicInteger(), new AtomicInteger());
		ShipAPI second = commandShip(new AtomicInteger(), new AtomicInteger());
		second.getLocation().set(100f, 0f);
		plugin.init(commandEngine(List.of(first, second), false, new AtomicInteger()));
		paused = true;
		select(plugin, first);
		pressGroup(plugin, "SHIP_TOGGLE_GROUP_1", false);
		select(plugin, second);
		for (String key : List.of("SHIP_TOGGLE_GROUP_1", "SHIP_SELECT_GROUP_1")) {
			AtomicBoolean repeated = new AtomicBoolean();
			plugin.processInputPreCoreControls(0f, List.of(controlEvent(key, true, false, repeated)));
			assertFalse(repeated.get());
			assertEquals(Set.of(second), selection(plugin));
			AtomicBoolean consumed = new AtomicBoolean(true);
			plugin.processInputPreCoreControls(0f, List.of(controlEvent(key, false, false, consumed)));
			assertEquals(Set.of(second), selection(plugin));
		}
		pressGroup(plugin, "SHIP_SELECT_GROUP_1", false);
		assertEquals(Set.of(first), selection(plugin));
	}

	private InputEventAPI pressGroup(BattleNext_RttPlugin plugin, String key, boolean additive) {
		AtomicBoolean consumed = new AtomicBoolean();
		InputEventAPI event = controlEvent(key, false, additive, consumed);
		plugin.processInputPreCoreControls(0f, List.of(event));
		assertTrue(consumed.get(), key);
		return event;
	}

	private Set<ShipAPI> selection(BattleNext_RttPlugin plugin) throws ReflectiveOperationException {
		Field field = BattleNext_RttPlugin.class.getDeclaredField("selected");
		field.setAccessible(true);
		return (Set<ShipAPI>) field.get(plugin);
	}

	private void select(BattleNext_RttPlugin plugin, ShipAPI... ships) throws ReflectiveOperationException {
		int left = (int) Arrays.stream(ships).mapToDouble(ship -> ship.getLocation().x).min().orElseThrow() - 10;
		int right = (int) Arrays.stream(ships).mapToDouble(ship -> ship.getLocation().x).max().orElseThrow() + 10;
		plugin.processInputPreCoreControls(0f, List.of(mouseEvent("down", left, -10, false),
				mouseEvent("move", right, 10, false), mouseEvent("up", right, 10, false)));
		Field field = BattleNext_RttPlugin.class.getDeclaredField("selected");
		field.setAccessible(true);
		assertEquals(Set.of(ships), field.get(plugin));
	}

	private InputEventAPI mouseEvent(String phase, int x, int y, boolean additive) {
		return (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InputEventAPI.class},
				(instance, method, args) -> switch (method.getName()) {
					case "isLMBDownEvent" -> phase.equals("down");
					case "isMouseMoveEvent" -> phase.equals("move");
					case "isLMBUpEvent" -> phase.equals("up");
					case "isShiftDown" -> !additive;
					case "isControlActivated" -> "SHIP_STRAFE_KEY".equals(args[0]) && additive;
					case "getX" -> x;
					case "getY" -> y;
					default -> method.getReturnType() == boolean.class ? false : null;
				});
	}

	private ShipAPI commandShip(AtomicInteger commands, AtomicInteger aiChanges) {
		Vector2f location = new Vector2f();
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipAPI.class},
				(instance, method, args) -> switch (method.getName()) {
					case "hashCode" -> System.identityHashCode(instance);
					case "equals" -> instance == args[0];
					case "isAlive" -> !"destroyed".equals(shipStates.get(instance));
					case "isExpired" -> "expired".equals(shipStates.get(instance));
					case "isFighter", "isDrone", "isStation", "isStationModule" -> false;
					case "getOwner", "getOriginalOwner" -> 0;
					case "getLocation" -> location;
					case "getCollisionRadius" -> 20f;
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
		return commandEngine(ships, new AtomicBoolean(commandUi), conversions);
	}

	private CombatEngineAPI commandEngine(List<ShipAPI> ships, AtomicBoolean commandUi, AtomicInteger conversions) {
		ShipAPI player = proxy(ShipAPI.class, name -> switch (name) {
			case "isShuttlePod" -> !playerPlaying;
			case "isAlive" -> playerPlaying;
			default -> null;
		});
		ViewportAPI viewport = (ViewportAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ViewportAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isExternalControl" -> freeCamera;
					case "convertScreenXToWorldX", "convertScreenYToWorldY" -> {
						conversions.incrementAndGet();
						yield ((Number) args[0]).floatValue();
					}
					default -> null;
				});
		CombatUIAPI ui = proxy(CombatUIAPI.class, name -> switch (name) {
			case "isShowingCommandUI" -> commandUi.get();
			case "isShowingDeploymentDialog" -> deployment;
			default -> null;
		});
		return proxy(CombatEngineAPI.class, name -> switch (name) {
			case "getPlayerShip" -> player;
			case "getViewport" -> viewport;
			case "getCombatUI" -> ui;
			case "getShips" -> ships;
			case "isCombatOver" -> combatOver;
			case "isPaused" -> paused;
			case "isUIShowingDialog" -> dialog;
			case "removePlugin" -> {
				removals++;
				yield null;
			}
			default -> null;
		});
	}

	private InputEventAPI controlEvent(String key, boolean repeat, boolean additive, AtomicBoolean consumed) {
		return (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InputEventAPI.class},
				(instance, method, args) -> switch (method.getName()) {
					case "isConsumed" -> consumed.get();
					case "consume" -> {
						consumed.set(true);
						yield null;
					}
					case "isControlDownEvent" -> key.equals(args[0]);
					case "isControlActivated" -> "SHIP_STRAFE_KEY".equals(args[0]) && additive;
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
