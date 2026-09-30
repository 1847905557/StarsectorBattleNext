package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.missions.BattleNext_MissionRestrictedUnlock;
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
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.List;

public class BattleNext_TitleEntry extends BaseEveryFrameCombatPlugin {

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
	private static final String MISSION_WIDGET_CLASS = "com.fs.starfarer.title.Object.E";
	private static final String MISSION_REFIT_PROVIDER_CLASS = "com.fs.starfarer.title.Object.ooOO";
	private static final String TITLE_DIALOG_CLASS = "com.fs.starfarer.ui.oo0O";
	private static final String REFIT_TAB_CLASS = "com.fs.starfarer.coreui.refit.void";
	private static final String REFIT_SCREEN_CLASS = "com.fs.starfarer.coreui.refit.U";
	private static final String REFIT_TAB_CONTENT_FIELD = "\u00d200000";
	private final BattleNext_TitleModalDialog titleModal = new BattleNext_TitleModalDialog();

	private CombatEngineAPI engine;
	private CustomPanelAPI configurationPanel;
	private BattleNext_ConfigurationPanelPlugin configurationPlugin;
	private CustomPanelAPI refitPanel;
	private CustomPanelAPI displayedPanel;

	private UIPanelAPI screenPanel;
	private Object refitDialog;
	private SpriteAPI entryIcon;
	private int backdropTexture;
	private UIBorderObject entryBorder;
	private float panelCooldown;
	private boolean hovered;
	private boolean mouseHeld;
	private boolean renderChecked;
	private boolean refitDetectionErrorLogged;
	private boolean missionSelectionErrorLogged;
	private boolean missionListClickPending;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		if (Global.getCurrentState() != GameState.TITLE || engine.isSimulation()) {
			BattleNext_MissionRestrictedUnlock.getInstance().forget();
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
			BattleNext_MissionRestrictedUnlock.getInstance().forget();
			closePanel();
			releaseBackdrop();
			if (engine != null) {
				engine.removePlugin(this);
			}
			return;
		}
		if (missionListClickPending) {
			missionListClickPending = false;
		} else {
			updateMissionRestrictedUnlock();
		}
		panelCooldown = Math.min(ENTRY_COOLDOWN_CAP, panelCooldown + amount);
		if (displayedPanel == null && titleModal.hasOwnedDialog()) titleModal.close();
		if (isRefitPanelDisplayed() && !isRefitDialogAttached(refitDialog)) {
			closePanel();
		}
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {

		if (!renderChecked) {
			Global.getLogger(BattleNext_TitleEntry.class).info("Title UI render: state="
					+ Global.getCurrentState() + ", icon=" + (entryIcon == null ? "missing" : "loaded"));
			renderChecked = true;
		}
		if (Global.getCurrentState() != GameState.TITLE || configurationPanel == null) {
			return;
		}
		if (displayedPanel != null) {
			captureBackdrop();
			return;
		}
		updateEntryInput();
		renderEntry();
	}

