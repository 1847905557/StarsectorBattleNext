package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;

import java.util.List;

public class BattleNext_EmptyPanelPlugin extends BaseCustomUIPanelPlugin {

	private static final float PANEL_BORDER_INSET = 8f;
	private static final float SHELL_OUTER_INSET = 8f;
	private static final float CLOSE_MAX_WIDTH = 180f;
	private static final float CLOSE_WIDTH_SHARE = 0.3f;

	private final Runnable closeAction;

	private UIBorderObject border;
	private CustomPanelAPI panel;
	private float borderX;
	private float borderY;
	private int backdropTexture;
	private float backdropU;
	private float backdropV;

	public BattleNext_EmptyPanelPlugin(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void init(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		border = new UIBorderObject(false, true);
		borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - PANEL_BORDER_INSET;
		borderY = (ShaderCore.getScreenHeight() - height) * 0.5f - PANEL_BORDER_INSET;
		border.setSize(width + PANEL_BORDER_INSET * 2f, height + PANEL_BORDER_INSET * 2f);
		float footerWidth = width - SHELL_OUTER_INSET * 2f;
		float closeWidth = Math.min(CLOSE_MAX_WIDTH, footerWidth * CLOSE_WIDTH_SHARE);
		UIRect closeRect = new UIRect(SHELL_OUTER_INSET + footerWidth - closeWidth,
				height - SHELL_OUTER_INSET - BattleNext_PageLayout.FOOTER_HEIGHT,
				closeWidth, BattleNext_PageLayout.FOOTER_HEIGHT);
		BattleNext_PageLayout.fixedButton(panel, closeRect,
				BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_").get("close"),
				"close", CutStyle.BOTTOM, false);
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
		if (panel == null) return;
		if (backdropTexture != 0) {
			BattleNext_GeometryRenderer.texturedFullscreen(backdropTexture, backdropU, backdropV,
					ShaderCore.getScreenScaleWidth(), ShaderCore.getScreenScaleHeight());
		}
		border.setAlpha(alphaMult);
		border.render(borderX, borderY);
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			event.consume();
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("close".equals(buttonId)) closeAction.run();
	}
}
