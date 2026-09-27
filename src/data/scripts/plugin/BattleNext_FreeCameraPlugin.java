package data.scripts.plugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Settings;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class BattleNext_FreeCameraPlugin extends BaseEveryFrameCombatPlugin {

	private static final String ID = "BattleNext_FreeCameraPlugin";
	private static final BattleNext_I18nSection STRINGS = BattleNext_I18nSection.getInstance("Combat", "BattleNext_combat_");

	// Movement speed in visible viewport fractions per second; held keys accelerate toward the cap.
	private static final float CAMERA_BASE_SPEED = 0.3f;
	private static final float CAMERA_ACCEL_PER_SEC = 0.3f;
	private static final float CAMERA_MAX_SPEED = 2f;

	// Viewport fraction multiplier applied per mouse wheel notch.
	private static final float CAMERA_ZOOM_STEP = 1.1f;
	private static final float CAMERA_MIN_ZOOM = 0.5f;
	private static final float CAMERA_MAX_ZOOM = 10f;

	private CombatEngineAPI engine;

	private boolean cameraToggle;
	private boolean externalCamera;
	private boolean boostActive;

	private float moveHoldTime;
	private float viewportWidth;
	private float viewportHeight;
	private float screenWidth;
	private float screenHeight;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		screenWidth = Global.getSettings().getScreenWidth();
		screenHeight = Global.getSettings().getScreenHeight();
	}

	@Override
	public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {

		if (engine == null || engine.isCombatOver()) return;

		if (isPlayerPlaying(engine)) {
			return;
		}

		// events here
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (!externalCamera) continue;
			if (event.isMouseScrollEvent() && event.getEventValue() > 0) {
				viewportWidth = Math.max(screenWidth * CAMERA_MIN_ZOOM, viewportWidth / CAMERA_ZOOM_STEP);
				viewportHeight = Math.max(screenHeight * CAMERA_MIN_ZOOM, viewportHeight / CAMERA_ZOOM_STEP);
				event.consume();
				continue;
			}
			if (event.isMouseScrollEvent() && event.getEventValue() < 0) {
				viewportWidth = Math.min(screenWidth * CAMERA_MAX_ZOOM, viewportWidth * CAMERA_ZOOM_STEP);
				viewportHeight = Math.min(screenHeight * CAMERA_MAX_ZOOM, viewportHeight * CAMERA_ZOOM_STEP);
				event.consume();
				continue;
			}
			if (event.isAltDown()) {
				externalCamera = false;
				engine.getViewport().setExternalControl(false);
				event.consume();
				continue;
			}
		}
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (engine == null || engine.isCombatOver()) return;

		if (isPlayerPlaying(engine)) {
			if (externalCamera) {
				externalCamera = false;
				engine.getViewport().setExternalControl(externalCamera);
			}

			// forced 1f here
			engine.getTimeMult().modifyMult(ID, 1f);
			return;
		}

		boolean boosting = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
		float timeMult = boosting ? BattleNext_Settings.getBoostTimeMult() : BattleNext_Settings.getDefaultTimeMult();
		engine.getTimeMult().modifyMult(ID, timeMult);
		if (boosting != boostActive) {
			boostActive = boosting;
			engine.getCombatUI().addMessage(0, STRINGS.format("time_multiplier", BattleNext_I18nSection.buildFactor(timeMult)));
		}

		if (Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) cameraToggle = true;
		else if (cameraToggle) {
			cameraToggle = false;
			externalCamera = !externalCamera;
			if (externalCamera) {
				viewportWidth = engine.getViewport().getVisibleWidth();
				viewportHeight = engine.getViewport().getVisibleHeight();
			}
			engine.getViewport().setExternalControl(externalCamera);
			engine.getCombatUI().addMessage(0, STRINGS.get(externalCamera ? "camera_enabled" : "camera_disabled"));
		}

		if (externalCamera) {
			float dx = (Keyboard.isKeyDown(Keyboard.KEY_D) ? 1f : 0f) - (Keyboard.isKeyDown(Keyboard.KEY_A) ? 1f : 0f);
			float dy = (Keyboard.isKeyDown(Keyboard.KEY_W) ? 1f : 0f) - (Keyboard.isKeyDown(Keyboard.KEY_S) ? 1f : 0f);
			Vector2f center = new Vector2f(engine.getViewport().getCenter());

			if (dx != 0f || dy != 0f) {
				moveHoldTime += amount;
				float speed = Math.min(CAMERA_BASE_SPEED + CAMERA_ACCEL_PER_SEC * moveHoldTime, CAMERA_MAX_SPEED);
				if (dx != 0f && dy != 0f) {
					// 1 / sqrt(2), keeps diagonal speed equal to axis speed.
					dx *= 0.7071f;
					dy *= 0.7071f;
				}
				center.x += dx * speed * viewportWidth * amount;
				center.y += dy * speed * viewportHeight * amount;
			} else {
				moveHoldTime = 0f;
			}
			center.x = Math.max(-engine.getMapWidth() / 2f, Math.min(engine.getMapWidth() / 2f, center.x));
			center.y = Math.max(-engine.getMapHeight() / 2f, Math.min(engine.getMapHeight() / 2f, center.y));
			engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), viewportWidth, viewportHeight);
			engine.getViewport().setCenter(center);
		}
	}

	private static boolean isPlayerPlaying(CombatEngineAPI engine) {
		ShipAPI player = engine.getPlayerShip();
		return player.isAlive() && !player.isShuttlePod();
	}
}
