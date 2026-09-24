package data.scripts.combat;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.util.List;

public final class BattleNext_PresentationPlugin extends BaseEveryFrameCombatPlugin {
	private static final String ID = "BattleNext_PresentationPlugin";
	private final String playerName;
	private final String enemyName;
	private CombatEngineAPI engine;
	private boolean versusShown;
	private boolean cameraToggle;
	private boolean externalCamera;

	public BattleNext_PresentationPlugin(String playerName, String enemyName) {
		this.playerName = playerName;
		this.enemyName = enemyName;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
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
			if (event.isAltDown() && externalCamera) {
				externalCamera = false;
				engine.getViewport().setExternalControl(false);
			}
		}
	}
}
