package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionSelectionMode;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_HullModSourceProviderIntegrationTest extends BattleNext_TestVariantFixture {

	private final Map<String, Mod> mods = new LinkedHashMap<>();
	private final AtomicReference<ShipVariantAPI> current = new AtomicReference<>();
	private final RecordingAppender appender = new RecordingAppender();
	private Mod installed;
	private Logger logger;
	private Level previousLevel;
	private boolean previousAdditivity;

	@BeforeEach
	void configureHullmods() {

		mods.put("zeta", new Mod("zeta", "Same name", false));
		mods.put("hidden", new Mod("hidden", "Hidden", false));
		mods.put("damage", new Mod("damage", "Damage", true));
		mods.put("built", new Mod("built", "Same name", false));
		mods.put("installed", new Mod("installed", "Alpha installed", false));
		mods.put("sm", new Mod("sm", "Zulu S-mod", false));
		mods.put("d", new Mod("d", "Zulu damaged", true));
		installed = mods.get("installed");
		current.set(context(HullSize.FRIGATE, false, List.of("installed", "built", "damage")));
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getAllHullModSpecs")) {
				return mods.values().stream().map(mod -> mod.api).toList();
			}
			if (method.getName().equals("getHullModSpec")) {
				Mod mod = mods.get(arguments[0]);
				return mod == null ? null : mod.api;
			}
			if (method.getName().equals("getHullSpec")) {
				ShipHullSpecAPI original = hulls.get(arguments[0]);
				return original == null ? null : decorateHull(original);
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
	void providersSelectTheirCurrentHullmodsWithTheSharedSelectionContract() {
		assertEquals(List.of("installed", "damage", "hidden", "built", "zeta", "sm", "d"), ids(provider("all")));
		assertEquals(List.of("damage", "d"), ids(provider("dmod")));
		assertEquals(List.of("installed", "built"), ids(provider("smod")));
		for (String kind : List.of("all", "smod", "dmod")) {
			var source = provider(kind);
			assertEquals(1, source.getMaxStack("installed"));
			assertEquals(360f, source.getTooltipWidth());
			assertTrue(source.expectsIcon());
			assertEquals("graphics/installed.png", source.getIconPath("installed"));
		}
	}

	@Test
	void supplierChangesAffectSourcesDescriptionsAndSuitabilityImmediately() {

		var source = provider("smod");
		assertEquals(List.of("installed", "built"), ids(source));
		current.set(context(HullSize.CAPITAL_SHIP, true, List.of("zeta")));
		assertEquals(List.of("built", "zeta"), ids(source));
		var text = textValues(render(provider("all"), "installed"));
		assertTrue(text.stream().anyMatch(value -> value.contains("First: CAPITAL_SHIP 20%.")));
		mods.get("damage").notPhase = true;
		assertFalse(BattleNext_HullModSourceProvider.isSuitableFor(current.get(), mods.get("damage").api));
		assertTrue(textValues(render(provider("dmod"), "damage")).contains(BattleNext_TestTextCatalog.text("Refit", "BattleNext_refit_d_mod_unsuitable")));
		current.set(context(HullSize.FRIGATE, false, List.of()));
		assertTrue(BattleNext_HullModSourceProvider.isSuitableFor(current.get(), mods.get("damage").api));
		assertTrue(textValues(render(provider("all"), "damage")).contains(BattleNext_TestTextCatalog.text("Refit", "BattleNext_refit_d_mod_suitable")));
	}

	@Test
	void emptyContextRetainsRegisteredSourcesAndHeaderUntilTheDraftIsReady() {
		current.set(null);
		assertEquals(7, provider("all").getOptions().size());
		assertEquals(2, provider("dmod").getOptions().size());
		assertEquals(List.of(), ids(provider("smod")));
		assertEquals(List.of("Alpha installed", "installed"), textValues(render(provider("all"), "installed")));
		assertTrue(installed.script.calls.isEmpty());
		assertTrue(appender.events.isEmpty());
	}

	@ParameterizedTest
	@EnumSource(value = HullSize.class, names = {"FRIGATE", "DESTROYER", "CRUISER", "CAPITAL_SHIP"})
	void nativeFullDescriptionsAndSModEffectsUseTheCurrentHullSize(HullSize size) {

		current.set(context(size, false, List.of("installed")));
		TooltipMakerAPI tooltip = render(provider("smod"), "installed");
		List<String> values = textValues(tooltip);
		assertEquals(List.of("Alpha installed", "installed", "First: " + size + " 20%.\nSecond: Literal %s, %d and 50%."),
				values.subList(0, 3));
		assertTrue(values.contains("Enhanced: " + size + " 30%.\nComplete extra effect 40%."));
		assertTrue(values.contains("S-mod bonus"));
		assertEquals(1, values.stream().filter(value -> value.startsWith("Enhanced:")).count());
		assertEquals(List.of("description:" + size, "post_description:" + size, "s_mod:" + size), installed.script.calls);
		var components = tooltips.get(tooltip).getComponents();
		assertEquals("small-orbitron", labelFonts.get(components.get(0).getPosition()));
		assertEquals(BattleNext_UiTheme.ACCENT, labelColors.get(components.get(0).getPosition()));
		assertEquals("small-insignia", labelFonts.get(components.get(1).getPosition()));
		assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(components.get(1).getPosition()));
		for (int index : List.of(2)) {
			assertEquals("default", labelFonts.get(components.get(index).getPosition()));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(components.get(index).getPosition()));
		}
		assertEquals(5f, gap(components.get(0).getPosition(), components.get(1).getPosition()));
		assertEquals(8f, gap(components.get(1).getPosition(), components.get(2).getPosition()));
		assertEquals(8f, components.get(components.size() - 1).getPosition().getHeight());
		assertEquals(installed.mainText, labelPatterns.get(components.get(2).getPosition()));
		assertEquals(Arrays.asList(size + " 20%", "Literal %s, %d and 50%", null, null, null, null, null, null, null, null),
				labelHighlights.get(components.get(2).getPosition()));
		assertEquals(List.of(Misc.getHighlightColor()), labelHighlightColors.get(components.get(2).getPosition()));
		assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), installed.script.parameterIndices);
		assertTrue(appender.events.isEmpty());
	}

	@Test
	void armoredWeaponMountValuesRemainNativePatternParametersAndHighlights() {

		installed.mainText = "Increases durability by %s and reduces recoil by %s, but decreases turn rate by %s. "
				+ "Also increases armor by %s.";
		installed.script.parameterValues.putAll(Map.of(0, "100%", 1, "25%", 2, "25%", 3, "10%"));
		TooltipMakerAPI tooltip = render(provider("all"), "installed");
		LabelAPI description = textLabels(tooltip).get(2);
		assertEquals("Increases durability by 100% and reduces recoil by 25%, but decreases turn rate by 25%. "
				+ "Also increases armor by 10%.", description.getText());
		assertEquals(installed.mainText, labelPatterns.get(description.getPosition()));
		assertEquals(Arrays.asList("100%", "25%", "25%", "10%", null, null, null, null, null, null),
				labelHighlights.get(description.getPosition()));
		assertEquals(List.of(Misc.getHighlightColor()), labelHighlightColors.get(description.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(description.getPosition()));
	}

	@Test
	void indexedRepeatedAndEscapedPlaceholdersUseOneCompletePatternAcrossParagraphs() {

		installed.mainText = "Reordered %2$s then %1$s again %1$s.\nLiteral 100%%.";
		TooltipMakerAPI tooltip = render(provider("all"), "installed");
		LabelAPI description = textLabels(tooltip).get(2);
		assertEquals("Reordered Literal %s, %d and 50% then FRIGATE 20% again FRIGATE 20%.\nLiteral 100%.",
				description.getText());
		assertEquals(installed.mainText, labelPatterns.get(description.getPosition()));
		assertEquals("FRIGATE 20%", labelHighlights.get(description.getPosition()).get(0));
		assertEquals("Literal %s, %d and 50%", labelHighlights.get(description.getPosition()).get(1));
		assertEquals(List.of(Misc.getHighlightColor()), labelHighlightColors.get(description.getPosition()));
		assertTrue(appender.events.isEmpty());
	}

	@Test
	void customCallbacksKeepTheirTablesAndStyleWhileFollowingTextRestoresDefaults() {

		installed.script.customPost = true;
		installed.script.customSMod = true;
		TooltipMakerAPI tooltip = render(provider("all"), "installed");
		assertTrue(textValues(tooltip).contains("Custom post 50%"));
		assertTrue(textValues(tooltip).contains("Custom S-mod 60%"));
		var table = tooltips.get(tooltip).getComponents().stream().filter(UIPanelAPI.class::isInstance)
				.map(UIPanelAPI.class::cast).findFirst().orElseThrow();
		assertEquals(List.of("Size", 180f, "Value", 180f), tables.get(table).getColumns());
		assertEquals(List.of(List.of("FRIGATE", "75%")), tables.get(table).getRows());
		assertSame(tooltip.getPosition(), geometry.get(table.getPosition()).getParent());
		LabelAPI customSMod = textLabels(tooltip).stream().filter(label -> label.getText().equals("Custom S-mod 60%"))
				.findFirst().orElseThrow();
		assertEquals("default", labelFonts.get(customSMod.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(customSMod.getPosition()));
		tooltip.addPara("Following public text 70%", 8f);
		LabelAPI following = (LabelAPI) tooltip.getPrev();
		assertEquals("default", labelFonts.get(following.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(following.getPosition()));
		assertTrue(tooltips.get(tooltip).getUsedHeight() > 100f);
	}

	@Test
	void customReplacementAndSModSectionWorkWithoutStockDescriptionFormats() {
		installed.script.showDescription = false;
		installed.script.customPost = true;
		installed.script.customSMod = true;
		installed.sModText = "";
		var text = textValues(render(provider("all"), "installed"));
		assertTrue(text.contains("Custom post 50%"));
		assertTrue(text.contains("Custom S-mod 60%"));
		assertFalse(text.stream().anyMatch(value -> value.startsWith("First:")));
		assertTrue(appender.events.isEmpty());
	}

	@Test
	void builtInWithoutExtraEffectsUsesTheExistingExplanation() {
		mods.get("built").sModText = "";
		assertTrue(textValues(render(provider("all"), "built")).contains(BattleNext_TestTextCatalog.text("Refit", "BattleNext_refit_s_mod_no_effect")));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\r\n"})
	void emptyBasicDescriptionsPreserveTheRemainingEffectChain(String description) {
		installed.mainText = description;
		installed.script.customPost = true;
		var text = textValues(render(provider("all"), "installed"));
		assertEquals(List.of("Alpha installed", "installed", "Custom post 50%"), text.subList(0, 3));
		assertTrue(text.contains("Enhanced: FRIGATE 30%.\nComplete extra effect 40%."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"description", "post_description", "s_mod", "notes"})
	void eachFailedPhaseKeepsEarlierOutputAndContinuesWithRestoredPublicText(String phase) throws Exception {

		installed.script.customPost = true;
		installed.script.customSMod = true;
		installed.failedPhase = phase;
		TooltipMakerAPI tooltip = tooltip(360f, 0f, false);
		BattleNext_EquipmentTooltip.renderHullmod(tooltip, "Alpha installed", "installed", installed.api,
				HullSize.FRIGATE, "live_variant", () -> {
					if (phase.equals("notes")) throw new IllegalStateException("Failed notes 50%");
					return List.of("Final auxiliary note 80%");
				});
		List<String> text = textValues(tooltip);
		assertEquals(List.of("Alpha installed", "installed"), text.subList(0, 2));
		if (!phase.equals("description")) assertTrue(text.stream().anyMatch(value -> value.contains("First: FRIGATE 20%.")));
		if (!phase.equals("post_description")) assertTrue(text.contains("Custom post 50%"));
		if (!phase.equals("s_mod")) assertTrue(text.contains("Custom S-mod 60%"));
		if (!phase.equals("notes")) assertEquals("Final auxiliary note 80%", text.get(text.size() - 1));
		assertEquals(1, appender.events.size());
		LoggingEvent event = appender.events.get(0);
		assertEquals(Level.WARN, event.getLevel());
		JSONObject entry = new JSONObject(event.getRenderedMessage());
		assertEquals("hullmod.description.degraded", entry.getString("event"));
		JSONObject fields = entry.getJSONObject("fields");
		assertEquals("installed", fields.getString("hullmod_id"));
		assertEquals("FRIGATE", fields.getString("hull_size"));
		assertEquals("live_variant", fields.getString("variant_id"));
		assertEquals(phase, fields.getString("phase"));
		assertTrue(fields.isNull("ship"));
		assertFalse(fields.getBoolean("is_for_mod_spec"));
		assertEquals(360f, fields.getDouble("width"));
		LabelAPI diagnostic = textLabels(tooltip).stream().filter(label -> label.getText().startsWith(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_load_failed")))
				.findFirst().orElseThrow();
		assertEquals("default", labelFonts.get(diagnostic.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(diagnostic.getPosition()));
	}

	@Test
	void unknownHullmodHasAStableHeaderAndDiagnosticAndMissingSourceIsOmitted() {
		var source = provider("all");
		assertEquals("", source.getIconPath("unknown"));
		assertEquals(List.of("Choice", "unknown", "java.lang.IllegalArgumentException: Unknown hullmod: unknown"),
				textValues(render(source, "unknown")));
		current.set(context(HullSize.FRIGATE, false, List.of("unknown", "installed")));
		assertEquals(List.of("installed", "built"), ids(provider("smod")));
		assertEquals(3, appender.events.size());
	}

	@ParameterizedTest
	@ValueSource(strings = {"all", "smod", "dmod"})
	void nativeBindingsSurviveFilteringAndRepeatedOpenClose(String kind) throws Exception {

		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = root.createUIElement(32f, 28f, false);
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, root, 32f, 28f, "...", 0f,
				provider(kind), OptionSelectionMode.MULTIPLE, List::of, ids -> {});
		picker.attach(host, root);
		root.addUIElement(host).inTL(0f, 0f);
		String id = kind.equals("dmod") ? "damage" : "installed";
		for (int cycle = 0; cycle < 3; cycle++) {
			var open = picker.getClass().getDeclaredMethod("open");
			open.setAccessible(true);
			open.invoke(picker);
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			BattleNext_TextField search = (BattleNext_TextField) getField(picker, "search");
			search.setText(id);
			plugins.get(popup).advance(0f);
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			var card = tooltips.get(grid).getComponents().get(0);
			assertEquals(TooltipMakerAPI.TooltipLocation.RIGHT, tooltipBindings.get(card).location());
			assertFalse(tooltipBindings.get(card).recreateEveryFrame());
			assertSame(tooltips.get(grid), tooltipBindings.get(card).owner());
			var creator = attachedTooltips.get(card);
			assertEquals(360f, creator.getTooltipWidth(null));
			TooltipMakerAPI content = tooltip(360f, 0f, false);
			creator.createTooltip(content, false, null);
			assertEquals(id, textValues(content).get(1));
			assertTrue(textValues(content).stream().anyMatch(value -> value.contains("Second: Literal %s, %d and 50%.")));
			search.setText("unmatched");
			plugins.get(popup).advance(0f);
			assertTrue(removedComponents.contains(gridPanel));
			plugins.get(popup).buttonPressed(buttonIds.get("CANCEL"));
			assertTrue(router.isEmpty());
			assertFalse(router.getSession().hasInputState());
		}
		picker.close();
		assertTrue(tooltips.get(host).getComponents().isEmpty());
	}

	private BattleNext_HullModSourceProvider provider(String kind) {
		return switch (kind) {
			case "smod" -> new BattleNext_RefitSModSourceProvider(current::get);
			case "dmod" -> new BattleNext_RefitDModSourceProvider(current::get);
			default -> new BattleNext_HullModSourceProvider(current::get);
		};
	}

	private List<String> ids(BattleNext_HullModSourceProvider source) {
		return source.getOptions().stream().map(OptionChoice::getId).toList();
	}

	private TooltipMakerAPI render(BattleNext_HullModSourceProvider source, String id) {
		TooltipMakerAPI tooltip = tooltip(source.getTooltipWidth(), 0f, false);
		source.createTooltip(tooltip, new OptionChoice(id, "Choice"));
		return tooltip;
	}

	private List<LabelAPI> textLabels(TooltipMakerAPI tooltip) {
		return tooltips.get(tooltip).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
	}

	private List<String> textValues(TooltipMakerAPI tooltip) {
		return textLabels(tooltip).stream().map(LabelAPI::getText).toList();
	}

	private ShipVariantAPI context(HullSize size, boolean phase, List<String> installedIds) {

		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullSize" -> size;
			case "getBuiltInMods" -> List.of("built");
			case "isBuiltInMod" -> args[0].equals("built");
			case "isPhase" -> phase;
			case "getHints" -> EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
			case "getDefenseType" -> ShieldAPI.ShieldType.OMNI;
			case "getNoCRLossTime" -> 1000f;
			default -> null;
		});
		return proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
			case "getHullVariantId" -> "live_variant";
			case "getHullSpec" -> hull;
			case "getHullSize" -> size;
			case "getHullMods" -> installedIds;
			case "hasHullMod" -> installedIds.contains(args[0]);
			default -> null;
		});
	}

	private ShipHullSpecAPI decorateHull(ShipHullSpecAPI original) {
		return (ShipHullSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipHullSpecAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getHullSize")) return HullSize.FRIGATE;
			try {
				return method.invoke(original, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		});
	}

	private float gap(PositionAPI previous, PositionAPI next) {
		return previous.getY() - next.getY() - next.getHeight();
	}

	private class Mod {
		private final HullModSpecAPI api;
		private final PreviewEffect script = new PreviewEffect(this);
		private String mainText = "First: %s.\nSecond: %s.";
		private String sModText = "Enhanced: %s.\nComplete extra effect 40%%.";
		private String failedPhase = "";
		private boolean notPhase;

		private Mod(String id, String name, boolean dmod) {

			if (dmod) sModText = "";
			api = proxy(HullModSpecAPI.class, (method, args) -> switch (method) {
				case "getId" -> id;
				case "getDisplayName" -> name;
				case "getSpriteName" -> "graphics/" + id + ".png";
				case "isHidden", "isHiddenEverywhere" -> id.equals("hidden");
				case "getEffect" -> script;
				case "getSModEffectFormat" -> sModText;
				case "getDescriptionFormat" -> {
					if (failedPhase.equals("description")) throw new IllegalStateException("Failed description 50%");
					yield mainText;
				}
				case "getDescription" -> throw new AssertionError("Native tooltip receives the pattern and parameters");
				case "getSModDescription" -> String.format(sModText, script.getSModDescriptionParam(0, (HullSize) args[0]));
				case "hasTag" -> dmod && args[0].equals(Tags.HULLMOD_DMOD) || notPhase && args[0].equals(Tags.HULLMOD_NOT_PHASE);
				default -> null;
			});
			script.init(api);
		}
	}

	private class PreviewEffect extends BaseHullMod {
		private final Mod mod;
		private final List<String> calls = new ArrayList<>();
		private final List<Integer> parameterIndices = new ArrayList<>();
		private final Map<Integer, String> parameterValues = new LinkedHashMap<>();
		private boolean showDescription = true;
		private boolean customPost;
		private boolean customSMod;

		private PreviewEffect(Mod mod) {
			this.mod = mod;
		}

		@Override
		public String getDescriptionParam(int index, HullSize hullSize) {
			if (parameterValues.containsKey(index)) return parameterValues.get(index);
			return switch (index) {
				case 0 -> hullSize + " 20%";
				case 1 -> "Literal %s, %d and 50%";
				default -> null;
			};
		}

		@Override
		public String getDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
			assertNull(ship);
			parameterIndices.add(index);
			return getDescriptionParam(index, hullSize);
		}

		@Override
		public String getSModDescriptionParam(int index, HullSize hullSize) {
			return hullSize + " 30%";
		}

		@Override
		public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
			assertNull(ship);
			assertFalse(isForModSpec);
			calls.add("description:" + hullSize);
			return showDescription;
		}

		@Override
		public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship,
				float width, boolean isForModSpec) {

			assertNull(ship);
			assertFalse(isForModSpec);
			assertEquals(360f, width);
			calls.add("post_description:" + hullSize);
			if (mod.failedPhase.equals("post_description")) {
				tooltip.setParaSmallInsignia();
				tooltip.setParaFontColor(Color.MAGENTA);
				throw new IllegalStateException("Failed post description 50%");
			}
			if (customPost) {
				tooltip.setParaSmallInsignia();
				tooltip.setParaFontColor(Color.MAGENTA);
				tooltip.addPara("Custom post 50%", 8f);
				tooltip.beginTable(Color.GRAY, Color.DARK_GRAY, Color.WHITE, 20f, "Size", width * 0.5f, "Value", width * 0.5f);
				tooltip.addRow(hullSize.name(), "75%");
				tooltip.addTable("", 0, 8f);
			}
		}

		@Override
		public boolean hasSModEffectSection(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
			return customSMod || super.hasSModEffectSection(hullSize, ship, isForModSpec);
		}

		@Override
		public void addSModSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship,
				float width, boolean isForModSpec, boolean isForBuildInList) {

			assertNull(ship);
			assertFalse(isForModSpec);
			assertFalse(isForBuildInList);
			assertEquals(360f, width);
			calls.add("s_mod:" + hullSize);
			if (mod.failedPhase.equals("s_mod")) {
				tooltip.setParaSmallInsignia();
				tooltip.setParaFontColor(Color.MAGENTA);
				throw new IllegalStateException("Failed S-mod 50%");
			}
			if (customSMod) {
				tooltip.addPara("Custom S-mod 60%", 8f);
				return;
			}
			super.addSModSection(tooltip, hullSize, ship, width, isForModSpec, isForBuildInList);
		}
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
