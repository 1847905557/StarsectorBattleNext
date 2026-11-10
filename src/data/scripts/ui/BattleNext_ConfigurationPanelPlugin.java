package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

public class BattleNext_ConfigurationPanelPlugin extends BattleNext_BasePanelPlugin {

	private final BattleNext_UiManager uiManager;

	public BattleNext_ConfigurationPanelPlugin(Runnable closeAction) {
		uiManager = new BattleNext_UiManager(closeAction);
	}

	public void init(CustomPanelAPI panel, float width, float height) {
		initFrame(panel, width, height);

		uiManager.attach(panel);
	}

	public void open() {
		uiManager.open();
	}

	@Override
	public void positionChanged(PositionAPI position) {

	}

	@Override
	public void advance(float amount) {
		uiManager.advance(amount);
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		uiManager.processInput(events);

		// Prevent unhandled events from reaching the underlying title controls.
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			event.consume();
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		uiManager.buttonPressed(buttonId);
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return uiManager.handleGlobalInput(events);
	}
}
