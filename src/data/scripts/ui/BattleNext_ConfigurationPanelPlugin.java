package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.boxutil.manager.ShaderCore;

import java.util.List;

public class BattleNext_ConfigurationPanelPlugin extends BaseCustomUIPanelPlugin {

	private static final float PANEL_BORDER_INSET = 8f;

	private final BattleNext_UiManager uiManager;
	private final UIBorderObject border = new UIBorderObject(false, true);

	private CustomPanelAPI panel;
	private float borderX;
	private float borderY;
	private int backdropTexture;
	private float backdropU;
	private float backdropV;

	public BattleNext_ConfigurationPanelPlugin(Runnable closeAction) {
		uiManager = new BattleNext_UiManager(closeAction);
	}

	public void init(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - PANEL_BORDER_INSET;
		borderY = (ShaderCore.getScreenHeight() - height) * 0.5f - PANEL_BORDER_INSET;
		border.setSize(width + PANEL_BORDER_INSET * 2f, height + PANEL_BORDER_INSET * 2f);
		uiManager.attach(panel);
	}

	public void open() {
		uiManager.open();
	}

	public void setBackdrop(int texture, float u, float v) {
		backdropTexture = texture;
		backdropU = u;
		backdropV = v;
	}

	@Override
	public void positionChanged(PositionAPI position) {
	}

	@Override
	public void renderBelow(float alphaMult) {
		if (panel != null) {
			if (backdropTexture != 0) {
				BattleNext_GeometryRenderer.texturedFullscreen(backdropTexture, backdropU, backdropV,
						ShaderCore.getScreenScaleWidth(), ShaderCore.getScreenScaleHeight());
			}
			border.setAlpha(alphaMult);
			border.render(borderX, borderY);
		}
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
