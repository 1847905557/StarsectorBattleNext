package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.state.AppDriver;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.backends.reflect.BUtil_RefMethod;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.boxutil.util.CommonUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.List;

public class BattleNext_TitlePanel extends BaseEveryFrameCombatPlugin {

	private static final float ENTRY_LEFT_INSET = 5f;
	static final float ENTRY_BORDER_WIDTH = 8f;
	static final float ENTRY_WIDTH = 96f;
	static final float ENTRY_HEIGHT = 24f;
	private static final float ENTRY_BOTTOM_OFFSET = 133f;
	private static final float MANAGEMENT_PANEL_SCREEN_SHARE = 0.8f;
	private static final float MANAGEMENT_PANEL_EDGE_SHARE = 0.1f;
	private static final float ENTRY_CLICK_COOLDOWN = 0.1f;
	private static final float ENTRY_COOLDOWN_CAP = 0.5f;
	private static final String MISSION_CORE_UI_CLASS = "com.fs.starfarer.coreui.returnnew";
	private static final String MISSION_REFIT_PROVIDER_CLASS = "com.fs.starfarer.title.Object.ooOO";
	private static final String TITLE_DIALOG_CLASS = "com.fs.starfarer.ui.oo0O";

	private CombatEngineAPI engine;
	private CustomPanelAPI configurationPanel;
	private BattleNext_ConfigurationPanelPlugin configurationPlugin;
	private CustomPanelAPI emptyPanel;
	private CustomPanelAPI displayedPanel;

