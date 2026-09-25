package data.scripts.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.boxutil.manager.ShaderCore;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class BattleNext_BasePanelPlugin extends BaseCustomUIPanelPlugin {

	private final BattleNext_UiManager uiManager;
	private final UIBorderObject border = new UIBorderObject(false, true);

	private CustomPanelAPI panel;
	private float borderX;
	private float borderY;
	private int backdropTexture;
	private float backdropU;
	private float backdropV;

	public BattleNext_BasePanelPlugin(Runnable closeAction) {
		uiManager = new BattleNext_UiManager(closeAction);
	}

	public void init(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		borderX = (ShaderCore.getScreenWidth() - width) * 0.5f - 8f;
		borderY = (ShaderCore.getScreenHeight() - height) * 0.5f - 8f;
		border.setSize(width + 16f, height + 16f);
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
				GL11.glPushAttrib(GL11.GL_TRANSFORM_BIT | GL11.GL_VIEWPORT_BIT | GL11.GL_ENABLE_BIT);
				GL11.glViewport(0, 0, ShaderCore.getScreenScaleWidth(), ShaderCore.getScreenScaleHeight());
				GL11.glMatrixMode(GL11.GL_PROJECTION);
				GL11.glPushMatrix();
				GL11.glLoadIdentity();
				GL11.glMatrixMode(GL11.GL_MODELVIEW);
				GL11.glPushMatrix();
				GL11.glLoadIdentity();
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTexture);
				GL11.glColor4f(0.5f, 0.5f, 0.5f, 1f);
				GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
				GL11.glTexCoord2f(0f, 0f);
				GL11.glVertex2f(-1f, -1f);
				GL11.glTexCoord2f(0f, backdropV);
				GL11.glVertex2f(-1f, 1f);
				GL11.glTexCoord2f(backdropU, 0f);
				GL11.glVertex2f(1f, -1f);
				GL11.glTexCoord2f(backdropU, backdropV);
				GL11.glVertex2f(1f, 1f);
				GL11.glEnd();
				GL11.glPopMatrix();
				GL11.glMatrixMode(GL11.GL_PROJECTION);
				GL11.glPopMatrix();
				GL11.glPopAttrib();
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
	}

	@Override
	public void buttonPressed(Object buttonId) {
		uiManager.buttonPressed(buttonId);
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return uiManager.handleGlobalInput(events);
	}
}
