package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip.VariantSection;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionSelectionMode;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestUiFixture;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_VariantPropertiesTooltipIntegrationTest extends BattleNext_TestUiFixture {

	private static final String SELECTION = "variant_50%";
	private final RecordingAppender appender = new RecordingAppender();
	private Variant root;
	private String failedKind = "";
	private String failureMode = "";
	private Logger logger;
	private Level previousLevel;
	private boolean previousAdditivity;

	@BeforeEach
	void configureVariantProperties() throws Exception {

		ShipHullSpecAPI parentHull = configuredHull("root", "Root", List.of("a", "b", "c"), true);
		ShipHullSpecAPI childHull = configuredHull("child_a", "Same module", List.of("nested"), false);
		ShipHullSpecAPI otherHull = configuredHull("child_b", "Same module", List.of(), false);
		root = variant(SELECTION, parentHull);
		root.getApi().setNumFluxCapacitors(14);
		root.getApi().setNumFluxVents(22);
		root.getJson().put("weaponGroups", new JSONArray().put(new JSONObject()
				.put("mode", "LINKED").put("weapons", new JSONObject()
						.put("builtin", "b").put("slot_1", "b").put("slot_2", "a"))));
		root.getJson().put("wings", new JSONArray().put("b").put("").put("b").put("a").put(JSONObject.NULL));
		Variant child = variant("first_module", childHull);
		child.getApi().setNumFluxCapacitors(99);
		child.getApi().setNumFluxVents(99);
		child.getJson().put("wings", new JSONArray().put("module_only_wing"));
		child.getJson().put("weaponGroups", new JSONArray().put(new JSONObject().put("mode", "LINKED")
				.put("weapons", new JSONObject().put("child_slot", "module_only_weapon"))));
		child.getApi().setModuleVariant("nested", variant("nested_module", otherHull).getApi());
		root.getApi().setModuleVariant("a", child.getApi());
		root.getApi().setModuleVariant("b", variant("second_module", childHull).getApi());
		root.getApi().setModuleVariant("c", variant("third_module", otherHull).getApi());
		root.getApi().addMod("ordinary");
		root.getApi().addMod("hidden");
		root.getApi().addMod("picker_hidden");
		root.getApi().addPermaMod("built_s", true);
		root.getApi().getSModdedBuiltIns().add("built_s");
		root.getApi().getSuppressedMods().add("damaged");
		root.getApi().addPermaMod("permanent");
		registeredVariants.put(SELECTION, root.getApi());

		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			String name = method.getName();
			if (name.equals("getDescription")) throw new AssertionError("Variant tooltip reads fitted properties");
			if (name.equals("getAllVariantIds")) return List.of(SELECTION);
			if (name.equals("getWeaponSpec")) {
				if (failedKind.equals("weapon")) return failedResource();
				return proxy(WeaponSpecAPI.class, (key, values) -> key.equals("getWeaponName") ? "Same weapon" : null);
			}
			if (name.equals("getFighterWingSpec")) {
				if (failedKind.equals("wing")) return failedResource();
				return proxy(FighterWingSpecAPI.class, (key, values) -> key.equals("getWingName") ? "Same wing" : null);
			}
			if (name.equals("getHullModSpec")) {
				if (failedKind.equals("hullmod")) return failedResource();
				String id = (String) arguments[0];
				return proxy(HullModSpecAPI.class, (key, values) -> switch (key) {
					case "getDisplayName" -> "Mod " + id;
					case "isHiddenEverywhere" -> id.equals("hidden");
					case "isHidden" -> id.equals("picker_hidden");
					case "hasTag" -> id.equals("damaged") && values[0].equals(Tags.HULLMOD_DMOD);
					default -> null;
				});
			}
			try {
				return method.invoke(previous, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_Logger.fields();
		logger = Global.getLogger(BattleNext_Logger.class);
		previousLevel = logger.getLevel();
		previousAdditivity = logger.getAdditivity();
		logger.setLevel(Level.INFO);
		logger.setAdditivity(false);
		logger.addAppender(appender);
	}

	@AfterEach
	void restoreLogging() {
		logger.removeAppender(appender);
		logger.setLevel(previousLevel);
		logger.setAdditivity(previousAdditivity);
	}

	@Test
	void countsOnlyTheRootFittedEquipmentAndGroupsByIdentityIncludingBuiltIns() {

		List<String> expected = List.of("Properties", SELECTION,
				"BattleNext_variant_hull_size: frigate - BattleNext_variant_base_deployment_points: 5.0",
				"BattleNext_variant_flux_capacitors: 14", "BattleNext_variant_flux_vents: 22",
				"BattleNext_variant_weapons: 3", "Same weapon ×1", "Same weapon ×2",
				"BattleNext_variant_fighter_wings: 3", "Same wing ×1", "Same wing ×2",
				"BattleNext_variant_modules: 3", "Same module ×2", "Same module ×1",
				"BattleNext_variant_hullmods: 5",
				"Mod built_s [BattleNext_variant_built_in, BattleNext_variant_permanent, BattleNext_variant_s_mod, BattleNext_variant_built_in_s_mod]",
				"Mod damaged [BattleNext_variant_suppressed, BattleNext_variant_d_mod]",
				"Mod ordinary", "Mod permanent [BattleNext_variant_permanent]", "Mod picker_hidden");
		assertEquals(expected, show(new BattleNext_VariantSourceProvider(), SELECTION));
		assertTrue(appender.events.isEmpty());
	}

	@Test
	void reloadsPinnedSnapshotsAndUsesTheirFormalSelectionIdentity() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("Pinned properties", root.getApi());
		List<String> registered = show(new BattleNext_VariantSourceProvider(), SELECTION);
		root.getApi().setNumFluxCapacitors(99);
		store.reload();
		String id = BattleNext_VariantSourceProvider.PINNED_PREFIX + pin.getPinId();
		List<String> pinned = show(new BattleNext_VariantSourceProvider(store), id);
		assertEquals(id, pinned.get(1));
		assertEquals(registered.subList(2, registered.size()), pinned.subList(2, pinned.size()));
		assertEquals(99, root.getApi().getNumFluxCapacitors());
		assertEquals(14, store.resolve(pin.getPinId()).getNumFluxCapacitors());
	}

	@Test
	void emptyVariantRetainsEveryZeroTotalInTheFinalSummary() throws Exception {

		Variant empty = variant("empty", configuredHull("empty_hull", "Empty", List.of(), false));
		registeredVariants.put("empty", empty.getApi());
		assertEquals(List.of("Properties", "empty",
				"BattleNext_variant_hull_size: frigate - BattleNext_variant_base_deployment_points: 5.0",
				"BattleNext_variant_flux_capacitors: 0", "BattleNext_variant_flux_vents: 0",
				"BattleNext_variant_weapons: 0 - BattleNext_variant_fighter_wings: 0 - BattleNext_variant_modules: 0 - BattleNext_variant_hullmods: 0"),
				show(new BattleNext_VariantSourceProvider(), "empty"));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 4, 8, 15})
	void independentSectionsAndEmptySummaryUseTheirDeclaredColorsAndOrder(int mask) throws Exception {

		String id = "combination_" + mask;
		Variant selected = variant(id, configuredHull(id + "_hull", "Combination", List.of("module"), false));
		if ((mask & 1) != 0) {
			selected.getApi().addWeapon("slot", "weapon");
		}
		if ((mask & 2) != 0) {
			selected.getApi().setWingId(0, "wing");
		}
		if ((mask & 4) != 0) {
			selected.getApi().setModuleVariant("module",
					variant("module_variant", configuredHull("module_hull", "Module", List.of(), false)).getApi());
		}
		if ((mask & 8) != 0) {
			selected.getApi().addMod("ordinary");
		}
		registeredVariants.put(id, selected.getApi());
		TooltipMakerAPI hover = tooltip(360f, 0f, false);
		new BattleNext_VariantSourceProvider().createTooltip(hover, new OptionChoice(id, "Properties"));
		List<String> expected = new ArrayList<>(List.of("Properties", id,
				"BattleNext_variant_hull_size: frigate - BattleNext_variant_base_deployment_points: 5.0",
				"BattleNext_variant_flux_capacitors: 0", "BattleNext_variant_flux_vents: 0"));
		List<String> titles = List.of("BattleNext_variant_weapons", "BattleNext_variant_fighter_wings",
				"BattleNext_variant_modules", "BattleNext_variant_hullmods");
		List<String> rows = List.of("Same weapon ×1", "Same wing ×1", "Module ×1", "Mod ordinary");
		Set<String> headings = new HashSet<>();
		List<String> empty = new ArrayList<>();
		for (int index = 0; index < titles.size(); index++) {
			if ((mask & (1 << index)) != 0) {
				String heading = titles.get(index) + ": 1";
				headings.add(heading);
				expected.add(heading);
				expected.add(rows.get(index));
			} else {
				empty.add(titles.get(index) + ": 0");
			}
		}
		String summary = String.join(" - ", empty);
		if (!empty.isEmpty()) {
			expected.add(summary);
		}
		List<LabelAPI> rendered = tooltips.get(hover).getComponents().stream()
				.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
		assertEquals(expected, rendered.stream().map(LabelAPI::getText).toList());
		assertEquals("small-orbitron", labelFonts.get(rendered.get(0).getPosition()));
		assertEquals(BattleNext_UiTheme.ACCENT, labelColors.get(rendered.get(0).getPosition()));
		assertEquals("small-insignia", labelFonts.get(rendered.get(1).getPosition()));
		assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(rendered.get(1).getPosition()));
		for (int index = 2; index < rendered.size(); index++) {
			LabelAPI label = rendered.get(index);
			boolean heading = headings.contains(label.getText());
			boolean footer = !empty.isEmpty() && index == rendered.size() - 1;
			assertEquals("default", labelFonts.get(label.getPosition()));
			assertEquals(footer ? BattleNext_UiTheme.MUTED : heading ? BattleNext_UiTheme.ACCENT : BattleNext_UiTheme.TEXT,
					labelColors.get(label.getPosition()));
			var previous = rendered.get(index - 1).getPosition();
			float gap = previous.getY() - label.getPosition().getY() - label.getPosition().getHeight();
			boolean row = index > 4 && !heading && !footer;
			assertEquals(index == 4 || row ? 2f : 8f, gap);
		}
		if (!empty.isEmpty()) {
			assertEquals(summary, rendered.get(rendered.size() - 1).getText());
			if (mask == 0) {
				assertTrue(rendered.get(rendered.size() - 1).getPosition().getHeight() > paragraphLineHeight);
			}
		}
		var components = tooltips.get(hover).getComponents();
		assertEquals(8f, components.get(components.size() - 1).getPosition().getHeight());
		assertTrue(appender.events.isEmpty());
	}

	@Test
	void structuredSectionKeepsAnImmutableSnapshotOfItsRowsAndExplicitCount() {
		List<String> rows = new ArrayList<>(List.of("Name with : 0 ×2", "Other name ×1"));
		VariantSection section = new VariantSection("Weapons", 3, rows);
		rows.clear();
		assertEquals("Weapons", section.title());
		assertEquals(3, section.count());
		assertEquals(List.of("Name with : 0 ×2", "Other name ×1"), section.rows());
		assertThrows(UnsupportedOperationException.class, () -> section.rows().clear());
	}

	@ParameterizedTest
	@CsvSource({"weapon,missing", "weapon,throw", "wing,missing", "wing,throw",
			"hullmod,missing", "hullmod,throw", "module,missing"})
	void resourceFailuresRetainIdentityAndCountsAndLogTheirSelectionContext(String kind, String mode) throws Exception {

		failedKind = kind;
		failureMode = mode;
		if (kind.equals("module")) root.getModules().put("a", null);
		List<String> values = show(new BattleNext_VariantSourceProvider(), SELECTION);
		String unavailable = "BattleNext_common_unavailable: ";
		if (kind.equals("weapon")) {
			assertTrue(values.contains("BattleNext_variant_weapons: 3"));
			assertTrue(values.contains(unavailable + "b ×2"));
		}
		if (kind.equals("wing")) {
			assertTrue(values.contains("BattleNext_variant_fighter_wings: 3"));
			assertTrue(values.contains(unavailable + "b ×2"));
		}
		if (kind.equals("hullmod")) {
			assertTrue(values.contains("BattleNext_variant_hullmods: 6"));
			assertTrue(values.stream().anyMatch(value -> value.startsWith(unavailable + "built_s [")));
		}
		if (kind.equals("module")) {
			assertTrue(values.contains("BattleNext_variant_modules: 3"));
			assertTrue(values.contains(unavailable + "missing ×1"));
		}
		var entries = new ArrayList<JSONObject>();
		for (LoggingEvent event : appender.events) {
			JSONObject entry = new JSONObject(event.getRenderedMessage());
			if (!entry.getString("source").equals(BattleNext_VariantSourceProvider.class.getName())) continue;
			entries.add(entry);
			assertEquals(Level.WARN, event.getLevel());
			assertEquals(SELECTION, entry.getJSONObject("fields").getString("variant_id"));
			assertEquals("variant.tooltip." + kind + ".unavailable", entry.getString("event"));
			if (!kind.equals("weapon")) assertTrue(entry.has("exception"));
		}
		assertEquals(kind.equals("hullmod") ? 6 : kind.equals("module") ? 1 : 2, entries.size());
		if (kind.equals("module")) assertEquals("a", entries.get(0).getJSONObject("fields").getString("module_slot"));
	}

	@Test
	void longNamesAndPercentCharactersRemainCompleteAndGrowTheNativeBody() {

		String fullName = "Long weapon 50% with %s " + "complete name ".repeat(40);
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getWeaponSpec")) {
				return proxy(WeaponSpecAPI.class, (name, values) -> name.equals("getWeaponName") ? fullName : null);
			}
			return method.invoke(previous, arguments);
		}));
		TooltipMakerAPI hover = tooltip(360f, 0f, false);
		new BattleNext_VariantSourceProvider().createTooltip(hover, new OptionChoice(SELECTION, "Properties"));
		assertTrue(labels.contains(fullName + " ×1"));
		assertTrue(labels.contains(fullName + " ×2"));
		assertTrue(tooltips.get(hover).getUsedHeight() > 400f);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void pickerCardsRetainNativeTooltipBindingsThroughSearchAndReopening(boolean pinned) throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		String id = SELECTION;
		if (pinned) {
			id = BattleNext_VariantSourceProvider.PINNED_PREFIX + store.saveVariant("Pinned properties", root.getApi()).getPinId();
		}
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI owner = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = owner.createUIElement(32f, 28f, false);
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, owner, 32f, 28f, "...", 0f,
				new BattleNext_VariantSourceProvider(store), OptionSelectionMode.SINGLE, List::of, ignored -> {});
		picker.attach(host, owner);
		owner.addUIElement(host).inTL(0f, 0f);
		for (int cycle = 0; cycle < 2; cycle++) {
			CustomPanelAPI row = (CustomPanelAPI) field(picker, "row");
			plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
			CustomPanelAPI popup = (CustomPanelAPI) field(picker, "popup");
			BattleNext_TextField search = (BattleNext_TextField) field(picker, "search");
			search.setText(id);
			plugins.get(popup).advance(0f);
			CustomPanelAPI gridPanel = (CustomPanelAPI) field(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			var tile = tooltips.get(grid).getComponents().get(0);
			assertEquals(TooltipMakerAPI.TooltipLocation.RIGHT, tooltipBindings.get(tile).location());
			assertFalse(tooltipBindings.get(tile).recreateEveryFrame());
			assertSame(tooltips.get(grid), tooltipBindings.get(tile).owner());
			assertEquals(360f, attachedTooltips.get(tile).getTooltipWidth(null));
			TooltipMakerAPI hover = tooltip(360f, 0f, false);
			attachedTooltips.get(tile).createTooltip(hover, false, null);
			assertTrue(labels.contains("BattleNext_variant_weapons: 3"));
			assertTrue(labels.contains("BattleNext_variant_modules: 3"));
			plugins.get(popup).buttonPressed(buttonIds.get("CANCEL"));
			assertTrue(router.isEmpty());
			assertFalse(router.getSession().hasInputState());
		}
		picker.close();
		assertTrue(tooltips.get(host).getComponents().isEmpty());
	}

	private Object field(Object target, String name) throws Exception {
		var field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private Object failedResource() {
		if (failureMode.equals("throw")) throw new IllegalArgumentException("Resource read failed");
		return null;
	}

	private List<String> show(BattleNext_VariantSourceProvider provider, String id) {
		labels.clear();
		TooltipMakerAPI hover = tooltip(360f, 0f, false);
		provider.createTooltip(hover, new OptionChoice(id, "Properties"));
		return List.copyOf(labels);
	}

	private ShipHullSpecAPI configuredHull(String id, String name, List<String> slots, boolean builtIn) {

		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullId" -> id;
			case "getHullName" -> name;
			case "getHullSize" -> HullSize.FRIGATE;
			case "getSuppliesToRecover" -> 5f;
			case "getSpriteName" -> "graphics/" + id + ".png";
			case "getBuiltInMods" -> builtIn ? List.of("built_s") : List.of();
			case "getBuiltInWings" -> builtIn ? List.of("b") : List.of();
			case "getBuiltInWeapons" -> new HashMap<>(builtIn ? Map.of("builtin", "b") : Map.of());
			case "getWeaponSlot", "getWeaponSlotAPI" -> proxy(WeaponSlotAPI.class, (key, values) -> switch (key) {
				case "getId" -> args[0];
				case "isStationModule" -> slots.contains(args[0]);
				default -> null;
			});
			default -> null;
		});
		hulls.put(id, hull);
		return hull;
	}

	private static class RecordingAppender extends AppenderSkeleton {
		private final List<LoggingEvent> events = new ArrayList<>();

		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
		}

		@Override
		public void close() {

		}

		@Override
		public boolean requiresLayout() {
			return false;
		}
	}
}
