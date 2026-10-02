package data.scripts.ui.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.*;
import data.model.BattleNext_BaseStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.BattleNextModPlugin;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.page.*;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.test.fixture.BattleNext_TestVariantFixture;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lunalib.lunaRefit.LunaRefitManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;
import static org.junit.jupiter.api.Assertions.*;

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
		((TextFieldAPI) getField(page, "name")).setText("Changed");
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