	private UIPanelAPI screenPanel;
	private Object refitDialog;
	private SpriteAPI entryIcon;
	private int backdropTexture;
	private UIBorderObject entryBorder;
	private float panelCooldown;
	private boolean panelVisible;
	private boolean hovered;
	private boolean mouseHeld;
	private boolean renderChecked;
	private boolean refitDetectionErrorLogged;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		if (Global.getCurrentState() != GameState.TITLE || engine.isSimulation()) {
			engine.removePlugin(this);
			return;
		}
		BoxUtilModPlugin.initLater();
		entryIcon = Global.getSettings().getSprite("icon", "BattleNext_Icon");
		loadBackdrop();
		entryBorder = new UIBorderObject(false, true);
		entryBorder.setSize(ENTRY_WIDTH + ENTRY_BORDER_WIDTH * 2f, ENTRY_HEIGHT + ENTRY_BORDER_WIDTH * 2f);
		createConfigurationPanel();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || Global.getCurrentState() != GameState.TITLE || engine.isSimulation()) {
			closePanel();
			releaseBackdrop();
			if (engine != null) {
				engine.removePlugin(this);
			}
			return;
		}
		panelCooldown = Math.min(ENTRY_COOLDOWN_CAP, panelCooldown + amount);
		if (panelVisible && displayedPanel == emptyPanel && !isRefitDialogAttached(refitDialog)) {
			closePanel();
		}
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {

		if (!renderChecked) {
			Global.getLogger(BattleNext_TitlePanel.class).info("Title UI render: state="
					+ Global.getCurrentState() + ", icon=" + (entryIcon == null ? "missing" : "loaded"));
			renderChecked = true;
		}
		if (Global.getCurrentState() != GameState.TITLE || configurationPanel == null) {
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
		if (Global.getCurrentState() != GameState.TITLE || configurationPanel == null) {
			return;
		}
		if (displayedPanel == configurationPanel && configurationPlugin != null && configurationPlugin.handleGlobalInput(events)) {
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

	private void releaseBackdrop() {
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

	private void createConfigurationPanel() {
		if (configurationPanel != null) {
			return;
		}
		screenPanel = getScreenPanel();
		if (screenPanel == null) {
			return;
		}
		float width = ShaderCore.getScreenWidth() * MANAGEMENT_PANEL_SCREEN_SHARE;
		float height = ShaderCore.getScreenHeight() * MANAGEMENT_PANEL_SCREEN_SHARE;
		configurationPlugin = new BattleNext_ConfigurationPanelPlugin(this::closePanel);
		configurationPlugin.setBackdrop(backdropTexture, ShaderCore.getScreenFixU(), ShaderCore.getScreenFixV());
		configurationPanel = Global.getSettings().createCustom(width, height, configurationPlugin);
		configurationPanel.getPosition().inBL(ShaderCore.getScreenWidth() * MANAGEMENT_PANEL_EDGE_SHARE,
				ShaderCore.getScreenHeight() * MANAGEMENT_PANEL_EDGE_SHARE);
		configurationPlugin.init(configurationPanel, width, height);
	}

	private void openPanel() {
		if (configurationPanel == null) {
			createConfigurationPanel();
		}
		if (configurationPanel == null || screenPanel == null) {
			return;
		}
		Object dialog = findMissionRefitDialog();
		if (dialog != null) {
			createEmptyPanel();
			if (emptyPanel == null) return;
			refitDialog = dialog;
			displayedPanel = emptyPanel;
		} else {
			configurationPlugin.open();
			displayedPanel = configurationPanel;
		}
		screenPanel.addComponent(displayedPanel);
		panelVisible = true;
		panelCooldown = 0f;
	}

	private void closePanel() {
		if (screenPanel != null && displayedPanel != null) {
			screenPanel.removeComponent(displayedPanel);
		}
		displayedPanel = null;
		refitDialog = null;
		panelVisible = false;
		panelCooldown = 0f;
		mouseHeld = Mouse.isButtonDown(0);
	}

	private void createEmptyPanel() {
		if (emptyPanel != null) return;
		float width = ShaderCore.getScreenWidth() * MANAGEMENT_PANEL_SCREEN_SHARE;
		float height = ShaderCore.getScreenHeight() * MANAGEMENT_PANEL_SCREEN_SHARE;
		BattleNext_EmptyPanelPlugin emptyPlugin = new BattleNext_EmptyPanelPlugin(this::closePanel);
		emptyPlugin.setBackdrop(backdropTexture, ShaderCore.getScreenFixU(), ShaderCore.getScreenFixV());
		emptyPanel = Global.getSettings().createCustom(width, height, emptyPlugin);
		emptyPanel.getPosition().inBL(ShaderCore.getScreenWidth() * MANAGEMENT_PANEL_EDGE_SHARE,
				ShaderCore.getScreenHeight() * MANAGEMENT_PANEL_EDGE_SHARE);
		emptyPlugin.init(emptyPanel, width, height);
	}

	private Object findMissionRefitDialog() {
		try {
			for (Object dialog : childrenOf(screenPanel)) {
				if (!hasDialogSuperclass(dialog) || !isRefitDialogAttached(dialog)) continue;
				for (Object child : childrenOf(dialog)) {
					if (!MISSION_CORE_UI_CLASS.equals(child.getClass().getName())) continue;
					Object provider = invokeNoArg(child, "getPlugin");
					if (matchesMissionRefit(provider == null ? null : provider.getClass().getName(),
							hasDialogSuperclass(dialog))) return dialog;
				}
			}
			return null;
		} catch (Throwable exception) {
			logRefitDetectionFailure(exception);
			return null;
		}
	}

	static boolean matchesMissionRefit(String providerClassName, boolean dialogTypeMatches) {
		return MISSION_REFIT_PROVIDER_CLASS.equals(providerClassName) && dialogTypeMatches;
	}

	private boolean hasDialogSuperclass(Object dialog) {
		Class<?> type = dialog.getClass();
		while (type != null) {
			if (TITLE_DIALOG_CLASS.equals(type.getName())) return true;
			type = type.getSuperclass();
		}
		return false;
	}

	private List<?> childrenOf(Object panel) throws Throwable {
		return (List<?>) invokeNoArg(panel, "getChildrenCopy");
	}

	private Object invokeNoArg(Object target, String name) throws Throwable {
		for (Object method : target.getClass().getMethods()) {
			if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) continue;
			return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, target);
		}
		throw new NoSuchMethodException(name);
	}

	private boolean isRefitDialogAttached(Object dialog) {
		if (dialog == null || screenPanel == null) return false;
		try {
			Object parent = invokeNoArg(dialog, "getParent");
			return parent == screenPanel;
		} catch (Throwable exception) {
			logRefitDetectionFailure(exception);
			return false;
		}
	}

	private void logRefitDetectionFailure(Throwable exception) {
		if (refitDetectionErrorLogged) return;
		refitDetectionErrorLogged = true;
		Global.getLogger(BattleNext_TitlePanel.class).warn("Mission refit UI detection failed", exception);
	}

	private void renderEntry() {
		entryBorder.render(ENTRY_LEFT_INSET, ENTRY_BOTTOM_OFFSET);
		renderIcon(entryIcon, hovered, ENTRY_LEFT_INSET + ENTRY_BORDER_WIDTH,
				ENTRY_BOTTOM_OFFSET + ENTRY_BORDER_WIDTH, BattleNext_UiTheme.PANEL, BattleNext_UiTheme.ACCENT);
	}

	static void renderIcon(SpriteAPI icon, boolean hovered, float x, float y, Color muted, Color accent) {
		Color previousColor = icon.getColor();
		float previousWidth = icon.getWidth();
		float previousHeight = icon.getHeight();
		try {
			icon.setColor(iconTint(hovered, muted, accent));
			icon.setSize(ENTRY_WIDTH, ENTRY_HEIGHT);
			icon.render(x, y);
		} finally {
			icon.setColor(previousColor);
			icon.setSize(previousWidth, previousHeight);
		}
	}

	static Color iconTint(boolean hovered, Color muted, Color accent) {
		return hovered ? accent : muted;
	}

	static boolean containsEntry(float x, float y) {
		return x >= ENTRY_LEFT_INSET && x <= ENTRY_LEFT_INSET + ENTRY_WIDTH + ENTRY_BORDER_WIDTH * 2f
				&& y >= ENTRY_BOTTOM_OFFSET && y <= ENTRY_BOTTOM_OFFSET + ENTRY_HEIGHT + ENTRY_BORDER_WIDTH * 2f;
	}

	private void updateEntryInput() {
		float scale = Global.getSettings().getScreenScaleMult();
		float mouseX = Mouse.getX() / scale;
		float mouseY = Mouse.getY() / scale;
		boolean inside = containsEntry(mouseX, mouseY);
		if (inside != hovered) {
			hovered = inside;
			if (hovered) Global.getSoundPlayer().playUISound(BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID, 1f, 1f);
		}
		boolean down = Mouse.isButtonDown(0);
		if (inside && down && !mouseHeld && panelCooldown > ENTRY_CLICK_COOLDOWN) {
			Global.getSoundPlayer().playUISound(BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID, 1f, 1f);
			openPanel();
		}
		mouseHeld = down;
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