	@Override
	public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
		if (Global.getCurrentState() == GameState.TITLE && displayedPanel == null) {
			restoreBeforeMissionListClick(events);
		}
		if (Global.getCurrentState() != GameState.TITLE || configurationPanel == null || displayedPanel == null) {
			return;
		}
		if (screenPanel != null) screenPanel.bringComponentToTop(displayedPanel);
		if (displayedPanel == configurationPanel && configurationPlugin != null) {
			configurationPlugin.handleGlobalInput(events);
		}
		if (displayedPanel == null) return;
		if (isRefitPanelDisplayed()) {
			((BattleNext_RefitPanelPlugin) refitPanel.getPlugin()).handleGlobalInput(events);
		}
		if (displayedPanel == null) return;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				if (isRefitPanelDisplayed()) {
					((BattleNext_RefitPanelPlugin) refitPanel.getPlugin()).requestClose();
				} else {
					closePanel();
				}
				event.consume();
				return;
			}
		}
		consumeOutsideMouseInput(displayedPanel.getPosition(), events);
	}

	private void updateMissionRestrictedUnlock() {

		BattleNext_MissionRestrictedUnlock unlock = BattleNext_MissionRestrictedUnlock.getInstance();
		if (!unlock.isEnabled() && !unlock.hasPreview()) return;
		try {
			Object widget = missionWidget();
			Object detail = invokeNoArg(widget, "getMissionDetail");
			Object preview = invokeNoArg(detail, "getPreview");
			boolean visible = isMissionWidgetVisible(widget);
			unlock.observe(preview, visible, !visible && findMissionRefitDialog() != null);
		} catch (Throwable exception) {
			unlock.restore();
			logMissionSelectionFailure(exception);
		}
	}

	private void restoreBeforeMissionListClick(List<InputEventAPI> events) {

		BattleNext_MissionRestrictedUnlock unlock = BattleNext_MissionRestrictedUnlock.getInstance();
		if (!unlock.isEnabled()) return;
		try {
			Object widget = missionWidget();
			if (!isMissionWidgetVisible(widget)) return;
			Object list = invokeNoArg(widget, "getMissionList");
			PositionAPI bounds = ((UIComponentAPI) list).getPosition();
			if (isMissionListClick(bounds, events)) {
				unlock.restore();
				missionListClickPending = true;
			}
		} catch (Throwable exception) {
			unlock.restore();
			logMissionSelectionFailure(exception);
		}
	}

	static boolean isMissionListClick(PositionAPI bounds, List<InputEventAPI> events) {
		for (InputEventAPI event : events) {
			if (event.isConsumed() || !event.isLMBDownEvent() || !bounds.containsEvent(event)) continue;
			return true;
		}
		return false;
	}

	private boolean isMissionWidgetVisible(Object widget) throws Throwable {
		Object fader = invokeNoArg(widget, "getFader");
		float brightness = ((Number) invokeNoArg(fader, "getBrightness")).floatValue();
		return brightness > 0f && !(Boolean) invokeNoArg(fader, "isFadingOut");
	}

	private Object missionWidget() throws Throwable {

		for (Object child : childrenOf(screenPanel)) {
			if (MISSION_WIDGET_CLASS.equals(child.getClass().getName())) return child;
		}
		throw new IllegalStateException("Mission widget is unavailable");
	}

	private void logMissionSelectionFailure(Throwable exception) {
		if (missionSelectionErrorLogged) return;
		missionSelectionErrorLogged = true;
		Global.getLogger(BattleNext_TitleEntry.class).warn("Mission selection detection failed", exception);
	}

	static void consumeOutsideMouseInput(PositionAPI bounds, List<InputEventAPI> events) {
		for (InputEventAPI event : events) {
			if (event.isConsumed() || !event.isMouseEvent() || bounds.containsEvent(event)) continue;
			event.consume();
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
		CustomPanelAPI nextPanel;
		if (dialog != null) {
			createRefitPanel();
			if (refitPanel == null) return;
			Object refitScreen = selectedMissionRefitScreen(dialog);
			FleetMemberAPI member = null;
			if (refitScreen != null) {
				try {
					Object selected = invokeNoArg(refitScreen, "getMember");
					if (selected instanceof FleetMemberAPI) member = (FleetMemberAPI) selected;
				} catch (Throwable exception) {
					logRefitDetectionFailure(exception);
				}
			}
			((BattleNext_RefitPanelPlugin) refitPanel.getPlugin()).open(member, refitScreen);
			nextPanel = refitPanel;
		} else {
			configurationPlugin.open();
			nextPanel = configurationPanel;
		}
		titleModal.open(screenPanel, AppDriver.getInstance().getCurrentState());
		screenPanel.addComponent(nextPanel);
		displayedPanel = nextPanel;
		refitDialog = nextPanel == refitPanel ? dialog : null;
		panelCooldown = 0f;
	}

	private void closePanel() {
		if (displayedPanel == null && !titleModal.hasOwnedDialog()) return;
		if (isRefitPanelDisplayed()) {
			((BattleNext_RefitPanelPlugin) refitPanel.getPlugin()).close();
		}
		titleModal.close();
		if (screenPanel != null && displayedPanel != null) {
			screenPanel.removeComponent(displayedPanel);
		}
		displayedPanel = null;
		refitDialog = null;
		panelCooldown = 0f;
		mouseHeld = Mouse.isButtonDown(0);
	}

	private boolean isRefitPanelDisplayed() {
		return refitPanel != null && displayedPanel == refitPanel;
	}

	private void createRefitPanel() {
		if (refitPanel != null) return;
		float width = ShaderCore.getScreenWidth() * MANAGEMENT_PANEL_SCREEN_SHARE;
		float height = ShaderCore.getScreenHeight() * MANAGEMENT_PANEL_SCREEN_SHARE;
		BattleNext_RefitPanelPlugin plugin = new BattleNext_RefitPanelPlugin(this::closePanel);
		plugin.setBackdrop(backdropTexture, ShaderCore.getScreenFixU(), ShaderCore.getScreenFixV());
		refitPanel = Global.getSettings().createCustom(width, height, plugin);
		refitPanel.getPosition().inBL(ShaderCore.getScreenWidth() * MANAGEMENT_PANEL_EDGE_SHARE,
				ShaderCore.getScreenHeight() * MANAGEMENT_PANEL_EDGE_SHARE);
		plugin.init(refitPanel, width, height);
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

	private Object selectedMissionRefitScreen(Object dialog) {
		try {
			for (Object core : childrenOf(dialog)) {
				if (!MISSION_CORE_UI_CLASS.equals(core.getClass().getName())) continue;
				Object tabData = invokeNoArg(core, "getCurrentTabData");
				if (tabData == null) return null;
				Object refitTab = MethodHandles.publicLookup().findGetter(tabData.getClass(),
						REFIT_TAB_CONTENT_FIELD, screenPanel.getClass()).invoke(tabData);
				if (refitTab == null || !REFIT_TAB_CLASS.equals(refitTab.getClass().getName())) return null;
				Object refitScreen = invokeNoArg(refitTab, "getRefitPanel");
				if (refitScreen == null || !REFIT_SCREEN_CLASS.equals(refitScreen.getClass().getName())) return null;
				return refitScreen;
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

	static Object invokeNoArg(Object target, String name) throws Throwable {
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
		Global.getLogger(BattleNext_TitleEntry.class).warn("Mission refit UI detection failed", exception);
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
				Global.getLogger(BattleNext_TitleEntry.class).warn("Title screen panel reflection is unavailable");
				return null;
			}
			Object state = AppDriver.getInstance().getCurrentState();
			for (Object method : state.getClass().getMethods()) {
				if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("getScreenPanel")) continue;

				Object result = BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, state);
				return result instanceof UIPanelAPI ? (UIPanelAPI) result : null;
			}
			Global.getLogger(BattleNext_TitleEntry.class).warn("Title screen panel getter is unavailable");
			return null;
		} catch (Throwable exception) {
			CommonUtil.printThrowable(BattleNext_TitleEntry.class, "Title screen panel lookup failed: ", exception);
			return null;
		}
	}
}
