package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;

public abstract class BattleNext_BasePanelPlugin extends BaseCustomUIPanelPlugin {

	protected static final float BORDER_INSET = BattleNext_PageLayout.SECTION_GAP;

	protected CustomPanelAPI panel;
	private UIBorderObject border;
	private float borderX;
	private float borderY;
	private int backdropTexture;
	private float backdropU;
	private float backdropV;

	protected final void initFrame(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		border = new UIBorderObject(false, true);
		borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - BORDER_INSET;
		borderY = (ShaderCore.getScreenHeight() - height) * 0.5f - BORDER_INSET;
		border.setSize(width + BORDER_INSET * 2f, height + BORDER_INSET * 2f);
	}

	public final void setBackdrop(int texture, float u, float v) {
		backdropTexture = texture;
		backdropU = u;
		backdropV = v;
	}

	@Override
	public final void renderBelow(float alphaMult) {
		if (panel == null) return;
		if (backdropTexture != 0) {
			BattleNext_GeometryRenderer.texturedFullscreen(backdropTexture, backdropU, backdropV,
					ShaderCore.getScreenScaleWidth(), ShaderCore.getScreenScaleHeight());
		}
		border.setAlpha(alphaMult);
		border.render(borderX, borderY);
	}

}
