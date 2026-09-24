package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.lwjgl.input.Keyboard;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class BattleNext_TitlePanel extends BaseEveryFrameCombatPlugin {

	private static final float ENTRY_WIDTH = 180f;
	private static final float ENTRY_HEIGHT = 48f;
	private static final float PANEL_WIDTH = 760f;
	private static final float PANEL_HEIGHT = 520f;
	private CombatEngineAPI engine;
	private UIPanelAPI screenPanel;
	private CustomPanelAPI entryPanel;
	private CustomPanelAPI managementPanel;
	private BattleNext_UiManager uiManager;
	private boolean managementVisible;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		if (!isTitleState() || engine.isSimulation()) {
			engine.removePlugin(this);
			return;
		}
		mountPanels();
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || !isTitleState() || engine.isSimulation()) {
			if (engine != null) {
				engine.removePlugin(this);
			}
			return;
		}
		if (isDialogOpen()) {
			return;
		}
		if (managementVisible && managementPanel == null) {
			mountPanels();
		}
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {
		if (isDialogOpen()) {
			return;
		}
	}

	@Override
	public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
		if (isDialogOpen()) {
			return;
		}
		if (managementVisible && uiManager != null && uiManager.handleGlobalInput(events)) {
			return;
		}
		for (InputEventAPI event : events) {
			if (event.isKeyboardEvent() && event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
				managementVisible = false;
				removeManagementPanel();
				event.consume();
				return;
			}
		}
	}

	private void mountPanels() {
		if (screenPanel == null) {
			screenPanel = findScreenPanel();
		}
		if (screenPanel == null) {
			return;
		}
		if (entryPanel == null) {
			entryPanel = createPanel(ENTRY_WIDTH, ENTRY_HEIGHT, new EntryPlugin());
			if (entryPanel != null) {
				addEntryContent();
				screenPanel.addComponent(entryPanel).inBR(24f, 24f);
			}
		}
		if (managementVisible && managementPanel == null) {
			uiManager = new BattleNext_UiManager(this::closeManagementPanel);
			managementPanel = createPanel(PANEL_WIDTH, PANEL_HEIGHT, uiManager);
			if (managementPanel != null) {
				uiManager.attach(managementPanel);
				screenPanel.addComponent(managementPanel).inMid();
			}
		}
	}

	private void addEntryContent() {
		TooltipMakerAPI content = entryPanel.createUIElement(ENTRY_WIDTH, ENTRY_HEIGHT, false);
		content.setButtonFontOrbitron20();
		content.addButton(text("BattleNext_title_open"), "open", ENTRY_WIDTH - 16f, 32f, 0f);
		entryPanel.addUIElement(content).inMid();
	}

	private CustomPanelAPI createPanel(float width, float height, CustomUIPanelPlugin plugin) {
		try {
			return Global.getSettings().createCustom(width, height, plugin);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private void removeManagementPanel() {
		if (screenPanel != null && managementPanel != null) {
			screenPanel.removeComponent(managementPanel);
		}
		managementPanel = null;
		uiManager = null;
	}

	private void closeManagementPanel() {
		managementVisible = false;
		removeManagementPanel();
	}

	private boolean isTitleState() {
		return Global.getCurrentState() == GameState.TITLE;
	}

	private boolean isDialogOpen() {
		try {
			Object state = Global.getCurrentState();
			Field field = state.getClass().getDeclaredField("dialogType");
			field.setAccessible(true);
			return field.get(state) != null;
		} catch (ReflectiveOperationException | SecurityException exception) {
			return false;
		}
	}

	private UIPanelAPI findScreenPanel() {
		try {
			Method method = Global.getCurrentState().getClass().getDeclaredMethod("getScreenPanel");
			MethodHandle handle = MethodHandles.lookup().unreflect(method).bindTo(Global.getCurrentState());
			Object result = handle.invokeWithArguments();
			return result instanceof UIPanelAPI ? (UIPanelAPI) result : null;
		} catch (Throwable exception) {
			return null;
		}
	}

	private String text(String key) {
		try {
			return Global.getSettings().getString("AI_Battles_Next", key);
		} catch (RuntimeException exception) {
			return key;
		}
	}

	private final class EntryPlugin extends BaseCustomUIPanelPlugin {

		@Override
		public void positionChanged(PositionAPI position) {
		}

		@Override
		public void render(float alphaMult) {
		}

		@Override
		public void advance(float amount) {
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
		}

		@Override
		public void buttonPressed(Object buttonId) {
			if ("open".equals(buttonId)) {
				managementVisible = true;
				mountPanels();
			}
		}

		@Override
		public void renderBelow(float alphaMult) {
		}
	}

}
