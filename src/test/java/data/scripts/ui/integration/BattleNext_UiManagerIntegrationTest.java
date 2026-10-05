package data.scripts.ui.integration;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.widget.BattleNext_StatusBar;
import data.scripts.ui.page.*;
import data.test.fixture.BattleNext_TestVariantFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.io.IOException;

class BattleNext_UiManagerIntegrationTest extends BattleNext_TestVariantFixture {
	private final AtomicReference<JSONObject> savedSettings = new AtomicReference<>();
	private boolean failSettingsWrite;

	@Test
	void shellConfirmsUnsavedNameOnTabNavigation() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("Original", source().getApi());
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		Object variants = tab("VARIANTS");
		Object fleets = tab("FLEETS");
		manager.buttonPressed(variants);
		var page = (BattleNext_PinnedVariantPage) getField(manager, "variantPage");
		manager.buttonPressed("pin:" + pin.getSourcePath());
		((TextFieldAPI) getField(getField(page, "editor"), "nameField")).setText("Changed");
		manager.buttonPressed(fleets);
		assertEquals(variants, getField(manager, "currentPage"));
		manager.buttonPressed("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		manager.buttonPressed(fleets);
		manager.buttonPressed("confirm_save");
		assertEquals(fleets, getField(manager, "currentPage"));
		store.reload();
		assertEquals("Changed", store.get(pin.getPinId()).getDisplayName());
	}

	@Test
	void pinnedVariantsUseTheirFormalViewAndEscapeReturnsToTheSelector() throws Exception {

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		var enumeration = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage");
		Object[] tabs = enumeration.getEnumConstants();
		Object variants = Arrays.stream(tabs).filter(tab -> tab.toString().equals("VARIANTS")).findFirst().orElseThrow();
		Object selector = Arrays.stream(tabs).filter(tab -> tab.toString().equals("BATTLE_SELECTOR")).findFirst().orElseThrow();
		manager.buttonPressed(variants);
		assertInstanceOf(BattleNext_PinnedVariantPage.class, getField(manager, "variantPage"));
		AtomicBoolean consumed = new AtomicBoolean();
		assertTrue(manager.handleGlobalInput(List.of(event("escape", consumed))));
		assertTrue(consumed.get());
		assertEquals(selector, getField(manager, "currentPage"));
	}

