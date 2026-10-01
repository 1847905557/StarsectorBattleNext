package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.widget.BattleNext_PinnedVariantDetails;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import lunalib.lunaRefit.LunaRefitManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_PinnedVariantTest {

	@TempDir
	private Path directory;
	private SettingsAPI previousSettings;
	private SectorAPI previousSector;
	private final Map<String, ShipHullSpecAPI> hulls = new LinkedHashMap<>();
	private final List<String> labels = new ArrayList<>();
	private final Map<String, AtomicBoolean> buttons = new LinkedHashMap<>();
	private final Map<CustomPanelAPI, CustomUIPanelPlugin> plugins = new java.util.IdentityHashMap<>();
	private final List<Float> tooltipHeights = new ArrayList<>();
	private boolean failBuild;
	private boolean lunaEnabled;

	@BeforeEach
	void configureGameServices() {

		previousSettings = Global.getSettings();
		previousSector = Global.getSector();
		ModSpecAPI mod = proxy(ModSpecAPI.class, (method, args) -> "getPath".equals(method) ? directory.toString() : null);
		ModManagerAPI mods = proxy(ModManagerAPI.class, (method, args) -> switch (method) {
			case "getModSpec" -> mod;
			case "isModEnabled" -> lunaEnabled && "lunalib".equals(args[0]);
			default -> null;
		});
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> switch (method) {
			case "getModManager" -> mods;
			case "getString" -> args[1];
			case "getColor" -> Color.GRAY;
			case "getFloat" -> 1f;
			case "getInt" -> 1;
			case "getAllVariantIds" -> List.of();
			case "getMergedSpreadsheetData" -> new JSONArray();
			case "createCustom" -> panel((Float) args[0], (Float) args[1], (CustomUIPanelPlugin) args[2]);
			case "getHullSpec" -> failBuild ? null : hulls.get(args[0]);
			case "getHullModSpec" -> proxy(HullModSpecAPI.class, (name, values) -> "getDisplayName".equals(name) ? "Hullmod " + args[0] : null);
			case "getFighterWingSpec" -> proxy(com.fs.starfarer.api.loading.FighterWingSpecAPI.class, (name, values) -> "getWingName".equals(name) ? "Wing " + args[0] : null);
			case "getWeaponSpec" -> proxy(com.fs.starfarer.api.loading.WeaponSpecAPI.class, (name, values) -> "getWeaponName".equals(name) ? "Weapon " + args[0] : null);
			case "createEmptyVariant" -> {
				Variant variant = new Variant((String) args[0], (ShipHullSpecAPI) args[1]);
				yield variant.api;
			}
			case "getSprite" -> proxy(SpriteAPI.class, (name, values) ->
					"getWidth".equals(name) || "getHeight".equals(name) ? 120f : null);
			default -> null;
		}));
	}

	@Test
	void catalogRetainsEveryErrorAndDeletesOnlyTheLoadedSourceFile() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		BattleNext_PinnedVariantModel pin = store.saveVariant("Valid", source().api);
		Path root = directory.resolve("tournament/_pinnedVariants");
		Files.writeString(root.resolve("broken.json"), "not json");
		Files.writeString(root.resolve("wrong.json"), pin.toJson().toString());
		JSONObject unavailable = pin.toJson();
		unavailable.put("pinId", "missing");
		unavailable.getJSONObject("variantJson").put("hullId", "unknown");
		Files.writeString(root.resolve("missing.json"), unavailable.toString());
		store.reload();
		assertEquals(4, store.getCatalog().size());
		assertEquals(1, store.getAll().size());
		assertEquals(3, store.getLoadErrors().size());
		var missing = store.getCatalog().stream().filter(entry -> entry.path().endsWith("missing.json")).findFirst().orElseThrow();
		assertEquals("Valid", missing.name());
		assertEquals("unknown", missing.hullId());
		assertNull(missing.model());
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/_pinnedVariants/../wrong.json"));
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/_pinnedVariants/unloaded.json"));
		assertThrows(IllegalArgumentException.class, () -> store.deleteFile("tournament/other.json"));
		store.deleteFile("tournament/_pinnedVariants/wrong.json");
		assertFalse(Files.exists(root.resolve("wrong.json")));
		assertTrue(Files.exists(root.resolve(pin.getPinId() + ".json")));
		store.deleteFile("tournament/_pinnedVariants/broken.json");
		assertEquals(2, store.getCatalog().size());
	}

	@Test
	void catalogSortUsesNameIdAndPath() {
		var a = new data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry("z", "Same", "b", "", null, "");
		var b = new data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry("a", "Same", "b", "", null, "");
		var c = new data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry("c", "Same", "a", "", null, "");
		var d = new data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader.CatalogEntry("d", "Alpha", "z", "", null, "");
		assertEquals(List.of(d, c, b, a), BattleNext_PinnedVariantPage.sorted(List.of(a, b, c, d)));
	}

	@ParameterizedTest
	@ValueSource(ints = {500, 800, 1400})
	void variantsPageHasFleetLayoutAndRenamePreservesSnapshot(int width) throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		BattleNext_PinnedVariantModel pin = store.saveVariant("Original", source().api);
		String json = pin.getVariantJson().toString();
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> action.run(), () -> {});
		page.attach(panel(width, 700f, new BaseCustomUIPanelPlugin()));
		assertFalse(buttons.get("save").get());
		page.buttonPressed("pin:" + pin.getSourcePath());
		assertTrue(buttons.get("save").get());
		assertTrue(buttons.get("delete").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("Weapon laser [laser]")));
		assertTrue(labels.stream().anyMatch(value -> value.contains("Wing wing [wing]")));
		assertTrue(labels.contains("BattleNext_ui_variant_suppressed_mods"));
		TextFieldAPI field = (TextFieldAPI) getField(page, "name");
		field.setText("  Renamed  ");
		page.refreshContent();
		assertEquals("  Renamed  ", ((TextFieldAPI) getField(page, "name")).getText());
		assertTrue(page.hasUnsavedChanges());
		assertTrue(page.save());
		assertFalse(page.hasUnsavedChanges());
		assertJsonEquals(new JSONObject(json), store.get(pin.getPinId()).getVariantJson());
		assertEquals("Renamed", store.get(pin.getPinId()).getDisplayName());
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();
		assertTrue(provider.getOptions().stream().anyMatch(option -> option.getLabel().equals("Renamed")));
		page.buttonPressed("delete");
		assertTrue(store.getCatalog().isEmpty());
		assertFalse(buttons.get("save").get());
	}

	@Test
	void pageNavigationSupportsCancelDiscardAndSavingBeforeSelection() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var first = store.saveVariant("First", source().api);
		var second = store.saveVariant("Second", source().api);
		List<Runnable> pending = new ArrayList<>();
		AtomicBoolean back = new AtomicBoolean();
		BattleNext_PinnedVariantPage page = page(store, pending::add, (description, action) -> {}, () -> back.set(true));
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("pin:" + first.getSourcePath());
		pending.remove(0).run();
		((TextFieldAPI) getField(page, "name")).setText("Draft");
		page.buttonPressed("pin:" + second.getSourcePath());
		assertTrue(page.hasUnsavedChanges());
		assertEquals(first.getSourcePath(), getField(page, "selectedPath"));
		pending.clear();
		page.discard();
		assertFalse(page.hasUnsavedChanges());
		((TextFieldAPI) getField(page, "name")).setText("Saved draft");
		page.buttonPressed("pin:" + second.getSourcePath());
		assertTrue(page.save());
		pending.remove(0).run();
		assertEquals("Saved draft", store.get(first.getPinId()).getDisplayName());
		page.buttonPressed("back");
		pending.remove(0).run();
		assertEquals("", getField(page, "selectedPath"));
		page.buttonPressed("back");
		assertTrue(back.get());
	}

	@Test
	void pageShowsErrorsAndDeletionConfirmationIdentifiesActualFile() throws Exception {

		Files.createDirectories(directory.resolve("tournament/_pinnedVariants"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants/broken.json"), "{}");
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		List<Runnable> pending = new ArrayList<>();
		List<String> descriptions = new ArrayList<>();
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> {
			descriptions.add(description);
			pending.add(action);
		}, () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("pin:tournament/_pinnedVariants/broken.json");
		assertFalse(buttons.get("save").get());
		assertTrue(buttons.get("delete").get());
		assertNull(getField(page, "name"));
		page.buttonPressed("delete");
		assertTrue(descriptions.get(0).contains("broken.json"));
		assertTrue(Files.exists(directory.resolve("tournament/_pinnedVariants/broken.json")));
		pending.remove(0).run();
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants/broken.json")));
	}

	@Test
	void pageSaveAndDeleteFailuresKeepSelectionAndDraft() throws Exception {

		FailingStore store = new FailingStore();
		store.writer.fail = false;
		store.reload();
		var pin = store.saveVariant("Original", source().api);
		store.writer.fail = true;
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> action.run(), () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("pin:" + pin.getSourcePath());
		TextFieldAPI field = (TextFieldAPI) getField(page, "name");
		field.setText("  ");
		assertFalse(page.save());
		field.setText("Retry");
		assertFalse(page.save());
		assertEquals("Retry", field.getText());
		assertEquals("Original", store.get(pin.getPinId()).getDisplayName());
		store.failDelete = true;
		page.buttonPressed("delete");
		assertTrue(page.getStatusText().contains("Injected delete failure"));
		assertEquals(pin.getSourcePath(), getField(page, "selectedPath"));
		assertTrue(Files.exists(directory.resolve(pin.getSourcePath())));
	}

	@Test
	void pageDisablesWritesOnDirectoryFailureAndReloadsOnEntry() throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> {}, () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		assertTrue(labels.contains("BattleNext_ui_no_pinned_variants"));
		store.saveVariant("External", source().api);
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		assertEquals(1, store.getCatalog().size());
		assertEquals("", getField(page, "selectedPath"));
		Files.createDirectory(directory.resolve("tournament/_pinnedVariants/broken.json"));
		store.reload();
		assertTrue(store.getCatalog().stream().noneMatch(entry -> entry.path().endsWith("broken.json")));
		FailingStore failed = new FailingStore();
		failed.failLoad = true;
		page = page(failed, Runnable::run, (description, action) -> {}, () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		assertFalse(buttons.get("save").get());
		assertTrue(page.getStatusText().contains("Injected load failure"));
	}

	@Test
	void recursiveDetailsKeepDraftAndIsolateModuleErrors() throws Exception {

		var source = source();
		JSONObject json = BattleNext_VariantBuilder.fromVariant(source.api).toJson();
		JSONObject child = json.getJSONObject("modules").getJSONObject("module");
		child.put("modules", new JSONObject().put("nested", new JSONObject()
				.put("hullId", "child").put("variantId", "nested_variant").put("displayName", "Nested")));
		AtomicBoolean refreshed = new AtomicBoolean();
		BattleNext_PinnedVariantDetails details = new BattleNext_PinnedVariantDetails(() -> refreshed.set(true));
		setField(details, "expanded", new java.util.HashSet<>(List.of("root/module", "root/module/nested")));
		CustomPanelAPI owner = panel(600f, 700f, new BaseCustomUIPanelPlugin());
		details.addTo(tooltip(600f, 700f), owner, 600f, json);
		assertTrue(labels.stream().anyMatch(value -> value.contains("Nested")));
		assertTrue(tooltipHeights.stream().anyMatch(height -> height > 200f));
		json.getJSONObject("modules").put("broken", new JSONObject().put("hullId", "missing"));
		labels.clear();
		details.addTo(tooltip(600f, 700f), owner, 600f, json);
		assertTrue(labels.stream().anyMatch(value -> value.contains("root/broken")));
		assertTrue(labels.stream().anyMatch(value -> value.contains("Nested")));
	}

	@Test
	void moduleToggleKeepsEditedNameAndRespectsModalPriority() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("Original", source().api);
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> {}, () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("pin:" + pin.getSourcePath());
		((TextFieldAPI) getField(page, "name")).setText("Unsaved module name");
		CustomUIPanelPlugin header = plugins.values().stream()
				.filter(plugin -> plugin.getClass().getSimpleName().equals("HeaderPlugin")).findFirst().orElseThrow();
		Runnable toggle = (Runnable) getField(header, "onToggle");
		toggle.run();
		assertEquals("Unsaved module name", ((TextFieldAPI) getField(page, "name")).getText());
		assertTrue(page.hasUnsavedChanges());
		var router = (data.scripts.ui.BattleNext_UiInputRouter) getField(page, "inputRouter");
		var overlay = new data.scripts.ui.BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return position(500f, 500f);
			}
			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				return true;
			}
			@Override
			public void close() {

			}
		};
		router.push(overlay);
		toggle.run();
		assertTrue(router.isTop(overlay));
		assertTrue(page.hasUnsavedChanges());
		router.pop(overlay);
	}

	@ParameterizedTest
	@ValueSource(ints = {32, 100})
	void longNamesAndLargeCatalogUseScrollableListAndFullDetail(int count) throws Exception {

		var pin = new BattleNext_PinnedVariantModel("pin_0", "Long name " + "segment ".repeat(40),
				BattleNext_VariantBuilder.fromVariant(source().api).toJson());
		Path root = directory.resolve("tournament/_pinnedVariants");
		Files.createDirectories(root);
		for (int index = 0; index < count; index++) {
			JSONObject json = pin.toJson().put("pinId", "pin_" + index);
			Files.writeString(root.resolve("pin_" + index + ".json"), json.toString());
		}
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> {}, () -> {});
		page.attach(panel(800f, 700f, new BaseCustomUIPanelPlugin()));
		assertEquals(count, store.getCatalog().size());
		page.buttonPressed("pin:tournament/_pinnedVariants/pin_0.json");
		assertTrue(labels.contains(pin.getDisplayName()));
		assertTrue(labels.stream().anyMatch(value -> value.contains("Parent")));
	}

	@Test
	void shellConfirmsUnsavedNameOnTabNavigation() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("Original", source().api);
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
				java.util.Arrays.stream(tabs).map(Object::toString).toList());
		manager.buttonPressed(tabs[2]);
		assertInstanceOf(BattleNext_PinnedVariantPage.class, getField(manager, "variantPage"));
		AtomicBoolean consumed = new AtomicBoolean();
		assertTrue(manager.handleGlobalInput(List.of(event("escape", consumed))));
		assertTrue(consumed.get());
		assertEquals(tabs[0], getField(manager, "currentPage"));
	}

	private BattleNext_PinnedVariantPage page(BattleNext_PinnedVariantStore store,
			java.util.function.Consumer<Runnable> navigation,
			java.util.function.BiConsumer<String, Runnable> deletion, Runnable back) throws Exception {
		var constructor = BattleNext_PinnedVariantPage.class.getDeclaredConstructor(Runnable.class,
				java.util.function.Consumer.class, java.util.function.BiConsumer.class, BattleNext_PinnedVariantStore.class);
		constructor.setAccessible(true);
		return constructor.newInstance(back, navigation, deletion, store);
	}

	private Object getField(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private void assertJsonEquals(Object expected, Object actual) throws Exception {
		if (expected instanceof JSONObject object) {
			JSONObject other = (JSONObject) actual;
			assertEquals(object.length(), other.length());
			if (object.length() == 0) return;
			for (String key : JSONObject.getNames(object)) {
				assertTrue(other.has(key), key);
				assertJsonEquals(object.get(key), other.get(key));
			}
		} else if (expected instanceof JSONArray array) {
			JSONArray other = (JSONArray) actual;
			assertEquals(array.length(), other.length());
			for (int index = 0; index < array.length(); index++) {
				assertJsonEquals(array.get(index), other.get(index));
			}
		} else {
			assertEquals(expected, actual);
		}
	}

	@AfterEach
	void restoreGameServices() {
		Global.setSettings(previousSettings);
		Global.setSector(previousSector);
	}

	@Test
	void registrationAndLoadCleanupAreIdempotentAndMenuIsAlwaysVisible() {

		var registry = LunaRefitManager.INSTANCE.getButtons();
		var previous = registry.stream().filter(button -> button instanceof BattleNext_PinnedVariantRefitButton).toList();
		registry.removeAll(previous);
		List<com.fs.starfarer.api.EveryFrameScript> scripts = new ArrayList<>();
		Global.setSector(proxy(SectorAPI.class, (method, args) -> {
			if ("addTransientScript".equals(method)) {
				scripts.add((com.fs.starfarer.api.EveryFrameScript) args[0]);
			}
			if ("removeTransientScript".equals(method)) {
				scripts.remove(args[0]);
			}
			return null;
		}));
		try {
			lunaEnabled = true;
			new data.scripts.BattleNextModPlugin().onApplicationLoad();
			BattleNext_PinnedVariantRefitButton.register();
			assertEquals(1, registry.stream().filter(button -> button instanceof BattleNext_PinnedVariantRefitButton).count());
			BattleNext_PinnedVariantRefitButton button = (BattleNext_PinnedVariantRefitButton)
					LunaRefitManager.getFirstButtonOfClass(BattleNext_PinnedVariantRefitButton.class);
			assertTrue(button.shouldShow(null, null, null));
			assertTrue(button.hasPanel(null, null, null));
			assertTrue(button.hasTooltip(null, null, null));
			assertTrue(button.runWhilePaused());
			assertFalse(button.isDone());
			BattleNext_PinnedVariantRefitButton.onGameLoad();
			BattleNext_PinnedVariantRefitButton.onGameLoad();
			assertEquals(List.of(button), scripts);
		} finally {
			registry.removeIf(button -> button instanceof BattleNext_PinnedVariantRefitButton);
			registry.addAll(previous);
		}
	}

	@Test
	void vanillaContextDistinguishesParentShipAndModuleAndRejectsMissingPanel() throws Throwable {

		Variant variant = source();
		FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, args) -> null);
		UIPanelAPI host = panel(640f, 560f, new BaseCustomUIPanelPlugin());
		Refit refit = new Refit(member, variant.api);
		Core core = new Core(new Tab(List.of(new Object(), refit)));
		var context = BattleNext_PinnedVariantRefitButton.contextFrom(core, host);
		assertSame(member, context.member());
		assertSame(variant.api, context.variant());
		assertSame(refit, context.refitPanel());
		assertEquals("", BattleNext_PinnedVariantRefitButton.contextError(member, variant.api));
		refit.member = null;
		context = BattleNext_PinnedVariantRefitButton.contextFrom(core, host);
		assertNull(context.member());
		assertEquals("BattleNext_ui_pin_module", BattleNext_PinnedVariantRefitButton.contextError(null, context.variant()));
		assertEquals("BattleNext_ui_refit_no_member", BattleNext_PinnedVariantRefitButton.contextError(null, null));
		assertThrows(IllegalStateException.class,
				() -> BattleNext_PinnedVariantRefitButton.contextFrom(new Core(new Tab(List.of())), host));
	}

	@ParameterizedTest
	@ValueSource(ints = {320, 480, 640})
	void panelUsesSameOwnerAndFitsNarrowAndWideWidths(int width) throws Exception {
		BattleNext_PinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), width);
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.contains("Parent"));
		assertTrue(labels.contains("Unsubmitted"));
		TextFieldAPI name = name(display);
		assertEquals("Parent - Unsubmitted", name.getText());
		assertTrue(name.getPosition().getWidth() <= width - 20f);
		display.close();
		assertFalse(buttons.get("pin_save").get());
	}

	@Test
	void openingFreezesFullParentSnapshotAndCollectionIsConsumable() throws Exception {

		Variant source = source();
		String before = BattleNext_VariantBuilder.fromVariant(source.api).toJson().toString();
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_PinnedVariantPanel display = open(source, store, 640);
		assertEquals(before, BattleNext_VariantBuilder.fromVariant(source.api).toJson().toString());
		source.json.put("displayName", "Later change");
		source.modules.get("module").setNumFluxVents(99);
		String changed = BattleNext_VariantBuilder.fromVariant(source.api).toJson().toString();
		display.buttonPressed("pin_save");
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		BattleNext_PinnedVariantModel pin = store.getAll().get(0);
		JSONObject json = pin.getVariantJson();
		assertEquals("Unsubmitted", json.getString("displayName"));
		assertEquals(7, json.getJSONObject("modules").getJSONObject("module").getInt("fluxVents"));
		assertEquals("built", json.getJSONArray("sModdedBuiltIns").getString(0));
		assertEquals("d", json.getJSONArray("suppressedMods").getString(0));
		assertEquals("sm", json.getJSONArray("sMods").getString(0));
		assertEquals("wing", json.getJSONArray("wings").getString(0));
		assertEquals("laser", json.getJSONArray("weaponGroups").getJSONObject(0).getJSONObject("weapons").getString("weapon"));
		assertFalse(buttons.get("pin_save").get());
		assertEquals(changed, BattleNext_VariantBuilder.fromVariant(source.api).toJson().toString());
		Path path = directory.resolve("tournament/_pinnedVariants/" + pin.getPinId() + ".json");
		assertTrue(Files.exists(path));
		assertEquals(pin.getPinId(), new JSONObject(Files.readString(path)).getString("pinId"));
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();
		assertTrue(provider.getOptions().stream().anyMatch(option -> option.getId().equals("pinned:" + pin.getPinId())));
		BattleNext_VariantBuilder local = BattleNext_VariantBuilder.fromVariant(provider.resolve("pinned:" + pin.getPinId()));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.putVariant(local);
		store.delete(pin.getPinId());
		assertEquals("Unsubmitted", fleet.getVariant(pin.getPinId()).build().getDisplayName());
		assertNotNull(fleet.getVariant(pin.getPinId()).build().getModuleVariant("module"));
		display.close();
	}

	@Test
	void fleetEditorEmbedsCollectionThroughItsFormalCapturePath() throws Exception {

		BattleNext_PinnedVariantStore pins = new BattleNext_PinnedVariantStore();
		pins.reload();
		BattleNext_PinnedVariantModel pin = pins.saveVariant("Fleet input", source().api);
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(),
				List.of(new BattleNext_MemberModel("pinned:" + pin.getPinId(), "Ship", "", List.of(), "", "", List.of())));
		BattleNext_FleetEditor editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
		setField(editor, "draft", fleet);
		for (String field : List.of("id", "folderName", "name", "author", "description", "skills", "tags")) {
			setField(editor, field, proxy(TextFieldAPI.class, (method, args) -> "getText".equals(method) ? "fleet" : null));
		}
		var capture = BattleNext_FleetEditor.class.getDeclaredMethod("captureDraft", boolean.class);
		capture.setAccessible(true);
		assertEquals(true, capture.invoke(editor, true));
		assertEquals(pin.getPinId(), fleet.getMembers().get(0).getVariantId());
		pins.delete(pin.getPinId());
		assertEquals("Unsubmitted", fleet.getVariant(pin.getPinId()).build().getDisplayName());
		assertNotNull(fleet.getVariant(pin.getPinId()).build().getModuleVariant("module"));
	}

	@Test
	void separateOpeningsPermitIdenticalNamesAndSnapshots() throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		Variant source = source();
		BattleNext_PinnedVariantPanel first = open(source, store, 640);
		first.buttonPressed("pin_save");
		first.close();
		BattleNext_PinnedVariantPanel second = open(source, store, 640);
		second.buttonPressed("pin_save");
		assertEquals(2, store.getAll().size());
		assertNotEquals(store.getAll().get(0).getPinId(), store.getAll().get(1).getPinId());
		assertEquals(store.getAll().get(0).getDisplayName(), store.getAll().get(1).getDisplayName());
		second.close();
	}

	@Test
	void blankNameIsRejectedAndCanBeCorrected() throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_PinnedVariantPanel display = open(source(), store, 640);
		name(display).setText("   ");
		display.buttonPressed("pin_save");
		assertTrue(labels.contains("BattleNext_ui_pin_name_required"));
		assertEquals("   ", name(display).getText());
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		name(display).setText("Named snapshot");
		display.buttonPressed("pin_save");
		assertEquals("Named snapshot", store.getAll().get(0).getDisplayName());
		display.close();
	}

	@Test
	void invalidContextAndRebuildFailureDisableSubmission() throws Exception {

		BattleNext_PinnedVariantPanel display = new BattleNext_PinnedVariantPanel(() -> {});
		display.open(null, "Specific context error");
		display.attach(panel(640f, 560f, display), 640f, 560f);
		assertTrue(labels.contains("Specific context error"));
		assertFalse(buttons.get("pin_save").get());
		display.buttonPressed("pin_save");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		Variant source = source();
		failBuild = true;
		display.open(source.api, "");
		display.attach(panel(640f, 560f, display), 640f, 560f);
		assertTrue(labels.stream().anyMatch(value -> value.contains("Unknown hull")));
		assertFalse(buttons.get("pin_save").get());
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"cycle", "missing"})
	void invalidModuleGraphIsLocalizedAndCannotBeSaved(String failure) throws Exception {
		Variant source = source();
		source.modules.put("module", "cycle".equals(failure) ? source.api : null);
		BattleNext_PinnedVariantPanel display = open(source, new BattleNext_PinnedVariantStore(), 640);
		assertFalse(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("cycle".equals(failure) ? "Cyclic variant" : "Missing module")));
		display.buttonPressed("pin_save");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		display.close();
	}

	@Test
	void textInputUsesItsOwnSessionAndConsumesKeyboardEvents() throws Exception {

		BattleNext_PinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 640);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		AtomicBoolean consumed = new AtomicBoolean();
		InputEventAPI selectAll = proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			case "isKeyboardEvent", "isKeyDownEvent", "isCtrlDown" -> true;
			case "getEventValue" -> Keyboard.KEY_A;
			default -> null;
		});
		display.processInput(List.of(selectAll));
		assertTrue(consumed.get());
		assertTrue(field.hasFocus());
		assertEquals("Parent - Unsubmitted", field.getText());
		display.close();
		assertFalse(field.hasFocus());
	}

	@Test
	void loadFailurePreservesNameAndAllowsRetry() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_PinnedVariantPanel display = open(source(), store, 640);
		name(display).setText("Keep this name");
		Files.createDirectory(directory.resolve("tournament"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants"), "blocked");
		display.buttonPressed("pin_save");
		assertEquals("Keep this name", name(display).getText());
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("Not a directory")));
		assertTrue(store.getAll().isEmpty());
		Files.delete(directory.resolve("tournament/_pinnedVariants"));
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		display.close();
	}

	@Test
	void saverFailureDoesNotPublishOrDisableTheDraft() throws Exception {

		FailingStore store = new FailingStore();
		BattleNext_PinnedVariantPanel display = open(source(), store, 640);
		name(display).setText("Retry after write failure");
		display.buttonPressed("pin_save");
		assertTrue(store.getAll().isEmpty());
		assertEquals("Retry after write failure", name(display).getText());
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("Injected write failure")));
		store.writer.fail = false;
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"callback", "reload", "leave", "detached", "left", "right"})
	void buttonOwnerReleasesItsPanelAcrossLifecyclePaths(String path) throws Exception {

		var registry = LunaRefitManager.INSTANCE.getButtons();
		var previous = registry.stream().filter(button -> button instanceof BattleNext_PinnedVariantRefitButton).toList();
		registry.removeAll(previous);
		BattleNext_PinnedVariantRefitButton.register();
		BattleNext_PinnedVariantRefitButton button = (BattleNext_PinnedVariantRefitButton)
				LunaRefitManager.getFirstButtonOfClass(BattleNext_PinnedVariantRefitButton.class);
		BattleNext_PinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 640);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		AtomicBoolean removed = new AtomicBoolean();
		UIPanelAPI host = proxy(ChildrenHost.class, (method, args) -> {
			if ("removeComponent".equals(method)) {
				removed.set(true);
			}
			if ("getChildrenCopy".equals(method)) return List.of();
			return null;
		});
		setField(button, "display", display);
		setField(button, "background", panel(640f, 560f, display));
		setField(button, "host", host);
		Global.setSector(proxy(SectorAPI.class, (method, args) -> "getCampaignUI".equals(method)
				? proxy(com.fs.starfarer.api.campaign.CampaignUIAPI.class, (name, values) ->
						"getCurrentCoreTab".equals(name) && "detached".equals(path)
								? com.fs.starfarer.api.campaign.CoreUITabId.REFIT : null) : null));
		try {
			if ("callback".equals(path)) {
				button.onPanelClose(null, null, null);
			} else if ("reload".equals(path)) {
				BattleNext_PinnedVariantRefitButton.onGameLoad();
			} else if ("left".equals(path) || "right".equals(path)) {
				button.onClick(null, null, proxy(InputEventAPI.class,
						(method, args) -> "isLMBEvent".equals(method) && "left".equals(path)), null);
			} else {
				button.advance(0f);
			}
			assertEquals(!"right".equals(path), removed.get());
			assertEquals("right".equals(path), field.hasFocus());
			assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		} finally {
			button.onPanelClose(null, null, null);
			registry.remove(button);
			registry.addAll(previous);
		}
	}

	@Test
	void malformedExistingPinIsReportedWhileNewPinIsSaved() throws Exception {
		Files.createDirectories(directory.resolve("tournament/_pinnedVariants"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants/broken.json"), "{}");
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_PinnedVariantPanel display = open(source(), store, 640);
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		assertEquals(1, store.getLoadErrors().size());
		assertTrue(labels.stream().anyMatch(value -> value.contains("broken.json")));
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"button", "escape", "outside"})
	void closePathsReleaseFocusAndNeverPersist(String path) throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_PinnedVariantPanel[] holder = new BattleNext_PinnedVariantPanel[1];
		holder[0] = new BattleNext_PinnedVariantPanel(() -> {
			closed.set(true);
			holder[0].close();
		});
		BattleNext_PinnedVariantPanel display = holder[0];
		display.open(source().api, "");
		display.attach(panel(640f, 560f, display), 640f, 560f);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		assertTrue(field.hasFocus());
		AtomicBoolean consumed = new AtomicBoolean();
		if ("button".equals(path)) {
			display.buttonPressed("pin_close");
		} else {
			display.processInput(List.of(event(path, consumed)));
			assertTrue(consumed.get());
		}
		assertTrue(closed.get());
		assertFalse(field.hasFocus());
		assertFalse(buttons.get("pin_save").get());
		display.buttonPressed("pin_save");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
	}

	private BattleNext_PinnedVariantPanel open(Variant source, BattleNext_PinnedVariantStore store, int width) {
		BattleNext_PinnedVariantPanel display = new BattleNext_PinnedVariantPanel(() -> {}, store);
		display.open(source.api, "");
		display.attach(panel(width, 560f, display), width, 560f);
		return display;
	}

	private TextFieldAPI name(BattleNext_PinnedVariantPanel display) throws Exception {
		Field field = BattleNext_PinnedVariantPanel.class.getDeclaredField("name");
		field.setAccessible(true);
		return (TextFieldAPI) field.get(display);
	}

	private void setField(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private Variant source() throws org.json.JSONException {

		ShipHullSpecAPI parentHull = hull("parent", "Parent", List.of("module"));
		ShipHullSpecAPI moduleHull = hull("child", "Child", List.of());
		Variant child = new Variant("child_variant", moduleHull);
		child.api.setNumFluxVents(7);
		Variant parent = new Variant("parent_variant", parentHull);
		parent.api.setVariantDisplayName("Unsubmitted");
		parent.api.setModuleVariant("module", child.api);
		parent.api.getSModdedBuiltIns().add("built");
		parent.api.getSuppressedMods().add("d");
		parent.api.addPermaMod("sm", true);
		parent.json.put("wings", new JSONArray().put("wing"));
		parent.json.put("weaponGroups", new JSONArray().put(new JSONObject()
				.put("mode", "LINKED").put("autofire", true)
				.put("weapons", new JSONObject().put("weapon", "laser"))));
		return parent;
	}

	private ShipHullSpecAPI hull(String id, String name, List<String> slots) {
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> id;
			case "getHullName" -> name;
			case "getSpriteName" -> "graphics/" + id + ".png";
			case "getBuiltInWings" -> List.of();
			case "getWeaponSlot" -> proxy(WeaponSlotAPI.class, (key, values) -> null);
			default -> null;
		});
		hulls.put(id, hull);
		moduleSlots.put(hull, slots);
		return hull;
	}

	private final Map<ShipHullSpecAPI, List<String>> moduleSlots = new java.util.IdentityHashMap<>();

	private class Variant {
		private final JSONObject json = new JSONObject();
		private final Map<String, ShipVariantAPI> modules = new LinkedHashMap<>();
		private final LinkedHashSet<String> builtIns = new LinkedHashSet<>();
		private final LinkedHashSet<String> suppressed = new LinkedHashSet<>();
		private final LinkedHashSet<String> sMods = new LinkedHashSet<>();
		private final LinkedHashSet<String> permaMods = new LinkedHashSet<>();
		private final LinkedHashSet<String> mods = new LinkedHashSet<>();
		private final ShipVariantAPI api;

		private Variant(String id, ShipHullSpecAPI hull) throws org.json.JSONException {

			json.put("variantId", id).put("hullId", hull.getHullId()).put("displayName", id);
			api = proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
				case "getHullSpec" -> hull;
				case "getHullVariantId" -> json.getString("variantId");
				case "getDisplayName" -> json.getString("displayName");
				case "clone" -> args[0];
				case "toJSONObject" -> new JSONObject(json.toString())
						.put("hullMods", new JSONArray(mods)).put("permaMods", new JSONArray(permaMods)).put("sMods", new JSONArray(sMods));
				case "getSModdedBuiltIns" -> builtIns;
				case "getSuppressedMods" -> suppressed;
				case "getSMods" -> sMods;
				case "getModuleSlots" -> moduleSlots.get(hull);
				case "getStationModules" -> {
					Map<String, String> ids = new LinkedHashMap<>();
					modules.forEach((slot, variant) -> ids.put(slot, variant == null ? "missing" : variant.getHullVariantId()));
					yield ids;
				}
				case "getModuleVariant" -> modules.get(args[0]);
				case "setModuleVariant" -> modules.put((String) args[0], (ShipVariantAPI) args[1]);
				case "setVariantDisplayName" -> json.put("displayName", args[0]);
				case "setNumFluxVents" -> json.put("fluxVents", args[0]);
				case "setNumFluxCapacitors" -> json.put("fluxCapacitors", args[0]);
				case "setGoalVariant" -> json.put("goalVariant", args[0]);
				case "addMod" -> mods.add((String) args[0]);
				case "addSuppressedMod" -> suppressed.add((String) args[0]);
				case "addPermaMod" -> {
					permaMods.add((String) args[0]);
					if (args.length > 1 && (Boolean) args[1]) {
						sMods.add((String) args[0]);
					}
					yield null;
				}
				case "setWingId" -> {
					JSONArray wings = json.optJSONArray("wings");
					if (wings == null) {
						wings = new JSONArray();
					}
					wings.put((Integer) args[0], args[1]);
					json.put("wings", wings);
					yield null;
				}
				case "addWeaponGroup" -> {
					WeaponGroupSpec group = (WeaponGroupSpec) args[0];
					JSONArray groups = json.optJSONArray("weaponGroups");
					if (groups == null) {
						groups = new JSONArray();
					}
					groups.put(new JSONObject().put("mode", group.getType().name())
							.put("autofire", group.isAutofireOnByDefault())
							.put("weapons", new JSONObject().put("weapon", "laser")));
					json.put("weaponGroups", groups);
					yield null;
				}
				default -> null;
			});
		}
	}

	private CustomPanelAPI panel(float width, float height, CustomUIPanelPlugin plugin) {

		PositionAPI position = position(width, height);
		Map<TooltipMakerAPI, Boolean> owned = new java.util.IdentityHashMap<>();
		CustomPanelAPI result = proxy(CustomPanelAPI.class, (method, args) -> switch (method) {
			case "getPosition" -> position;
			case "getPlugin" -> plugin;
			case "createCustomPanel" -> panel((Float) args[0], (Float) args[1], (CustomUIPanelPlugin) args[2]);
			case "createUIElement" -> {
				TooltipMakerAPI tooltip = tooltip((Float) args[0], (Float) args[1]);
				owned.put(tooltip, true);
				yield tooltip;
			}
			case "addUIElement" -> {
				assertTrue(owned.containsKey(args[0]), "UI element must be mounted by its creating panel");
				yield ((TooltipMakerAPI) args[0]).getPosition();
			}
			case "addComponent" -> ((UIComponentAPI) args[0]).getPosition();
			default -> null;
		});
		plugins.put(result, plugin);
		return result;
	}

	private TooltipMakerAPI tooltip(float width, float height) {

		PositionAPI position = position(width, height);
		float[] usedHeight = {0f};
		return proxy(TooltipMakerAPI.class, (method, args) -> switch (method) {
			case "getPosition" -> position;
			case "addTitle", "addPara", "addSectionHeading" -> {
				labels.add((String) args[0]);
				usedHeight[0] += 20f * Math.max(1, Math.ceil(((String) args[0]).length() * 7f / Math.max(1f, width))) + 8f;
				tooltipHeights.add(usedHeight[0]);
				yield label((String) args[0], width);
			}
			case "getHeightSoFar" -> usedHeight[0];
			case "setHeightSoFar" -> { usedHeight[0] = (Float) args[0]; yield null; }
			case "createRect" -> proxy(UIComponentAPI.class, (name, values) -> "getPosition".equals(name) ? position : null);
			case "addCustomDoNotSetPosition" -> args[0];
			case "shortenString" -> args[0];
			case "addCustom" -> args[0];
			case "addButton" -> {
				AtomicBoolean enabled = new AtomicBoolean(true);
				buttons.put(String.valueOf(args[1]), enabled);
				yield proxy(ButtonAPI.class, (name, values) -> {
					if ("setEnabled".equals(name)) {
						enabled.set((Boolean) values[0]);
					}
					if ("getPosition".equals(name)) return position;
					return null;
				});
			}
			default -> null;
		});
	}

	private LabelAPI label(String text, float width) {
		PositionAPI position = position(width, 20f);
		return proxy(LabelAPI.class, (method, args) -> switch (method) {
			case "getPosition" -> position;
			case "computeTextWidth" -> ((String) args[0]).length() * 7f;
			case "computeTextHeight" -> 20f;
			case "getText" -> text;
			default -> null;
		});
	}

	private PositionAPI position(float width, float height) {
		return proxy(PositionAPI.class, (method, args) -> switch (method) {
			case "getWidth" -> width;
			case "getHeight" -> height;
			case "containsEvent" -> false;
			default -> null;
		});
	}

	private InputEventAPI event(String type, AtomicBoolean consumed) {
		return proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			case "isKeyboardEvent", "isKeyDownEvent" -> "escape".equals(type);
			case "getEventValue" -> Keyboard.KEY_ESCAPE;
			case "isMouseDownEvent" -> "outside".equals(type);
			default -> null;
		});
	}

	private static <T> T proxy(Class<T> type, Handler handler) {

		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
			if ("equals".equals(method.getName())) return instance == args[0];
			if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
			if ("clone".equals(method.getName())) return instance;
			Object result = handler.apply(method.getName(), args == null ? new Object[0] : args);
			if (result != null && method.getReturnType() == void.class) return null;
			if (result != null) return result;
			if (type == PositionAPI.class && method.getReturnType() == PositionAPI.class) return instance;
			if (method.getReturnType() == boolean.class) return false;
			if (method.getReturnType() == float.class) return 0f;
			if (method.getReturnType() == int.class) return 0;
			return null;
		}));
	}

	private interface Handler {
		Object apply(String method, Object[] args) throws Throwable;
	}

	public interface ChildrenHost extends UIPanelAPI {
		List<Object> getChildrenCopy();
	}

	private static class FailingStore extends BattleNext_PinnedVariantStore {
		private final FailingSaver writer = new FailingSaver();
		private boolean failDelete;
		private boolean failLoad;

		@Override
		public void reload() throws IOException, org.json.JSONException {
			if (failLoad) throw new IOException("Injected load failure");
			super.reload();
		}

		@Override
		public void deleteFile(String path) throws IOException, org.json.JSONException {
			if (failDelete) throw new IOException("Injected delete failure");
			super.deleteFile(path);
		}

		@Override
		protected BattleNext_PinnedVariantSaver saver() {
			return writer;
		}
	}

	private static class FailingSaver extends BattleNext_PinnedVariantSaver {
		private boolean fail = true;

		@Override
		public String save(BattleNext_PinnedVariantModel model) throws IOException, org.json.JSONException {
			if (fail) throw new IOException("Injected write failure");
			return super.save(model);
		}
	}

	public static class Core {
		private final Tab tab;
		public Core(Tab tab) {
			this.tab = tab;
		}
		public Tab getCurrentTab() {
			return tab;
		}
	}

	public static class Tab {
		private final List<Object> children;
		public Tab(List<Object> children) {
			this.children = children;
		}
		public List<Object> getChildrenCopy() {
			return children;
		}
	}

	public static class Refit {
		private FleetMemberAPI member;
		private final ShipVariantAPI variant;
		public Refit(FleetMemberAPI member, ShipVariantAPI variant) {
			this.member = member;
			this.variant = variant;
		}
		public void syncWithCurrentVariant() {

		}
		public FleetMemberAPI getMember() {
			return member;
		}
		public Refit getShipDisplay() {
			return this;
		}
		public ShipVariantAPI getCurrentVariant() {
			return variant;
		}
	}
}
