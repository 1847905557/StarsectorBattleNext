package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.state.AppDriver;
import data.scripts.ui.widget.BattleNext_BasePanelPlugin;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.backends.reflect.BUtil_RefMethod;
import org.boxutil.config.BoxConfigGUI;
import org.boxutil.manager.ShaderCore;
import org.boxutil.util.CommonUtil;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.util.List;

public class BattleNext_TitlePanel extends BaseEveryFrameCombatPlugin {

	private static final float SIMPLE_PADDING = 5f;
	private static final float BORDER_WIDTH = 8f;
	private static final float ENTRY_WIDTH = 48f;
	private static final float ENTRY_HEIGHT = 24f;
	private static final float ENTRY_BOTTOM = 133f;
	private static final float EXPANDED_SIZE = 112f;
	private static final float PANEL_SCALE = 0.8f;
	private static final float PANEL_MARGIN = 0.1f;

	private CombatEngineAPI engine;
	private CustomPanelAPI mainPanel;
	private BattleNext_BasePanelPlugin mainPlugin;
	private UIPanelAPI screenPanel;
	private final int[] iconTextures = new int[2];
	private int backdropTexture;
	private UIBorderObject entryBorder;
	private float panelCooldown;
	private boolean panelVisible;
	private boolean hovered;
	private boolean mouseHeld;
	private boolean renderChecked;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		if (Global.getCurrentState() != GameState.TITLE || engine.isSimulation()) {
			engine.removePlugin(this);
			return;
		}
		BoxUtilModPlugin.initLater();
		loadIcons();
		loadBackdrop();
		entryBorder = new UIBorderObject(false, true);
		entryBorder.setSize(ENTRY_WIDTH + BORDER_WIDTH * 2f, ENTRY_HEIGHT + BORDER_WIDTH * 2f);
		createMainPanel();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || Global.getCurrentState() != GameState.TITLE || engine.isSimulation()) {
			closePanel();
			releaseIcons();
			if (engine != null) {
				engine.removePlugin(this);
			}
			return;
		}
		panelCooldown = Math.min(0.5f, panelCooldown + amount);
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {
		boolean panelEmpty = isTitlePanelEmpty();
		if (!renderChecked) {
			Global.getLogger(BattleNext_TitlePanel.class).info("Title UI render: state="
					+ Global.getCurrentState() + ", dialogEmpty=" + panelEmpty + ", icon=" + iconTextures[0]);
			renderChecked = true;
		}
		if (Global.getCurrentState() != GameState.TITLE || !panelEmpty) {
			return;
		}
		if (panelVisible) {
			captureBackdrop();
			return;
		}
		updateEntryInput();
		renderEntry();
	}

	@Override
	public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
		if (Global.getCurrentState() != GameState.TITLE || !isTitlePanelEmpty()) {
			return;
		}
		if (panelVisible && mainPlugin != null && mainPlugin.handleGlobalInput(events)) {
			return;
		}
		if (!panelVisible) {
			return;
		}
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				closePanel();
				event.consume();
				return;
			}
		}
	}

	private void loadIcons() {
		if (iconTextures[0] != 0) {
			return;
		}
		String[] paths = {"graphics/ui/BattleNext_FeaturesIcon.png", "graphics/ui/BattleNext_ConfigButton.png"};
		for (int index = 0; index < paths.length; index++) {
			int texture = GL11.glGenTextures();
			if (texture == 0) continue;
			Pair<int[], ByteBuffer> data = CommonUtil.getRawPixels(paths[index], 4);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
					data.one[0], data.one[1], 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data.two);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			iconTextures[index] = texture;
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private void releaseIcons() {
		for (int index = 0; index < iconTextures.length; index++) {
			if (iconTextures[index] == 0) continue;
			GL11.glDeleteTextures(iconTextures[index]);
			iconTextures[index] = 0;
		}
		if (backdropTexture != 0) {
			GL11.glDeleteTextures(backdropTexture);
			backdropTexture = 0;
		}
	}

	private void loadBackdrop() {
		if (backdropTexture != 0) return;
		backdropTexture = GL11.glGenTextures();
		if (backdropTexture == 0) return;
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTexture);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ShaderCore.getScreenFixWidth(),
				ShaderCore.getScreenFixHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private void captureBackdrop() {
		if (backdropTexture == 0) return;
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTexture);
		GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
				ShaderCore.getScreenScaleWidth(), ShaderCore.getScreenScaleHeight());
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private void createMainPanel() {
		if (mainPanel != null) {
			return;
		}
		screenPanel = getScreenPanel();
		if (screenPanel == null) {
			return;
		}
		float width = ShaderCore.getScreenWidth() * PANEL_SCALE;
		float height = ShaderCore.getScreenHeight() * PANEL_SCALE;
		mainPlugin = new BattleNext_BasePanelPlugin(this::closePanel);
		mainPlugin.setBackdrop(backdropTexture, ShaderCore.getScreenFixU(), ShaderCore.getScreenFixV());
		mainPanel = Global.getSettings().createCustom(width, height, mainPlugin);
		mainPanel.getPosition().inBL(ShaderCore.getScreenWidth() * PANEL_MARGIN, ShaderCore.getScreenHeight() * PANEL_MARGIN);
		mainPlugin.init(mainPanel, width, height);
	}

	private void openPanel() {
		if (mainPanel == null) {
			createMainPanel();
		}
		if (mainPanel == null || screenPanel == null) {
			return;
		}
		mainPlugin.open();
		screenPanel.addComponent(mainPanel);
		panelVisible = true;
		panelCooldown = 0f;
	}

	private void closePanel() {
		if (screenPanel != null && mainPanel != null) {
			screenPanel.removeComponent(mainPanel);
		}
		panelVisible = false;
		panelCooldown = 0f;
		mouseHeld = Mouse.isButtonDown(0);
	}

	private void renderEntry() {
		entryBorder.render(SIMPLE_PADDING, ENTRY_BOTTOM);
		float x = hovered ? SIMPLE_PADDING + 12f : SIMPLE_PADDING + BORDER_WIDTH;
		float y = hovered ? ENTRY_BOTTOM + 12f : ENTRY_BOTTOM + BORDER_WIDTH;
		float width = hovered ? 88f : ENTRY_WIDTH;
		float height = hovered ? 88f : ENTRY_HEIGHT;
		GL11.glColor4f(1f, 1f, 1f, 1f);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, iconTextures[hovered ? 1 : 0]);
		GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
		GL11.glTexCoord2f(0f, 0f);
		GL11.glVertex2f(x, y);
		GL11.glTexCoord2f(1f, 0f);
		GL11.glVertex2f(x + width, y);
		GL11.glTexCoord2f(0f, 1f);
		GL11.glVertex2f(x, y + height);
		GL11.glTexCoord2f(1f, 1f);
		GL11.glVertex2f(x + width, y + height);
		GL11.glEnd();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	private void updateEntryInput() {
		float scale = Global.getSettings().getScreenScaleMult();
		float mouseX = Mouse.getX() / scale;
		float mouseY = Mouse.getY() / scale;
		float x = SIMPLE_PADDING;
		float y = ENTRY_BOTTOM;
		float sizeX = hovered ? EXPANDED_SIZE : ENTRY_WIDTH + BORDER_WIDTH * 2f;
		float sizeY = hovered ? EXPANDED_SIZE : ENTRY_HEIGHT + BORDER_WIDTH * 2f;
		boolean inside = mouseX >= x && mouseX <= x + sizeX && mouseY >= y && mouseY <= y + sizeY;
		if (inside != hovered) {
			hovered = inside;
			entryBorder.setSize(hovered ? EXPANDED_SIZE : ENTRY_WIDTH + BORDER_WIDTH * 2f,
					hovered ? EXPANDED_SIZE : ENTRY_HEIGHT + BORDER_WIDTH * 2f);
			if (hovered) Global.getSoundPlayer().playUISound("BUtil_button_in", 1f, 1f);
		}
		boolean down = Mouse.isButtonDown(0);
		if (inside && down && !mouseHeld && panelCooldown > 0.1f) {
			Global.getSoundPlayer().playUISound("BUtil_button_down", 1f, 1f);
			openPanel();
		}
		mouseHeld = down;
	}

	private boolean isTitlePanelEmpty() {
		return BoxConfigGUI.isTitlePanelEmpty();
	}

	private UIPanelAPI getScreenPanel() {
		try {
			if (!BUtil_RefMethod.VALID) {
				Global.getLogger(BattleNext_TitlePanel.class).warn("Title screen panel reflection is unavailable");
				return null;
			}
			Object state = AppDriver.getInstance().getCurrentState();
			for (Object method : state.getClass().getMethods()) {
				if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("getScreenPanel")) continue;

				Object result = BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, state);
				return result instanceof UIPanelAPI ? (UIPanelAPI) result : null;
			}
			Global.getLogger(BattleNext_TitlePanel.class).warn("Title screen panel getter is unavailable");
			return null;
		} catch (Throwable exception) {
			CommonUtil.printThrowable(BattleNext_TitlePanel.class, "Title screen panel lookup failed: ", exception);
			return null;
		}
	}
}
