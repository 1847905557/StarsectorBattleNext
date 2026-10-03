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
import data.test.fixture.BattleNext_TestPageFixture;
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

class BattleNext_PageLifecycleIntegrationTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(ints = {500, 800, 1400})
	void managementPagesShareRegionsPromptsScrollingAndFooter(int width) throws Exception {

		List<BattleNext_ManagementPage> pages = List.of(
				new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {}),
				new BattleNext_PinnedVariantPage(() -> {}, Runnable::run, (description, action) -> {}),
				new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {}),
				new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {}),
				new BattleNext_RulesPage(() -> {}, Runnable::run, (description, action) -> {}));
		for (BattleNext_ManagementPage page : pages) {
			buttons.clear();
			CustomPanelAPI root = panel(width, 700f, new BaseCustomUIPanelPlugin());
			page.attach(root);
			CustomPanelAPI shell = (CustomPanelAPI) getField(page, "panel");
			UIRect[] expected = BattleNext_PageLayout.managementRegions(shell);
			UIRect listRect = (UIRect) getField(page, "listRect");
			UIRect detailRect = (UIRect) getField(page, "detailRect");
			assertEquals(expected[0].getX(), listRect.getX());
			assertEquals(expected[0].getWidth(), listRect.getWidth());
			assertEquals(expected[1].getX(), detailRect.getX());
			assertEquals(expected[1].getHeight(), detailRect.getHeight());
			TooltipMakerAPI list = (TooltipMakerAPI) getField(page, "listContent");
			TooltipState listState = tooltips.get(list);
			assertTrue(listState.isScrollable());
			assertEquals(listState.getUsedHeight(), listState.getMountedHeight());
			CustomPanelAPI detail = (CustomPanelAPI) getField(page, "detailPanel");
			TooltipState prompt = tooltips.get(children.get(detail).get(0));
			assertFalse(prompt.isScrollable());
			assertEquals(8f, prompt.getViewport().getX() - detail.getPosition().getX());
			assertEquals(8f, detail.getPosition().getY() + detail.getPosition().getHeight()
					- prompt.getViewport().getY() - prompt.getViewport().getHeight());
			assertFalse(buttons.get("save").get());
			boolean add = page instanceof BattleNext_FleetLibraryPage || page instanceof BattleNext_TournamentPage
					|| page instanceof BattleNext_RulesPage;
			assertEquals(add ? List.of("add", "save", "back") : List.of("save", "back"),
					new ArrayList<>(buttons.keySet()));
			float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(expected[2], add ? 3 : 2);
			UIRect save = BattleNext_PageLayout.managementFooterButtonRect(expected[2], buttonWidth, add ? 1 : 0);
			PositionAPI savePosition = buttonPositions.get("save");
			assertEquals(save.getX(), savePosition.getX() - shell.getPosition().getX());
			assertEquals(save.getY(), shell.getPosition().getY() + shell.getPosition().getHeight()
					- savePosition.getY() - savePosition.getHeight());
			int childCount = children.get(shell).size();
			page.refreshContent();
			assertEquals(childCount, children.get(shell).size());
			assertEquals(1, children.get(root).size());
			page.createContents();
			assertEquals(1, children.get(root).size());
			page.close();
			assertTrue(children.get(root).isEmpty());
		}
	}

	@Test
	void pageLifecycleOwnsMountsInputAndSaveDiscardBridges() throws Exception {

		ProbePage page = new ProbePage();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		page.setInputRouter(router);
		CustomPanelAPI firstRoot = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		page.attach(firstRoot);
		assertTrue(page.getEvents().indexOf("load") < page.getEvents().indexOf("build"));
		assertTrue(page.getEvents().indexOf("build") < page.getEvents().indexOf("refresh"));
		assertTrue(page.getEvents().indexOf("refresh") < page.getEvents().indexOf("baseline"));
		AtomicBoolean closed = new AtomicBoolean();
		router.push(new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return firstRoot.getPosition();
			}

			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				return false;
			}

			@Override
			public void close() {
				closed.set(true);
			}
		});
		page.refreshContent();
		assertTrue(closed.get());
		assertTrue(router.isEmpty());
		assertEquals(1, children.get(firstRoot).size());
		page.setDirty(true);
		assertTrue(page.hasUnsavedChanges());
		assertTrue(page.save());
		assertEquals("saved", page.getStatusText());
		assertEquals("saved", ((LabelAPI) getField(page, "statusLabel")).getText());
		assertFalse(page.hasUnsavedChanges());
		page.setDirty(true);
		page.discard();
		assertFalse(page.hasUnsavedChanges());
		CustomPanelAPI secondRoot = panel(800f, 600f, new BaseCustomUIPanelPlugin());
		page.attach(secondRoot);
		assertTrue(children.get(firstRoot).isEmpty());
		assertEquals(1, children.get(secondRoot).size());
		for (String name : List.of("createContents", "refreshContent", "close", "save", "discard", "getStatusText", "hasUnsavedChanges")) {
			assertTrue(Modifier.isFinal(BattleNext_PageView.class.getMethod(name).getModifiers()));
		}
		assertTrue(Modifier.isFinal(BattleNext_PageView.class.getMethod("attach", CustomPanelAPI.class).getModifiers()));
		assertTrue(Modifier.isFinal(BattleNext_PageView.class.getMethod("buttonPressed", Object.class).getModifiers()));
		page.close();
		assertTrue(children.get(secondRoot).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"selector", "settings"})
	void dedicatedPagesUseTheSharedMountLifecycle(String kind) throws Exception {
		BattleNext_PageView page = "selector".equals(kind)
				? new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin())
				: new BattleNext_SettingsPage();
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		assertEquals(1, children.get(root).size());
		page.refreshContent();
		assertEquals(1, children.get(root).size());
		page.attach(root);
		assertEquals(1, children.get(root).size());
		page.close();
		assertTrue(children.get(root).isEmpty());
	}

	@Test
	void selectorKeepsRawDraftAndDirtyStateAcrossRefresh() throws Exception {
		BattleNext_BattleSelectorPage page = new BattleNext_BattleSelectorPage(() -> {}, new BaseCustomUIPanelPlugin());
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("add");
		Object editor = getField(page, "quickBattleEditor");
		Object row = ((List<?>) getField(editor, "rows")).get(0);
		((TextFieldAPI) getField(row, "player")).setText("Draft fleet");
		((TextFieldAPI) getField(row, "map")).setText("Unfinished number");
		String snapshot = ((BattleNext_EditorView) editor).serializeState();
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		assertSame(editor, getField(page, "quickBattleEditor"));
		assertEquals(snapshot, ((BattleNext_EditorView) editor).serializeState());
		assertTrue(page.hasUnsavedChanges());
	}

	@Test
	void settingsKeepsRawDraftAndDirtyStateAcrossRefresh() throws Exception {
		BattleNext_SettingsPage page = new BattleNext_SettingsPage();
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		((TextFieldAPI) getField(page, "battleTimeLimit")).setText("pending");
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		assertEquals("pending", ((TextFieldAPI) getField(page, "battleTimeLimit")).getText());
		assertTrue(page.hasUnsavedChanges());
		CustomPanelAPI content = (CustomPanelAPI) getField(page, "contentPanel");
		TooltipState state = tooltips.get(children.get(content).get(0));
		PositionAPI save = buttonPositions.get("settings_save");
		assertEquals(8f, state.getViewport().getY() - save.getY() - save.getHeight());
		page.discard();
		assertFalse(page.hasUnsavedChanges());
	}

	@ParameterizedTest
	@ValueSource(strings = {"fleet", "rules", "tournament"})
	void writableEditorsKeepDraftAndBaselineAcrossPageRefresh(String kind) throws Exception {
		BattleNext_ManagementPage page = switch (kind) {
			case "fleet" -> new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {});
			case "rules" -> new BattleNext_RulesPage(() -> {}, Runnable::run, (description, action) -> {});
			default -> new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
		};
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		assertFalse(buttons.get("save").get());
		page.buttonPressed("add");
		assertTrue(buttons.get("save").get());
		Object editor = getField(page, "editor");
		TextFieldAPI field = (TextFieldAPI) getField(editor, "fleet".equals(kind) ? "name" : "nameField");
		field.setText("Draft name");
		assertTrue(page.hasUnsavedChanges());
		page.refreshContent();
		field = (TextFieldAPI) getField(editor, "fleet".equals(kind) ? "name" : "nameField");
		assertEquals("Draft name", field.getText());
		assertTrue(page.hasUnsavedChanges());
		page.buttonPressed("back");
		assertFalse(buttons.get("save").get());
	}

	@ParameterizedTest
	@ValueSource(strings = {"fleet", "pin", "report", "tournament", "rules"})
	void managementSaveRequiresEveryStoreUsedByTheSelectedObject(String kind) throws Exception {

		BattleNext_ManagementPage page = switch (kind) {
			case "fleet" -> new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {});
			case "pin" -> new BattleNext_PinnedVariantPage(() -> {}, Runnable::run, (description, action) -> {});
			case "report" -> new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
			case "tournament" -> new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
			default -> new BattleNext_RulesPage(() -> {}, Runnable::run, (description, action) -> {});
		};
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		assertFalse(buttons.get("save").get());
		if ("pin".equals(kind)) {
			BattleNext_PinnedVariantStore store = (BattleNext_PinnedVariantStore) getField(page, "store");
			var pin = store.saveVariant("Pin", source().getApi());
			page.attach(root);
			page.buttonPressed("pin:" + pin.getSourcePath());
		} else if ("report".equals(kind)) {
			var store = (BattleNext_BattleReportStore) getField(page, "reportStore");
			var report = new BattleNext_BattleReportModel();
			report.setId("report-1");
			report.setName("Report");
			store.save(report);
			page.attach(root);
			page.buttonPressed("report:report-1");
		} else {
			page.buttonPressed("add");
		}
		assertTrue(buttons.get("save").get());
		List<String> dependencies = switch (kind) {
			case "tournament" -> List.of("tournamentStore", "fleetStore", "ruleStore");
			case "report" -> List.of("reportStore");
			default -> List.of("store");
		};
		Field loadError = BattleNext_BaseStore.class.getDeclaredField("loadError");
		loadError.setAccessible(true);
		for (String dependency : dependencies) {
			Object store = getField(page, dependency);
			loadError.set(store, "Injected load failure");
			page.refreshContent();
			assertFalse(buttons.get("save").get(), dependency);
			loadError.set(store, "");
			page.refreshContent();
			assertTrue(buttons.get("save").get(), dependency);
		}
	}

	@Test
	void tournamentSaveIsDisabledForStartedObjectsAcrossRefresh() throws Exception {
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> {});
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		var store = (BattleNext_TournamentStore) getField(page, "tournamentStore");
		var tournament = new BattleNext_TournamentModel();
		tournament.setId("cup");
		tournament.setName("Cup");
		tournament.setStarted(true);
		store.save(tournament);
		page.attach(root);
		page.buttonPressed("tournament:select:cup");
		assertFalse(buttons.get("save").get());
		assertFalse(page.hasUnsavedChanges());
		page.refreshContent();
		assertFalse(buttons.get("save").get());
		assertTrue(labels.contains("BattleNext_tournament_locked_message"));
	}

	@Test
	void reportDetailsKeepDraftExpansionAndFooterSaveAcrossRefresh() throws Exception {

		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report-1");
		report.setName("Original report");
		report.addPlayerMember(new BattleNext_MemberReport("parent_variant", "Parent"));
		store.save(report);
		BattleNext_BattleReportPage page = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("report:report-1");
		assertTrue(buttons.get("save").get());
		assertTrue(labels.contains("report-1"));
		((TextFieldAPI) getField(page, "nameField")).setText("Draft report");
		page.buttonPressed("member:0");
		assertEquals(1, ((Set<?>) getField(page, "expandedMembers")).size());
		assertEquals("Draft report", ((TextFieldAPI) getField(page, "nameField")).getText());
		assertTrue(page.hasUnsavedChanges());
		CustomPanelAPI detail = (CustomPanelAPI) getField(page, "detailPanel");
		UIRect detailRect = (UIRect) getField(page, "detailRect");
		UIRect delete = BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect);
		assertEquals(delete.getX(), buttonPositions.get("delete").getX() - detail.getPosition().getX());
		assertEquals(delete.getY(), detail.getPosition().getY() + detail.getPosition().getHeight()
				- buttonPositions.get("delete").getY() - buttonPositions.get("delete").getHeight());
		assertTrue(page.save());
		assertFalse(page.hasUnsavedChanges());
		store.reload();
		assertEquals("Draft report", store.get("report-1").getName());
	}
}
