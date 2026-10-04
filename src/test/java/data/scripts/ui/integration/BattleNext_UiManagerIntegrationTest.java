package data.scripts.ui.integration;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.page.*;
import data.test.fixture.BattleNext_TestVariantFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_UiManagerIntegrationTest extends BattleNext_TestVariantFixture {

	@Test
	void shellConfirmsUnsavedNameOnTabNavigation() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("Original", source().getApi());
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
		manager.buttonPressed(tabs[2]);
		var page = (BattleNext_PinnedVariantPage) getField(manager, "variantPage");
		manager.buttonPressed("pin:" + pin.getSourcePath());
		((TextFieldAPI) getField(getField(page, "editor"), "nameField")).setText("Changed");
		manager.buttonPressed(tabs[1]);
		assertEquals(tabs[2], getField(manager, "currentPage"));
		manager.buttonPressed("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		manager.buttonPressed(tabs[1]);
		manager.buttonPressed("confirm_save");
		assertEquals(tabs[1], getField(manager, "currentPage"));
		store.reload();
		assertEquals("Changed", store.get(pin.getPinId()).getDisplayName());
	}

	@Test
	void shellContainsSevenOrderedTabsAndVariantsUsesFormalViewAndEscape() throws Exception {

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> {});
		manager.attach(panel(1400f, 900f, manager));
		var enumeration = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage");
		Object[] tabs = enumeration.getEnumConstants();
		assertEquals(List.of("BATTLE_SELECTOR", "FLEETS", "VARIANTS", "REPORTS", "TOURNAMENTS", "RULES", "SETTINGS"),
				Arrays.stream(tabs).map(Object::toString).toList());
		manager.buttonPressed(tabs[2]);
		assertInstanceOf(BattleNext_PinnedVariantPage.class, getField(manager, "variantPage"));
		AtomicBoolean consumed = new AtomicBoolean();
		assertTrue(manager.handleGlobalInput(List.of(event("escape", consumed))));
		assertTrue(consumed.get());
		assertEquals(tabs[0], getField(manager, "currentPage"));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
	void managerRoutesEveryPageAndClosesItsInputAndPanel(int tab) throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_UiManager manager = new BattleNext_UiManager(() -> closed.set(true));
		manager.attach(panel(1400f, 900f, manager));
		manager.open();
		Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
		manager.buttonPressed(tabs[tab]);
		BattleNext_PageView page = (BattleNext_PageView) getField(manager, "attachedView");
		List<Class<?>> types = List.of(BattleNext_BattleSelectorPage.class, BattleNext_FleetLibraryPage.class,
				BattleNext_PinnedVariantPage.class, BattleNext_BattleReportPage.class,
				BattleNext_TournamentPage.class, BattleNext_RulesPage.class, BattleNext_SettingsPage.class);
		assertInstanceOf(types.get(tab), page);
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(manager, "inputRouter");
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
		manager.buttonPressed(tabs[(tab + 1) % tabs.length]);
		assertTrue(overlayClosed.get());
		assertTrue(router.isEmpty());
		assertNull(getField(page, "panel"));
		BattleNext_PageView next = (BattleNext_PageView) getField(manager, "attachedView");
		manager.buttonPressed("close");
		assertTrue(closed.get());
		assertNull(getField(next, "panel"));
		assertNull(getField(manager, "attachedView"));
	}
}
