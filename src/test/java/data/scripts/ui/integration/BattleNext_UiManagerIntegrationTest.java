package data.scripts.ui.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.scripts.ui.page.BattleNext_SettingsPage;
import data.scripts.ui.widget.BattleNext_BattleSourceField;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.scripts.ui.widget.BattleNext_StatusBar;
import data.simulator.BattleNext_SimulatorService;
import data.simulator.BattleNext_SimulatorWidget;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_UiManagerIntegrationTest extends BattleNext_TestVariantFixture {
	private final AtomicReference<JSONObject> savedSettings = new AtomicReference<>();
	private boolean failSettingsWrite;

	@Test
	void retainedReportsRefreshTheShellImmediatelyAndStayWithinTheCurrentPage() throws Exception {

		installSettings(false);
		var configurations = new BattleNext_TournamentStore();
		configurations.reload();
		configurations.save(new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2));
		var started = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("alpha", "beta"));
		var instances = new BattleNext_StartedTournamentStore();
		instances.save(started);
		instances.activate(started.getId());
		var manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		BattleNext_BattleSignals.markPendingResult("before-open");
		manager.open();
		manager.buttonPressed(tab("SETTINGS"));
		var status = (BattleNext_StatusBar) getField(manager, "statusBar");
		var label = (LabelAPI) getField(status, "statusLabel");
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_active_tournament"), started.getId()), label.getText());
		var report = new BattleNext_BattleReportModel();
		report.setId(UUID.randomUUID().toString());
		report.setName("Generated report");
		report.setCreatedAt(System.currentTimeMillis());
		assertTrue(BattleNext_BattleResultRecorder.saveStandalone(report));
		int reads = dataReads.size();
		manager.advance(0f);
		assertEquals(reads, dataReads.size());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_report_updated"), report.getId()), label.getText());
		manager.advance(0f);
		assertEquals(report.getId(), manager.getPendingReportId());
		String error = BattleNext_TestTextCatalog.text("Common", "BattleNext_common_load_failed");
		status.showError(error);
		manager.advance(0f);
		assertEquals(error, label.getText());
		status.update("", started.getId(), manager.getPendingReportId());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_report_updated"), report.getId()), label.getText());
		var reports = new BattleNext_BattleReportStore();
		reports.reload();
		report.setName("Renamed report");
		reports.save(report);
		assertEquals("", BattleNext_BattleSignals.consumePendingResult());
		var page = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		((ButtonAPI) getField(getField(page, "editor"), "cnTextCheckbox")).setChecked(true);
		manager.buttonPressed(tab("FLEETS"));
		var latest = new BattleNext_BattleReportModel();
		latest.setId(UUID.randomUUID().toString());
		latest.setName("Latest report");
		latest.setCreatedAt(System.currentTimeMillis());
		assertTrue(BattleNext_BattleResultRecorder.saveStandalone(latest));
		manager.advance(0f);
		assertEquals(latest.getId(), manager.getPendingReportId());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_report_updated"), latest.getId()), label.getText());
		manager.buttonPressed("confirm_cancel");
		assertEquals(latest.getId(), manager.getPendingReportId());
		manager.buttonPressed("settings_save");
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_report_updated"), latest.getId()), label.getText());
		manager.buttonPressed(tab("FLEETS"));
		assertEquals("", manager.getPendingReportId());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_active_tournament"), started.getId()), label.getText());
		BattleNext_BattleSignals.markPendingResult(report.getId());
		manager.buttonPressed(tab("REPORTS"));
		manager.advance(0f);
		assertEquals("", manager.getPendingReportId());
		manager.buttonPressed("close");
		BattleNext_BattleSignals.markPendingResult(latest.getId());
		manager.advance(0f);
		manager.open();
		manager.advance(0f);
		assertEquals("", manager.getPendingReportId());
		manager.buttonPressed("close");
	}

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

	@ParameterizedTest
	@ValueSource(strings = {"direct", "host", "confirm"})
	void languageCommitRefreshesTheShellThroughEverySaveRoute(String route) throws Exception {

		boolean initial = false;
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
		statusBar.update("", "example", "");
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
		String category = "Management";
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
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_close"),
				((ButtonAPI) getField(manager, "closeButton")).getText());
		if (!route.equals("confirm")) {
			assertSame(page, getField(manager, "attachedView"));
			assertEquals(String.format(BattleNext_TestTextCatalog.text(category, "BattleNext_management_active_tournament"), "example"),
					((LabelAPI) getField(statusBar, "statusLabel")).getText());
		}
		manager.buttonPressed(settings);
		((ButtonAPI) getField(getField(page, "editor"), "cnTextCheckbox")).setChecked(initial);
		manager.buttonPressed("settings_save");
		assertEquals(initial, BattleNext_Settings.isCnTextEnabled());
		assertEquals(initial, savedSettings.get().getBoolean("cnText"));
		assertSame(headingLabel, getField(manager, "headingLabel"));
		assertEquals(BattleNext_TestTextCatalog.text("Management", "BattleNext_management_heading"),
				headingLabel.getText());
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_close"),
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
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_close"),
				((ButtonAPI) getField(manager, "closeButton")).getText());
		manager.buttonPressed("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		manager.buttonPressed("close");
		manager.buttonPressed("confirm_discard");
		assertNull(getField(manager, "attachedView"));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void simulatorUnlockIsConfirmedAndRetainedUntilTheManagementWindowCloses(boolean initial) throws Exception {

		installSettings(initial);
		var instance = BattleNext_SimulatorService.class.getDeclaredField("instance");
		instance.setAccessible(true);
		Object service = instance.get(null);
		AtomicInteger closes = new AtomicInteger();
		BattleNext_UiManager manager = new BattleNext_UiManager(closes::incrementAndGet);
		CustomPanelAPI root = panel(1400f, 900f, manager);
		manager.attach(root);
		manager.open();
		Object settings = tab("SETTINGS");
		manager.buttonPressed(settings);
		BattleNext_SettingsPage page = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		String category = "Simulator";
		ButtonAPI enable = simulatorButtons(page).get(0);
		assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_simulator_enable"), enable.getText());
		assertSimulatorHeading(page, initial);
		assertFalse(page.hasUnsavedChanges());
		String stored = savedSettings.get().toString();
		manager.buttonPressed(enable.getCustomData());
		BattleNext_ConfirmationDialog dialog = (BattleNext_ConfirmationDialog) getField(manager, "confirmationDialog");
		CustomPanelAPI popup = (CustomPanelAPI) getField(dialog, "confirmation");
		assertSame(root.getPosition(), geometry.get(popup.getPosition()).getParent());
		assertTrue(labels.contains(BattleNext_TestTextCatalog.text(category, "BattleNext_simulator_confirm_message")));
		manager.buttonPressed(tab("FLEETS"));
		assertEquals(settings, getField(manager, "currentPage"));
		assertTrue(dialog.isActive());
		manager.buttonPressed("action_cancel");
		assertEquals(List.of(enable), simulatorButtons(page));
		assertEquals(stored, savedSettings.get().toString());
		manager.buttonPressed(enable.getCustomData());
		AtomicBoolean escape = new AtomicBoolean();
		assertTrue(manager.handleGlobalInput(List.of(event("escape", escape))));
		assertTrue(escape.get());
		assertFalse(dialog.isActive());
		assertEquals(List.of(enable), simulatorButtons(page));
		TextFieldAPI timeLimit = (TextFieldAPI) getField(getField(page, "editor"), "battleTimeLimitField");
		timeLimit.setText("pending");
		manager.buttonPressed(enable.getCustomData());
		manager.buttonPressed("action_confirm");
		assertTrue(simulatorButtons(page).isEmpty());
		assertSimulatorHeading(page, initial);
		BattleNext_SimulatorWidget unlocked = (BattleNext_SimulatorWidget) getField(page, "simulatorWidget");
		PositionAPI source = ((BattleNext_BattleSourceField) getField(unlocked, "sourceField")).getPanel().getPosition();
		assertEquals(BattleNext_PageLayout.HEADER_HEIGHT + BattleNext_PageLayout.PAGE_CONTENT_INSET,
				unlocked.getPanel().getPosition().getY() + unlocked.getPanel().getPosition().getHeight()
						- source.getY() - source.getHeight());
		assertEquals(stored, savedSettings.get().toString());
		assertSame(timeLimit, getField(getField(page, "editor"), "battleTimeLimitField"));
		assertEquals("pending", timeLimit.getText());
		assertTrue(page.hasUnsavedChanges());
		assertFalse(dialog.isActive());
		page.refreshContent();
		assertTrue(simulatorButtons(page).isEmpty());
		assertEquals("pending", ((TextFieldAPI) getField(getField(page, "editor"), "battleTimeLimitField")).getText());
		page.discard();
		assertTrue(simulatorButtons(page).isEmpty());
		assertFalse(page.hasUnsavedChanges());
		((ButtonAPI) getField(getField(page, "editor"), "cnTextCheckbox")).setChecked(!initial);
		manager.buttonPressed("settings_save");
		assertEquals(!initial, savedSettings.get().getBoolean("cnText"));
		assertTrue(simulatorButtons(page).isEmpty());
		assertSimulatorHeading(page, !initial);
		manager.buttonPressed(tab("FLEETS"));
		assertNull(getField(page, "panel"));
		manager.buttonPressed(settings);
		assertSame(page, getField(manager, "settingsPage"));
		assertTrue(simulatorButtons(page).isEmpty());
		assertFalse(page.hasUnsavedChanges());
		manager.buttonPressed("close");
		assertEquals(1, closes.get());
		assertNull(getField(page, "simulatorWidget"));
		manager.open();
		manager.buttonPressed(settings);
		BattleNext_SettingsPage reopened = (BattleNext_SettingsPage) getField(manager, "settingsPage");
		assertNotSame(page, reopened);
		assertEquals(1, simulatorButtons(reopened).size());
		assertEquals(BattleNext_TestTextCatalog.text("Simulator", "BattleNext_simulator_enable"),
				simulatorButtons(reopened).get(0).getText());
		assertSame(service, instance.get(null));
		manager.buttonPressed("close");
		assertEquals(2, closes.get());
		assertTrue(((BattleNext_UiInputRouter) getField(manager, "inputRouter")).isEmpty());
	}

	private List<ButtonAPI> simulatorButtons(BattleNext_SettingsPage page) throws Exception {
		BattleNext_SimulatorWidget widget = (BattleNext_SimulatorWidget) getField(page, "simulatorWidget");
		CustomPanelAPI content = (CustomPanelAPI) children.get(widget.getPanel()).get(0);
		return children.get(content).stream().filter(TooltipMakerAPI.class::isInstance)
				.flatMap(child -> tooltips.get(child).getComponents().stream())
				.filter(ButtonAPI.class::isInstance).map(ButtonAPI.class::cast)
				.filter(button -> "simulator_enable".equals(button.getCustomData())).toList();
	}

	private void assertSimulatorHeading(BattleNext_SettingsPage page, boolean cnText) throws Exception {
		BattleNext_SimulatorWidget widget = (BattleNext_SimulatorWidget) getField(page, "simulatorWidget");
		CustomPanelAPI content = (CustomPanelAPI) children.get(widget.getPanel()).get(0);
		String title = BattleNext_TestTextCatalog.text("Simulator", "BattleNext_simulator_heading");
		LabelAPI heading = children.get(content).stream().filter(TooltipMakerAPI.class::isInstance)
				.flatMap(child -> tooltips.get(child).getComponents().stream()).filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).filter(label -> title.equals(label.getText())).findFirst().orElseThrow();
		assertEquals(Misc.getNegativeHighlightColor(), headingBackgroundColors.get(heading.getPosition()));
		assertEquals(content.getPosition().getX(), heading.getPosition().getX());
		assertEquals(content.getPosition().getWidth(), heading.getPosition().getWidth());
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
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
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
