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
	private final Vector2f target = new Vector2f();

	public BattleNext_PresentationPlugin(String playerName, String enemyName) {
		this.playerName = playerName;
		this.enemyName = enemyName;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		viewportWidth = engine.getViewport().getVisibleWidth();
		viewportHeight = engine.getViewport().getVisibleHeight();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || engine.isCombatOver()) return;
		if (!versusShown && engine.getTotalElapsedTime(false) >= 0.5f) {
			versusShown = true;
			engine.getCombatUI().addMessage(0, playerName + "  VS  " + enemyName);
		}
		if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) engine.getTimeMult().modifyMult(ID, 4f);
		else if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) engine.getTimeMult().modifyMult(ID, 1f);
		else engine.getTimeMult().modifyMult(ID, 1.5f);
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
			center.x += target.x * amount * 0.5f;
			center.y += target.y * amount * 0.5f;
			center.x = Math.max(-engine.getMapWidth() / 2f, Math.min(engine.getMapWidth() / 2f, center.x));
			center.y = Math.max(-engine.getMapHeight() / 2f, Math.min(engine.getMapHeight() / 2f, center.y));
			if (zoomIn) {
				viewportWidth = Math.max(400f, viewportWidth - amount * 1800f);
				viewportHeight = Math.max(300f, viewportHeight - amount * 1800f);
			} else if (zoomOut) {
				viewportWidth = Math.min(engine.getMapWidth(), viewportWidth + amount * 1800f);
				viewportHeight = Math.min(engine.getMapHeight(), viewportHeight + amount * 1800f);
			}
			engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), viewportWidth, viewportHeight);
			engine.getViewport().setCenter(center);
		}
	}
}
