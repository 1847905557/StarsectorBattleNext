package data.scripts.plugin;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.tools.BattleNext_KeyTuning;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestGameFixture;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;

class BattleNext_FreeCameraPluginTest extends BattleNext_TestGameFixture {

	private final Set<Integer> keys = new HashSet<>();
	private final Set<String> overlays = new HashSet<>();
	private final List<Boolean> controlChanges = new ArrayList<>();
	private final MutableStat timeMult = new MutableStat(1f);
	private final Vector2f center = new Vector2f();
	private float width = 2400f;
	private float height = 1350f;
	private boolean external;
	private boolean playing;
	private boolean paused;
	private boolean combatOver;
	private boolean resetViewOnRelease;
	private int geometryWrites;
	private int centerWrites;
	private BattleNext_FreeCameraPlugin camera;
	private CombatEngineAPI engine;
	private Field settingsValues;
	private Object previousValues;
	private Field keyboardInput;
	private Object previousKeyboardInput;

	@BeforeEach
	void configureCamera() throws Exception {

		keyboardInput = BattleNext_KeyTuning.class.getDeclaredField("keyPressed");
		keyboardInput.setAccessible(true);
		previousKeyboardInput = keyboardInput.get(null);
		keyboardInput.set(null, (IntPredicate) keys::contains);
		settingsValues = BattleNext_Settings.class.getDeclaredField("values");
		settingsValues.setAccessible(true);
		previousValues = settingsValues.get(null);
		settingsValues.set(null, new JSONObject().put("defaultTimeMult", 1f).put("boostTimeMult", 3f));
		ShipAPI player = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "isAlive" -> playing;
			case "isShuttlePod" -> !playing;
			default -> null;
		});
		CombatUIAPI ui = proxy(CombatUIAPI.class, (method, args) -> switch (method) {
			case "isShowingCommandUI" -> overlays.contains("command");
			case "isShowingDeploymentDialog" -> overlays.contains("deployment");
			default -> null;
		});
		ViewportAPI viewport = proxy(ViewportAPI.class, (method, args) -> switch (method) {
			case "isExternalControl" -> external;
			case "setExternalControl" -> {
				external = (Boolean) args[0];
				controlChanges.add(external);
				if (!external && resetViewOnRelease) {
					center.set(0f, 0f);
					width = 1600f;
					height = 900f;
				}
				yield null;
			}
			case "getVisibleWidth" -> width;
			case "getVisibleHeight" -> height;
			case "getCenter" -> center;
			case "getLLX" -> center.x - width * 0.5f;
			case "getLLY" -> center.y - height * 0.5f;
			case "set" -> {
				width = (Float) args[2];
				height = (Float) args[3];
				center.set((Float) args[0] + width * 0.5f, (Float) args[1] + height * 0.5f);
				geometryWrites++;
				yield null;
			}
			case "setCenter" -> {
				center.set((Vector2f) args[0]);
				centerWrites++;
				yield null;
			}
			default -> null;
		});
		engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getPlayerShip" -> player;
			case "getCombatUI" -> ui;
			case "getViewport" -> viewport;
			case "isUIShowingDialog" -> overlays.contains("dialog");
			case "isPaused" -> paused;
			case "isCombatOver" -> combatOver;
			case "getTimeMult" -> timeMult;
			case "getMapWidth", "getMapHeight" -> 50000f;
			default -> null;
		});
		camera = new BattleNext_FreeCameraPlugin();
		camera.init(engine);
	}

	@AfterEach
	void restoreSettingsValues() throws Exception {
		settingsValues.set(null, previousValues);
		keyboardInput.set(null, previousKeyboardInput);
	}

	@ParameterizedTest
	@ValueSource(strings = {"command", "deployment", "dialog"})
	void coveredUiOwnsWheelAltAndViewport(String overlay) {

		toggleCamera();
		int previousGeometryWrites = geometryWrites;
		int previousCenterWrites = centerWrites;
		keys.addAll(List.of(Keyboard.KEY_W, Keyboard.KEY_D, Keyboard.KEY_RCONTROL));
		overlays.add(overlay);
		AtomicBoolean wheelConsumed = new AtomicBoolean();
		AtomicBoolean altConsumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0.1f, List.of(wheel(1, wheelConsumed), alt(altConsumed)));
		assertFalse(external);
		assertFalse(wheelConsumed.get());
		assertFalse(altConsumed.get());
		assertFalse(BattleNext_RttPlugin.acceptsMouseInput(engine));
		for (int frame = 0; frame < 10; frame++) {
			camera.advance(0.1f, List.of());
		}
		assertEquals(previousGeometryWrites, geometryWrites);
		assertEquals(previousCenterWrites, centerWrites);
		assertEquals(List.of(true, false), controlChanges);
		assertEquals(2400f, width);
		assertEquals(1350f, height);
	}

	@ParameterizedTest
	@ValueSource(strings = {"command", "deployment", "dialog"})
	void resumingCameraRestoresFreeViewportBeforeFirstWheel(String overlay) {

		center.set(600f, -400f);
		toggleCamera();
		overlays.add(overlay);
		camera.processInputPreCoreControls(0f, List.of());
		width = 3200f;
		height = 1600f;
		center.set(300f, -100f);
		overlays.remove(overlay);
		AtomicBoolean consumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0f, List.of(wheel(1, consumed)));
		camera.advance(0f, List.of());
		assertTrue(consumed.get());
		assertTrue(external);
		assertTrue(BattleNext_RttPlugin.acceptsMouseInput(engine));
		assertEquals(2400f / 1.1f, width, 0.001f);
		assertEquals(1350f / 1.1f, height, 0.001f);
		assertEquals(600f, center.x);
		assertEquals(-400f, center.y);
		assertEquals(List.of(true, false, true), controlChanges);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void nativeResetBetweenCallbacksRestoresSavedViewFromEitherResumeEntry(boolean resumeInAdvance) {

		center.set(1200f, -800f);
		toggleCamera();
		keys.add(Keyboard.KEY_D);
		camera.advance(0.1f, List.of());
		keys.clear();
		camera.processInputPreCoreControls(0f, List.of(wheel(1, new AtomicBoolean())));
		camera.advance(0f, List.of());
		Vector2f savedCenter = new Vector2f(center);
		float savedWidth = width;
		float savedHeight = height;
		camera.processInputPreCoreControls(0f, List.of());
		overlays.add("command");
		center.set(0f, 0f);
		width = 1600f;
		height = 900f;
		resetViewOnRelease = true;
		camera.advance(0f, List.of());
		assertFalse(external);
		for (int frame = 0; frame < 10; frame++) {
			center.set(frame * 10f, -frame * 10f);
			camera.advance(0f, List.of());
		}
		overlays.clear();
		center.set(0f, 0f);
		if (resumeInAdvance) {
			camera.advance(0f, List.of());
		} else {
			camera.processInputPreCoreControls(0f, List.of());
		}
		assertTrue(external);
		assertEquals(savedWidth, width);
		assertEquals(savedHeight, height);
		assertEquals(savedCenter.x, center.x);
		assertEquals(savedCenter.y, center.y);
		assertEquals(savedCenter.x - savedWidth * 0.5f, engine.getViewport().getLLX(), 0.001f);
		assertEquals(savedCenter.y - savedHeight * 0.5f, engine.getViewport().getLLY(), 0.001f);
		assertTrue(BattleNext_RttPlugin.acceptsMouseInput(engine));
	}

	@Test
	void advanceUsesOwnedCenterWhenNativeViewChangesOnTheClosingFrame() {
		center.set(1000f, -500f);
		toggleCamera();
		overlays.add("command");
		camera.advance(0f, List.of());
		overlays.clear();
		camera.processInputPreCoreControls(0f, List.of());
		center.set(0f, 0f);
		width = 1600f;
		height = 900f;
		camera.advance(0f, List.of());
		assertEquals(1000f, center.x);
		assertEquals(-500f, center.y);
		assertEquals(2400f, width);
		assertEquals(1350f, height);
	}

	@ParameterizedTest
	@ValueSource(strings = {"alt", "toggle", "player"})
	void explicitCameraExitStartsNewSessionAtCurrentNativeView(String exit) {

		center.set(1200f, -800f);
		toggleCamera();
		switch (exit) {
			case "alt" -> camera.processInputPreCoreControls(0f, List.of(alt(new AtomicBoolean())));
			case "toggle" -> toggleCamera();
			case "player" -> {
				playing = true;
				camera.advance(0f, List.of());
				playing = false;
			}
		}
		assertFalse(external);
		center.set(-900f, 400f);
		width = 3200f;
		height = 1800f;
		toggleCamera();
		assertTrue(external);
		assertEquals(-900f, center.x);
		assertEquals(400f, center.y);
		assertEquals(3200f, width);
		assertEquals(1800f, height);
	}

	@Test
	void commandUiOpeningAfterPreCoreInputStopsAdvanceCameraWrites() {
		toggleCamera();
		camera.processInputPreCoreControls(0f, List.of());
		int previousWrites = geometryWrites;
		keys.add(Keyboard.KEY_D);
		overlays.add("command");
		camera.advance(0.5f, List.of());
		assertFalse(external);
		assertEquals(previousWrites, geometryWrites);
		assertEquals(0f, center.x);
	}

	@Test
	void overlappingUiAndRepeatedOpenClosePreserveLatestFreeView() {

		toggleCamera();
		for (int iteration = 0; iteration < 100; iteration++) {
			keys.add(Keyboard.KEY_D);
			camera.advance(0.01f, List.of());
			keys.clear();
			camera.processInputPreCoreControls(0f, List.of(wheel(iteration % 2 == 0 ? 1 : -1, new AtomicBoolean())));
			camera.advance(0f, List.of());
			float savedWidth = width;
			float savedHeight = height;
			Vector2f savedCenter = new Vector2f(center);
			overlays.addAll(List.of("command", "deployment"));
			camera.processInputPreCoreControls(0f, List.of());
			int previousWrites = geometryWrites;
			overlays.remove("command");
			camera.advance(0f, List.of());
			assertFalse(external);
			assertEquals(previousWrites, geometryWrites);
			width = 2400f + iteration;
			height = 1350f + iteration;
			center.set(iteration, -iteration);
			overlays.clear();
			camera.processInputPreCoreControls(0f, List.of());
			assertTrue(external);
			assertEquals(savedWidth, width);
			assertEquals(savedHeight, height);
			assertEquals(savedCenter.x, center.x);
			assertEquals(savedCenter.y, center.y);
			camera.advance(0f, List.of());
			assertEquals(savedCenter.x, center.x);
			assertEquals(savedCenter.y, center.y);
		}
		assertEquals(201, controlChanges.size());
	}

	@Test
	void uiClearsPendingToggleAndHeldUiKeyRequiresFreshPress() {

		toggleCamera();
		keys.add(Keyboard.KEY_RCONTROL);
		camera.advance(0f, List.of());
		overlays.add("command");
		camera.processInputPreCoreControls(0f, List.of());
		camera.advance(0f, List.of());
		overlays.clear();
		camera.processInputPreCoreControls(0f, List.of());
		camera.advance(0f, List.of());
		assertTrue(external);
		keys.remove(Keyboard.KEY_RCONTROL);
		camera.advance(0f, List.of());
		assertTrue(external);
		toggleCamera();
		assertFalse(external);
	}

	@Test
	void resumingMovementStartsAtBaseAccelerationWithSavedViewport() {

		toggleCamera();
		keys.add(Keyboard.KEY_D);
		camera.advance(4f, List.of());
		Vector2f savedCenter = new Vector2f(center);
		overlays.add("command");
		camera.processInputPreCoreControls(0f, List.of());
		camera.advance(1f, List.of());
		width = 3200f;
		height = 1600f;
		center.set(300f, -100f);
		overlays.clear();
		camera.processInputPreCoreControls(0f, List.of());
		camera.advance(0.1f, List.of());
		assertEquals(savedCenter.x + 0.33f * 2400f * 0.1f, center.x, 0.001f);
		assertEquals(savedCenter.y, center.y);
	}

	@Test
	void disabledCameraPreservesOtherViewportControlAndUiToggleStaysInactive() {

		external = true;
		overlays.add("command");
		keys.add(Keyboard.KEY_RCONTROL);
		AtomicBoolean consumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0f, List.of(wheel(1, consumed)));
		camera.advance(0f, List.of());
		assertTrue(external);
		assertTrue(controlChanges.isEmpty());
		assertFalse(consumed.get());
		external = false;
		overlays.clear();
		keys.clear();
		camera.advance(0f, List.of());
		assertFalse(external);
		assertTrue(controlChanges.isEmpty());
		assertEquals(0, geometryWrites);
	}

	@Test
	void playerControlReleasesCameraAndRestoresNormalTimeMultiplier() {

		toggleCamera();
		keys.add(Keyboard.KEY_LSHIFT);
		camera.advance(0f, List.of());
		assertEquals(3f, timeMult.getModifiedValue());
		playing = true;
		AtomicBoolean consumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0f, List.of(wheel(1, consumed)));
		camera.advance(0f, List.of());
		assertFalse(external);
		assertFalse(consumed.get());
		assertEquals(1f, timeMult.getModifiedValue());
		playing = false;
		keys.clear();
		camera.advance(0f, List.of());
		assertFalse(external);
	}

	@Test
	void spectatorPauseKeepsWheelMovementAndExistingZoomBounds() {

		toggleCamera();
		paused = true;
		keys.add(Keyboard.KEY_D);
		AtomicBoolean consumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0.1f, List.of(wheel(1, consumed)));
		camera.advance(0.1f, List.of());
		assertTrue(consumed.get());
		assertTrue(center.x > 0f);
		for (int step = 0; step < 100; step++) {
			camera.processInputPreCoreControls(0f, List.of(wheel(1, new AtomicBoolean())));
		}
		camera.advance(0f, List.of());
		assertEquals(800f, width);
		assertEquals(450f, height);
		for (int step = 0; step < 100; step++) {
			camera.processInputPreCoreControls(0f, List.of(wheel(-1, new AtomicBoolean())));
		}
		camera.advance(0f, List.of());
		assertEquals(16000f, width);
		assertEquals(9000f, height);
	}

	@Test
	void altDisablesCameraAndLeavesFollowingWheelForCoreControls() {
		toggleCamera();
		AtomicBoolean altConsumed = new AtomicBoolean();
		AtomicBoolean wheelConsumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0f, List.of(alt(altConsumed), wheel(1, wheelConsumed)));
		camera.advance(0f, List.of());
		assertTrue(altConsumed.get());
		assertFalse(wheelConsumed.get());
		assertFalse(external);
		assertEquals(List.of(true, false), controlChanges);
	}

	@Test
	void consumedWheelAndCombatEndKeepInputForTheirExistingOwners() {
		toggleCamera();
		int previousWrites = geometryWrites;
		camera.processInputPreCoreControls(0f, List.of(wheel(1, new AtomicBoolean(true))));
		camera.advance(0f, List.of());
		assertEquals(2400f, width);
		assertEquals(1350f, height);
		combatOver = true;
		AtomicBoolean consumed = new AtomicBoolean();
		camera.processInputPreCoreControls(0f, List.of(wheel(1, consumed)));
		camera.advance(0.1f, List.of());
		assertFalse(consumed.get());
		assertEquals(previousWrites + 1, geometryWrites);
	}

	private void toggleCamera() {
		keys.add(Keyboard.KEY_RCONTROL);
		camera.advance(0f, List.of());
		keys.remove(Keyboard.KEY_RCONTROL);
		camera.advance(0f, List.of());
	}

	private InputEventAPI wheel(int direction, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "isMouseScrollEvent" -> true;
			case "getEventValue" -> direction;
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
	}

	private InputEventAPI alt(AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "isAltDown" -> true;
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
	}
}