	@Test
	void managerRoutesDeclaredTabsAndReleasesEachPagesInputAndPanelBeforeClosing() throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> closed.set(true));
		manager.attach(panel(1400f, 900f, manager));
		manager.open();
		Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(manager, "inputRouter");
		for (int index = 0; index < tabs.length; index++) {
			manager.buttonPressed(tabs[index]);
			BattleNext_PageView page = (BattleNext_PageView) getField(manager, "attachedView");
			assertEquals(tabs[index], getField(manager, "currentPage"));
			assertSame(router, getField(page, "inputRouter"));
			AtomicBoolean overlayClosed = new AtomicBoolean();
			PositionAPI bounds = ((CustomPanelAPI) getField(page, "panel")).getPosition();
			router.push(new BattleNext_UiInputRouter.Overlay() {
				@Override
				public PositionAPI getBounds() {
					return bounds;
				}

				@Override
				public boolean handleGlobalInput(List<InputEventAPI> events) {
					return false;
				}

				@Override
				public void close() {
					overlayClosed.set(true);
				}
			});
			manager.buttonPressed(tabs[(index + 1) % tabs.length]);
			assertTrue(overlayClosed.get());
			assertTrue(router.isEmpty());
			assertNull(getField(page, "panel"));
		}
		BattleNext_PageView next = (BattleNext_PageView) getField(manager, "attachedView");
		manager.buttonPressed("close");
		assertTrue(closed.get());
		assertNull(getField(next, "panel"));
		assertNull(getField(manager, "attachedView"));
	}

	@ParameterizedTest
	@CsvSource({"false,direct", "true,direct", "false,host", "true,host", "false,confirm", "true,confirm"})
	void languageCommitRefreshesTheShellThroughEverySaveRoute(boolean initial, String route) throws Exception {

		deferTooltipWidth = true;
		shortenLabels = true;
		installSettings(initial);
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
		Object settings = Arrays.stream(tabs).filter(tab -> tab.toString().equals("SETTINGS")).findFirst().orElseThrow();
		LabelAPI headingLabel = (LabelAPI) getField(manager, "headingLabel");
		float headingX = headingLabel.getPosition().getX();
		float headingWidth = headingLabel.getPosition().getWidth();
		manager.buttonPressed(settings);
		BattleNext_SettingsPage page = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		Object editor = getField(page, "editor");
		((ButtonAPI) getField(editor, "cnTextCheckbox")).setChecked(!initial);
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		BattleNext_StatusBar statusBar = (BattleNext_StatusBar) getField(manager, "statusBar");
		statusBar.update("", "example", true);
		if (route.equals("confirm")) {
			manager.buttonPressed(tabs[0]);
			assertEquals(settings, getField(manager, "currentPage"));
			manager.buttonPressed("confirm_save");
			assertEquals(tabs[0], getField(manager, "currentPage"));
		} else if (route.equals("host")) {
			assertTrue(manager.saveCurrentPage());
		} else {
			manager.buttonPressed("settings_save");
		}
		assertEquals(!initial, savedSettings.get().getBoolean("cnText"));
		assertEquals(!initial, BattleNext_Settings.isCnTextEnabled());
		assertFalse(page.hasUnsavedChanges());
		String category = !initial ? "Management_CN" : "Management";
		ButtonAPI[] buttons = (ButtonAPI[]) getField(manager, "tabButtons");
		for (int index = 0; index < tabs.length; index++) {
			String key = "BattleNext_management_" + getField(tabs[index], "titleKey");
			assertEquals(BattleNext_TestTextCatalog.text(category, key), buttons[index].getText());
		}
		TooltipMakerAPI heading = (TooltipMakerAPI) getField(manager, "headingContent");
		assertFalse(heading.getPrev() instanceof LabelAPI);
		assertTrue(tooltips.get(heading).getComponents().contains(heading.getPrev()));
		assertSame(headingLabel, getField(manager, "headingLabel"));
		assertEquals(headingX, headingLabel.getPosition().getX());
		assertEquals(headingWidth, headingLabel.getPosition().getWidth());
		assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_management_heading"), headingLabel.getText());
		assertEquals(BattleNext_TestTextCatalog.text(!initial ? "Common_CN" : "Common", "BattleNext_common_close"),
				((ButtonAPI) getField(manager, "closeButton")).getText());
		if (!route.equals("confirm")) {
			assertSame(page, getField(manager, "attachedView"));
			assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_management_active_tournament") + ": example",
					((LabelAPI) getField(statusBar, "statusLabel")).getText());
		}
		manager.buttonPressed(settings);
		((ButtonAPI) getField(getField(page, "editor"), "cnTextCheckbox")).setChecked(initial);
		manager.buttonPressed("settings_save");
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		assertEquals(initial, savedSettings.get().getBoolean("cnText"));
		assertSame(headingLabel, getField(manager, "headingLabel"));
		assertEquals(BattleNext_TestTextCatalog.text(initial ? "Management_CN" : "Management", "BattleNext_management_heading"),
				headingLabel.getText());
		assertEquals(BattleNext_TestTextCatalog.text(initial ? "Common_CN" : "Common", "BattleNext_common_close"),
				((ButtonAPI) getField(manager, "closeButton")).getText());
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(manager, "inputRouter");
		assertTrue(router.isEmpty());
		manager.buttonPressed("close");
		assertFalse(router.getSession().hasInputState());
		assertNull(getField(manager, "attachedView"));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void failedConfirmationSaveKeepsTheDialogDraftAndShellLanguage(boolean initial) throws Exception {
		installSettings(initial);
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
		Object settings = Arrays.stream(tabs).filter(tab -> tab.toString().equals("SETTINGS")).findFirst().orElseThrow();
		manager.buttonPressed(settings);
		BattleNext_SettingsPage page = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		((ButtonAPI) getField(getField(page, "editor"), "cnTextCheckbox")).setChecked(!initial);
		manager.buttonPressed(tabs[0]);
		failSettingsWrite = true;
		manager.buttonPressed("confirm_save");
		assertEquals(settings, getField(manager, "currentPage"));
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		assertEquals(initial, savedSettings.get().getBoolean("cnText"));
		assertTrue(page.hasUnsavedChanges());
		assertFalse(((BattleNext_UiInputRouter) getField(manager, "inputRouter")).isEmpty());
		assertEquals(BattleNext_TestTextCatalog.text(initial ? "Common_CN" : "Common", "BattleNext_common_close"),
				((ButtonAPI) getField(manager, "closeButton")).getText());
		manager.buttonPressed("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		manager.buttonPressed("close");
		manager.buttonPressed("confirm_discard");
		assertNull(getField(manager, "attachedView"));
	}

	private Object tab(String name) throws ClassNotFoundException {
		Class<?> type = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage");
		return Arrays.stream(type.getEnumConstants()).filter(value -> value.toString().equals(name)).findFirst().orElseThrow();
	}

	private void installSettings(boolean initial) throws Exception {
		savedSettings.set(new JSONObject().put("cnText", initial));
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.text(args[0], args[1]);
			if (method.getName().equals("readJSONFromCommon")) return savedSettings.get();
			if (method.getName().equals("writeJSONToCommon")) {
				if (failSettingsWrite) throw new IOException("Injected settings write failure");
				savedSettings.set(new JSONObject(((JSONObject) args[1]).toString()));
				return null;
			}
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}
}
