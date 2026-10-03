package data.scripts.plugin;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.tools.BattleNext_Logger;
import data.test.fixture.BattleNext_TestGameFixture;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_AllShipsStatusPluginTest extends BattleNext_TestGameFixture {

	private GameState gameState = GameState.COMBAT;
	private final List<ShipAPI> ships = new ArrayList<>();
	private final Set<ShipAPI> departed = new HashSet<>();
	private final List<Object> removed = new ArrayList<>();
	private final FakeAccess access = new FakeAccess();
	private final RecordingAppender appender = new RecordingAppender();
	private BattleNext_AllShipsStatusPlugin plugin;
	private CombatUIAPI ui;
	private boolean combatOver;
	private boolean paused;
	private Logger logger;
	private Level previousLevel;
	private boolean previousAdditivity;

	@BeforeEach
	void configureStatusPlugin() {

		Global.setSettings(proxy(SettingsAPI.class, (method, args) ->
				"getCurrentState".equals(method) ? gameState : null));
		ui = proxy(CombatUIAPI.class, (method, args) -> {
			throw new AssertionError("Native renderer owns UI visibility: " + method);
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getCombatUI" -> ui;
			case "getShips" -> ships;
			case "isEntityInPlay" -> ships.contains(args[0]) && !departed.contains(args[0]);
			case "isPaused" -> paused;
			case "isCombatOver" -> combatOver;
			case "removePlugin" -> {
				removed.add(args[0]);
				yield null;
			}
			default -> throw new AssertionError("Unexpected engine query: " + method);
		});
		plugin = new BattleNext_AllShipsStatusPlugin(access);
		plugin.init(engine);

		BattleNext_Logger.fields();
		logger = Global.getLogger(BattleNext_Logger.class);
		previousLevel = logger.getLevel();
		previousAdditivity = logger.getAdditivity();
		logger.setLevel(Level.INFO);
		logger.setAdditivity(false);
		logger.addAppender(appender);
	}

	@AfterEach
	void restoreLogging() {
		logger.removeAppender(appender);
		logger.setLevel(previousLevel);
		logger.setAdditivity(previousAdditivity);
	}

	@ParameterizedTest
	@CsvSource({"0,false", "0,true", "1,false", "1,true"})
	void refreshesBothOwnersAndAlliesAcrossTheBattlefield(int owner, boolean ally) {

		ShipState state = new ShipState("outside_viewport_and_in_fog", owner);
		state.ally = ally;
		ships.add(state.api);
		paused = true;
		for (int frame = 0; frame < 5; frame++) {
			plugin.advance(0f, List.of());
			plugin.renderInUICoords(null);
		}
		assertEquals(List.of(state.api, state.api, state.api, state.api, state.api), access.refreshed);
		assertEquals(List.of(0.75f, 0.75f, 0.75f, 0.75f, 0.75f), access.alphas);
		assertEquals(1, access.binds);
		assertEquals(5, access.lookups);
		assertEquals(1, access.manager.reticles.size());
		assertTrue(access.manager.reticles.get(0).secondary);
		assertTrue(appender.events.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"isFighter", "isDrone", "isShuttlePod", "isStation", "isStationModule",
			"dead", "expired", "departed", "neutral", "otherOwner"})
	void filtersEveryNonOrdinaryOrInactiveShip(String condition) {

		ShipState state = new ShipState(condition, 1);
		switch (condition) {
			case "dead" -> state.alive = false;
			case "expired" -> state.expired = true;
			case "departed" -> departed.add(state.api);
			case "neutral" -> state.owner = 100;
			case "otherOwner" -> state.owner = 2;
			default -> state.kinds.add(condition);
		}
		ships.add(state.api);
		ShipState ordinary = new ShipState("ordinary", 0);
		ships.add(ordinary.api);
		plugin.renderInUICoords(null);
		assertEquals(List.of(ordinary.api), access.refreshed);
		assertEquals(List.of(0.75f), access.alphas);
	}

	@Test
	void tracksDeploymentDepartureDeathAndCurrentOwnerOnEachRender() {

		ShipState first = new ShipState("first", 0);
		ShipState second = new ShipState("second", 1);
		ships.add(first.api);
		plugin.renderInUICoords(null);
		ships.add(second.api);
		first.owner = 100;
		plugin.renderInUICoords(null);
		first.owner = 1;
		departed.add(second.api);
		plugin.renderInUICoords(null);
		first.alive = false;
		plugin.renderInUICoords(null);
		first.alive = true;
		ships.remove(first.api);
		plugin.renderInUICoords(null);
		assertEquals(List.of(first.api, second.api, first.api), access.refreshed);
		assertTrue(removed.isEmpty());
	}

	@Test
	void retainsCurrentAndFadingPrimaryIdentitiesUntilVanillaRemovesThem() {

		ShipState current = new ShipState("current_R_target", 1);
		ShipState fading = new ShipState("previous_R_target_fading", 1);
		ShipState secondary = new ShipState("secondary", 0);
		ships.addAll(List.of(current.api, fading.api, secondary.api));
		Reticle currentReticle = new Reticle(current.api, false);
		Reticle fadingReticle = new Reticle(fading.api, false);
		Reticle secondaryReticle = new Reticle(secondary.api, true);
		access.manager.reticles.addAll(List.of(currentReticle, fadingReticle, secondaryReticle));

		plugin.renderInUICoords(null);
		plugin.renderInUICoords(null);
		assertEquals(List.of(secondary.api, secondary.api), access.refreshed);
		assertSame(currentReticle, access.manager.reticles.get(0));
		assertSame(fadingReticle, access.manager.reticles.get(1));
		assertFalse(currentReticle.secondary);
		assertFalse(fadingReticle.secondary);

		access.manager.reticles.remove(fadingReticle);
		plugin.renderInUICoords(null);
		assertEquals(List.of(secondary.api, secondary.api, fading.api, secondary.api), access.refreshed);
		assertSame(currentReticle, access.manager.reticles.get(0));
		assertFalse(currentReticle.secondary);
	}

	@Test
	void waitsForCombatUiAndManagerThenReadsTheCurrentManagerEachFrame() {

		ships.add(new ShipState("enemy", 1).api);
		CombatUIAPI readyUi = ui;
		Manager readyManager = access.manager;
		gameState = GameState.TITLE;
		plugin.renderInUICoords(null);
		assertEquals(0, access.binds);
		gameState = GameState.COMBAT;
		ui = null;
		plugin.renderInUICoords(null);
		assertEquals(0, access.binds);
		ui = readyUi;
		access.manager = null;
		plugin.renderInUICoords(null);
		assertEquals(1, access.binds);
		assertTrue(access.refreshed.isEmpty());

		access.manager = readyManager;
		plugin.renderInUICoords(null);
		Manager replacement = new Manager();
		access.manager = replacement;
		plugin.renderInUICoords(null);
		assertEquals(List.of(readyManager, replacement), access.refreshedManagers);
		assertEquals(1, access.binds);
		assertEquals(3, access.lookups);
		assertTrue(removed.isEmpty());
	}

	@Test
	void delegatesStatusBarTagsToTheNativeRenderer() {

		ShipState hidden = new ShipState("module_no_status_bar", 0);
		ShipState visible = new ShipState("visible", 1);
		ships.addAll(List.of(hidden.api, visible.api));
		access.manager.hidden.add(hidden.api);
		plugin.renderInUICoords(null);
		assertEquals(List.of(hidden.api, visible.api), access.refreshed);
		assertEquals(List.of(visible.api), access.manager.reticles.stream().map(reticle -> reticle.ship).toList());
	}

	@ParameterizedTest
	@ValueSource(strings = {"bind", "renderer.lookup", "primary.scan", "ship.refresh"})
	void bridgeFailuresLogOnceUnregisterAndReleaseReferences(String phase) throws Exception {

		ShipState first = new ShipState("first", 0);
		ShipState failed = new ShipState("failed", 1);
		ships.addAll(List.of(first.api, failed.api));
		Manager nativeManager = access.manager;
		Reticle primary = new Reticle(new ShipState("R_target", 1).api, false);
		nativeManager.reticles.add(primary);
		access.failurePhase = phase;
		access.failedShip = failed.api;
		plugin.renderInUICoords(null);
		plugin.renderInUICoords(null);
		plugin.advance(0.15f, List.of());

		assertEquals(List.of(plugin), removed);
		assertEquals(1, access.clears);
		assertNull(access.manager);
		assertReleased();
		assertEquals(1, appender.events.size());
		LoggingEvent event = appender.events.get(0);
		assertEquals(Level.ERROR, event.getLevel());
		JSONObject log = new JSONObject(event.getRenderedMessage());
		assertEquals(BattleNext_AllShipsStatusPlugin.class.getName(), log.getString("source"));
		assertEquals("ship_status.refresh.failed", log.getString("event"));
		JSONObject fields = log.getJSONObject("fields");
		assertEquals(phase, fields.getString("phase"));
		assertEquals(ui.getClass().getName(), fields.getString("ui_class"));
		assertEquals(5, fields.getJSONArray("signatures").length());
		assertEquals(IllegalStateException.class.getName(), log.getJSONObject("exception").getString("type"));
		assertSame(primary, nativeManager.reticles.get(0));
		assertFalse(primary.secondary);
		if (phase.equals("ship.refresh")) {
			assertEquals("failed", fields.getString("ship_id"));
			assertEquals(List.of(first.api), access.refreshed);
			assertEquals(2, nativeManager.reticles.size());
		} else {
			assertTrue(fields.isNull("ship_id"));
			assertTrue(access.refreshed.isEmpty());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void battleEndReleasesOnlyPluginStateFromEitherCallback(boolean render) throws Exception {

		ships.add(new ShipState("enemy", 1).api);
		plugin.renderInUICoords(null);
		Manager nativeManager = access.manager;
		Reticle original = nativeManager.reticles.get(0);
		combatOver = true;
		if (render) {
			plugin.renderInUICoords(null);
		} else {
			plugin.advance(0.1f, List.of());
		}
		plugin.renderInUICoords(null);
		plugin.advance(0.1f, List.of());
		assertReleased();
		assertEquals(List.of(plugin), removed);
		assertEquals(1, access.clears);
		assertSame(original, nativeManager.reticles.get(0));
		assertTrue(appender.events.isEmpty());
	}

	@Test
	void publicNoArgumentConstructorUsesAnIndependentBridge() throws Exception {
		BattleNext_AllShipsStatusPlugin instance = BattleNext_AllShipsStatusPlugin.class.getConstructor().newInstance();
		assertNotSame(plugin, instance);
		instance.advance(0.1f, List.of());
		instance.renderInUICoords(null);
		assertTrue(removed.isEmpty());
	}

	private void assertReleased() throws Exception {
		Field engine = BattleNext_AllShipsStatusPlugin.class.getDeclaredField("engine");
		engine.setAccessible(true);
		Field bound = BattleNext_AllShipsStatusPlugin.class.getDeclaredField("bound");
		bound.setAccessible(true);
		assertNull(engine.get(plugin));
		assertFalse(bound.getBoolean(plugin));
	}

	private static class ShipState {
		private int owner;
		private boolean ally;
		private boolean alive = true;
		private boolean expired;
		private final Set<String> kinds = new HashSet<>();
		private final ShipAPI api;

		private ShipState(String id, int owner) {
			this.owner = owner;
			api = proxy(ShipAPI.class, (method, args) -> switch (method) {
				case "getId" -> id;
				case "getOwner" -> this.owner;
				case "getOriginalOwner" -> owner;
				case "isAlly" -> ally;
				case "isAlive" -> alive;
				case "isExpired" -> expired;
				case "isFighter", "isDrone", "isShuttlePod", "isStation", "isStationModule" -> kinds.contains(method);
				default -> throw new AssertionError("Unexpected ship query: " + method);
			});
		}
	}

	private static class Reticle {
		private final ShipAPI ship;
		private final boolean secondary;

		private Reticle(ShipAPI ship, boolean secondary) {
			this.ship = ship;
			this.secondary = secondary;
		}
	}

	private static class Manager {
		private final List<Reticle> reticles = new ArrayList<>();
		private final Set<ShipAPI> hidden = new HashSet<>();
	}

	private static class FakeAccess implements BattleNext_AllShipsStatusPlugin.Access {
		private Manager manager = new Manager();
		private final List<ShipAPI> refreshed = new ArrayList<>();
		private final List<Float> alphas = new ArrayList<>();
		private final List<Object> refreshedManagers = new ArrayList<>();
		private String failurePhase = "";
		private ShipAPI failedShip;
		private int binds;
		private int lookups;
		private int clears;

		@Override
		public void bind(Class<?> uiClass) {
			binds++;
			if (failurePhase.equals("bind")) throw new IllegalStateException("Binding failed");
		}

		@Override
		public Object renderer(CombatUIAPI ui) {
			lookups++;
			if (failurePhase.equals("renderer.lookup")) throw new IllegalStateException("Renderer lookup failed");
			return manager;
		}

		@Override
		public List<?> reticles(Object renderer) {
			if (failurePhase.equals("primary.scan")) throw new IllegalStateException("Primary scan failed");
			return ((Manager) renderer).reticles;
		}

		@Override
		public boolean isSecondary(Object reticle) {
			return ((Reticle) reticle).secondary;
		}

		@Override
		public ShipAPI ship(Object reticle) {
			return ((Reticle) reticle).ship;
		}

		@Override
		public void refresh(Object renderer, ShipAPI ship, float alpha) {
			if (failurePhase.equals("ship.refresh") && ship == failedShip) throw new IllegalStateException("Refresh failed");
			refreshed.add(ship);
			alphas.add(alpha);
			refreshedManagers.add(renderer);
			Manager current = (Manager) renderer;
			if (current.hidden.contains(ship)) return;
			if (current.reticles.stream().anyMatch(reticle -> reticle.ship == ship)) return;
			current.reticles.add(new Reticle(ship, true));
		}

		@Override
		public void clear() {
			clears++;
			manager = null;
			failedShip = null;
		}
	}

	private static class RecordingAppender extends AppenderSkeleton {
		private final List<LoggingEvent> events = new ArrayList<>();

		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
		}

		@Override
		public void close() {

		}

		@Override
		public boolean requiresLayout() {
			return false;
		}
	}
}
