package data.scripts.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public final class BattleNext_PresentationPlugin extends BaseEveryFrameCombatPlugin {

	private static final String ID = "BattleNext_PresentationPlugin";
	private static final float DEFAULT_TIME_MULT = 1.5f;
	private static final float BOOST_TIME_MULT = 4f;
	private static final float NORMAL_TIME_MULT = 1f;
	private static final float VERSUS_DELAY = 0.5f;
	private static final float CAMERA_MOUSE_SPEED = 1f;
	private static final float CAMERA_ZOOM_STEP = 3f;
	private static final float CAMERA_MIN_ZOOM = 0.5f;
	private static final float CAMERA_MAX_ZOOM = 10f;

	private final String playerName;
	private final String enemyName;
	private CombatEngineAPI engine;
	private boolean versusShown;
	private boolean cameraToggle;
	private boolean externalCamera;
	private boolean zoomIn;
	private boolean zoomOut;
	private float viewportWidth;
	private float viewportHeight;
	private float screenWidth;
	private float screenHeight;

	private final Vector2f target = new Vector2f();

	public BattleNext_PresentationPlugin(String playerName, String enemyName) {
		this.playerName = playerName;
		this.enemyName = enemyName;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		screenWidth = Global.getSettings().getScreenWidth();
		screenHeight = Global.getSettings().getScreenHeight();
		viewportWidth = engine.getViewport().getVisibleWidth();
		viewportHeight = engine.getViewport().getVisibleHeight();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || engine.isCombatOver()) return;
		if (!versusShown && engine.getTotalElapsedTime(false) >= VERSUS_DELAY) {
			versusShown = true;
			engine.getCombatUI().addMessage(0, playerName + "  VS  " + enemyName);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) engine.getTimeMult().modifyMult(ID, BOOST_TIME_MULT);
		else if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) engine.getTimeMult().modifyMult(ID, NORMAL_TIME_MULT);
		else engine.getTimeMult().modifyMult(ID, DEFAULT_TIME_MULT);
		if (Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) cameraToggle = true;
		else if (cameraToggle) {
			cameraToggle = false;
			externalCamera = !externalCamera;
			engine.getViewport().setExternalControl(externalCamera);
			engine.getCombatUI().addMessage(0, externalCamera ? "Free camera enabled" : "Free camera disabled");
		}
		for (InputEventAPI event : events) {
			if (event.isLMBDownEvent()) zoomIn = true;
			if (event.isLMBUpEvent()) zoomIn = false;
			if (event.isRMBDownEvent()) zoomOut = true;
			if (event.isRMBUpEvent()) zoomOut = false;
			if (event.isMouseMoveEvent() && externalCamera) {
				target.set(event.getX() - Global.getSettings().getScreenWidth() / 2f,
						event.getY() - Global.getSettings().getScreenHeight() / 2f);
			}
			if (event.isAltDown() && externalCamera) {
				externalCamera = false;
				engine.getViewport().setExternalControl(false);
			}
		}
		if (externalCamera) {
			Vector2f center = new Vector2f(engine.getViewport().getCenter());
			center.x += target.x * amount * CAMERA_MOUSE_SPEED;
			center.y += target.y * amount * CAMERA_MOUSE_SPEED;
			center.x = Math.max(-engine.getMapWidth() / 2f, Math.min(engine.getMapWidth() / 2f, center.x));
			center.y = Math.max(-engine.getMapHeight() / 2f, Math.min(engine.getMapHeight() / 2f, center.y));
			if (zoomIn) {
				viewportWidth = Math.max(screenWidth * CAMERA_MIN_ZOOM, viewportWidth - screenWidth * amount * CAMERA_ZOOM_STEP);
				viewportHeight = Math.max(screenHeight * CAMERA_MIN_ZOOM, viewportHeight - screenHeight * amount * CAMERA_ZOOM_STEP);
			} else if (zoomOut) {
				viewportWidth = Math.min(screenWidth * CAMERA_MAX_ZOOM, viewportWidth + screenWidth * amount * CAMERA_ZOOM_STEP);
				viewportHeight = Math.min(screenHeight * CAMERA_MAX_ZOOM, viewportHeight + screenHeight * amount * CAMERA_ZOOM_STEP);
			}
			engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), viewportWidth, viewportHeight);
			engine.getViewport().setCenter(center);
		}
	}
}
