package data.scripts.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.ui.widget.provider.BattleNext_RefitDModSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_RefitSModSourceProvider;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.test.fixture.BattleNext_TestPageFixture;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_RefitPanelPluginTest extends BattleNext_TestPageFixture {

	@Test
	void modSelectorsUseLocalizedTitlesExactGeometryAndFullPanelOverlayHost() throws Exception {
		deferTooltipWidth = true;
		BattleNext_RefitPanelPlugin plugin = new BattleNext_RefitPanelPlugin(() -> {});
		CustomPanelAPI root = panel(1000f, 700f, new BaseCustomUIPanelPlugin());
		CustomPanelAPI right = panel(180f, 560f, new BaseCustomUIPanelPlugin());
		root.addComponent(right).inTL(700f, 63f);
		for (String name : List.of("panel", "rightPanel")) {
			Field field = BattleNext_RefitPanelPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(plugin, "panel".equals(name) ? root : right);
		}
		Method build = BattleNext_RefitPanelPlugin.class.getDeclaredMethod("buildPickers", float.class);
		build.setAccessible(true);
		build.invoke(plugin, 180f);
		BattleNext_OptionPicker sMods = (BattleNext_OptionPicker) getField(plugin, "sModPicker");
		BattleNext_OptionPicker dMods = (BattleNext_OptionPicker) getField(plugin, "dModPicker");
		for (BattleNext_OptionPicker picker : List.of(sMods, dMods)) {
			assertSame(root, getField(picker, "overlayHost"));
			PositionAPI row = ((CustomPanelAPI) getField(picker, "row")).getPosition();
			assertEquals(180f, row.getWidth());
			assertEquals(30f, row.getHeight());
			assertEquals(right.getPosition().getX(), row.getX());
			float top = right.getPosition().getY() + right.getPosition().getHeight() - row.getY() - row.getHeight();
			assertEquals(picker == sMods ? 0f : 36f, top);
			assertEquals(picker == sMods ? "BattleNext_refit_s_mods" : "BattleNext_refit_d_mods", getField(picker, "title"));
			picker.close();
		}
	}

	@Test
	void shipSpriteKeepsNativeSizeWhenItFitsAndShrinksToFitOtherwise() {
		assertEquals(1f, BattleNext_PageLayout.spriteScaleToFit(128f, 256f, 500f, 700f));
		assertEquals(0.5f, BattleNext_PageLayout.spriteScaleToFit(800f, 200f, 400f, 700f));
		assertEquals(0.5f, BattleNext_PageLayout.spriteScaleToFit(200f, 800f, 400f, 400f));
	}

	@Test
	void ordinaryAndBuiltInHullmodsCanBePromotedAndDemoted() {
		HullState hull = new HullState("wolf", false, false, false, Set.of("built"), null, null);
		VariantState variant = new VariantState(hull, Set.of("normal", "built"));

		BattleNext_RefitPanelPlugin.applySMods(variant.api, Set.of("normal", "built"));
		assertEquals(Set.of("normal"), variant.sMods);
		assertEquals(Set.of("normal"), variant.permaMods);
		assertEquals(Set.of("built"), variant.builtInSMods);

		BattleNext_RefitPanelPlugin.applySMods(variant.api, Set.of());
		assertTrue(variant.sMods.isEmpty());
		assertTrue(variant.permaMods.isEmpty());
		assertTrue(variant.builtInSMods.isEmpty());
		assertTrue(variant.hullMods.contains("normal"));
		assertTrue(variant.hullMods.contains("built"));
	}

	@Test
	void ordinaryHullSwitchesToDefaultDHullAndRestoresAfterLastDMod() {
		HullState ordinary = new HullState("wolf", false, false, false, Set.of(), null, null);
		HullState damaged = new HullState("wolf_default_D", true, true, false,
				Set.of(), ordinary, ordinary);
		withSettings(damaged, () -> {
			VariantState variant = new VariantState(ordinary, Set.of());

			BattleNext_RefitPanelPlugin.applyDMods(variant.api, Set.of("d"));
			assertSame(damaged.api, variant.hull);
			assertEquals(Set.of("d"), variant.permaMods);

			BattleNext_RefitPanelPlugin.applyDMods(variant.api, Set.of());
			assertSame(ordinary.api, variant.hull);
			assertTrue(variant.permaMods.isEmpty());
		});
	}

	@Test
	void nativeDHullKeepsItsSkinWhenBuiltInDModIsRemoved() {
		HullState ordinary = new HullState("wolf", false, false, false, Set.of(), null, null);
		HullState nativeD = new HullState("wolf_d", true, false, true, Set.of("d"), null, ordinary);
		withSettings(null, () -> {
			VariantState variant = new VariantState(nativeD, Set.of("d"));

			BattleNext_RefitPanelPlugin.applyDMods(variant.api, Set.of());
			assertSame(nativeD.api, variant.hull);
			assertFalse(variant.hullMods.contains("d"));
			assertEquals(Set.of("d"), variant.suppressedMods);
		});
	}

	@Test
	void nativeDHullWithoutActiveDModsKeepsItsSkin() {
		HullState nativeD = new HullState("mercury_d", true, false, false,
				Set.of("d"), null, null);
		withSettings(null, () -> {
			VariantState variant = new VariantState(nativeD, Set.of());
			variant.suppressedMods.add("d");

			BattleNext_RefitPanelPlugin.applyDMods(variant.api, Set.of());
			assertSame(nativeD.api, variant.hull);
			assertEquals(Set.of("d"), variant.suppressedMods);
		});
	}

	@Test
	void defaultDHullOfNativeSkinReturnsToItsNativeParent() {
		HullState ordinary = new HullState("wolf", false, false, false, Set.of(), null, null);
		HullState nativeD = new HullState("wolf_d", true, false, true,
				Set.of("d"), null, ordinary);
		HullState generated = new HullState("wolf_d_default_D", true, true, true,
				Set.of("d"), nativeD, ordinary);
		withSettings(null, () -> {
			VariantState variant = new VariantState(generated, Set.of("d"));

			BattleNext_RefitPanelPlugin.applyDMods(variant.api, Set.of());
			assertSame(nativeD.api, variant.hull);
			assertEquals(Set.of("d"), variant.suppressedMods);
		});
	}

	@Test
	void ordinaryHullWithoutDModsRemainsOrdinary() {
		HullState ordinary = new HullState("wolf", false, false, false, Set.of(), null, null);
		withSettings(null, () -> {
			VariantState variant = new VariantState(ordinary, Set.of());

			BattleNext_RefitPanelPlugin.applyDMods(variant.api, Set.of());
			assertSame(ordinary.api, variant.hull);
		});
	}

	@Test
	void dModSuitabilityUsesTheVanillaPhaseHullRule() {
		HullState phase = new HullState("shade", false, false, false,
				Set.of(), null, null, true);
		HullState ordinary = new HullState("wolf", false, false, false,
				Set.of(), null, null);
		HullModSpecAPI noPhaseDMod = (HullModSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{HullModSpecAPI.class}, (instance, method, args) ->
						"hasTag".equals(method.getName()) && "notPhase".equals(args[0]));
		withSettings(null, () -> {
			assertFalse(BattleNext_RefitDModSourceProvider.isSuitableFor(
					new VariantState(phase, Set.of()).api, noPhaseDMod));
			assertTrue(BattleNext_RefitDModSourceProvider.isSuitableFor(
					new VariantState(ordinary, Set.of()).api, noPhaseDMod));
		});
	}

	@Test
	void pickersListInstalledSModsAndAllRegisteredDMods() {
		HullState hull = new HullState("wolf", false, false, false,
				Set.of("built"), null, null);
		withSettings(null, () -> {
			VariantState variant = new VariantState(hull, Set.of("normal", "built", "d"));
			List<String> sIds = new BattleNext_RefitSModSourceProvider(() -> variant.api).getOptions().stream()
					.map(choice -> choice.getId()).toList();
			List<String> dIds = new BattleNext_RefitDModSourceProvider(() -> variant.api).getOptions().stream()
					.map(choice -> choice.getId()).toList();

			assertEquals(List.of("built", "normal"), sIds);
			assertEquals(List.of("d"), dIds);
		});
	}

	@Test
	void escapeClosesAnUnchangedRefitPanel() {
		AtomicBoolean closed = new AtomicBoolean();
		AtomicBoolean consumed = new AtomicBoolean();
		BattleNext_RefitPanelPlugin plugin = new BattleNext_RefitPanelPlugin(() -> closed.set(true));
		InputEventAPI escape = (InputEventAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{InputEventAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isConsumed" -> consumed.get();
					case "isKeyboardEvent", "isKeyDownEvent" -> true;
					case "getEventValue" -> Keyboard.KEY_ESCAPE;
					case "consume" -> { consumed.set(true); yield null; }
					default -> null;
				});

		assertTrue(plugin.handleGlobalInput(List.of(escape)));
		assertTrue(consumed.get());
		assertTrue(closed.get());
	}

	private void withSettings(HullState damaged, Runnable action) {
		SettingsAPI previous = Global.getSettings();
		HullModSpecAPI dMod = spec("d", true);
		HullModSpecAPI built = spec("built", false);
		HullModSpecAPI normal = spec("normal", false);
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getHullModSpec" -> switch ((String) args[0]) {
						case "d" -> dMod;
						case "built" -> built;
						case "normal" -> normal;
						default -> null;
					};
					case "getHullSpec" -> damaged == null ? null : damaged.api;
					case "getAllHullModSpecs" -> List.of(normal, dMod, built);
					case "getInt" -> 5;
					case "getFloat" -> 1f;
					case "getBoolean" -> false;
					case "getColor" -> Color.WHITE;
					default -> null;
				});
		Global.setSettings(settings);
		try {
			action.run();
		} finally {
			Global.setSettings(previous);
		}
	}

	private HullModSpecAPI spec(String id, boolean dmod) {
		return (HullModSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{HullModSpecAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "hasTag" -> dmod && "dmod".equals(args[0]);
					case "getId" -> id;
					case "getDisplayName" -> id;
					default -> null;
				});
	}

	private static class HullState {
		private final ShipHullSpecAPI api;
		private final Set<String> builtIns;

		private HullState(String id, boolean dHull, boolean defaultD, boolean restoreToBase,
				Set<String> builtIns, HullState dParent, HullState base) {
			this(id, dHull, defaultD, restoreToBase, builtIns, dParent, base, false);
		}

		private HullState(String id, boolean dHull, boolean defaultD, boolean restoreToBase,
				Set<String> builtIns, HullState dParent, HullState base, boolean phase) {
			this.builtIns = builtIns;
			api = (ShipHullSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{ShipHullSpecAPI.class}, (instance, method, args) -> switch (method.getName()) {
						case "getHullId" -> id;
						case "isDHull" -> dHull;
						case "isDefaultDHull" -> defaultD;
						case "isRestoreToBase" -> restoreToBase;
						case "getDParentHull" -> dParent == null ? null : dParent.api;
						case "getBaseHull" -> base == null ? null : base.api;
						case "isBuiltInMod" -> builtIns.contains(args[0]);
						case "getBuiltInMods" -> builtIns.stream().toList();
						case "isPhase" -> phase;
						case "getNoCRLossTime" -> 1000f;
						case "getHints" -> EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
						case "getDefenseType" -> ShieldAPI.ShieldType.OMNI;
						case "hasTag" -> false;
						default -> null;
					});
		}
	}

	private static class VariantState {
		private final ShipVariantAPI api;
		private final Set<String> hullMods = new LinkedHashSet<>();
		private final Set<String> permaMods = new LinkedHashSet<>();
		private final LinkedHashSet<String> sMods = new LinkedHashSet<>();
		private final LinkedHashSet<String> builtInSMods = new LinkedHashSet<>();
		private final Set<String> suppressedMods = new LinkedHashSet<>();
		private ShipHullSpecAPI hull;

		private VariantState(HullState hull, Set<String> initialMods) {
			this.hull = hull.api;
			hullMods.addAll(initialMods);
			api = (ShipVariantAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{ShipVariantAPI.class}, (instance, method, args) -> switch (method.getName()) {
						case "getHullSpec" -> this.hull;
						case "setHullSpecAPI" -> { this.hull = (ShipHullSpecAPI) args[0]; yield null; }
						case "getHullMods" -> hullMods;
						case "getPermaMods" -> permaMods;
						case "getSMods" -> sMods;
						case "getSModdedBuiltIns" -> builtInSMods;
						case "getSuppressedMods" -> suppressedMods;
						case "hasDMods" -> hullMods.contains("d");
						case "hasHullMod" -> hullMods.contains(args[0]);
						case "hasTag" -> false;
						case "addMod" -> { hullMods.add((String) args[0]); yield null; }
						case "removeMod" -> { hullMods.remove(args[0]); yield null; }
						case "addPermaMod" -> {
							String id = (String) args[0];
							permaMods.add(id);
							hullMods.add(id);
							if (args.length > 1 && (Boolean) args[1]) sMods.add(id);
							yield null;
						}
						case "removePermaMod" -> {
							permaMods.remove(args[0]);
							sMods.remove(args[0]);
							hullMods.remove(args[0]);
							yield null;
						}
						case "addSuppressedMod" -> {
							suppressedMods.add((String) args[0]);
							hullMods.remove(args[0]);
							yield null;
						}
						case "removeSuppressedMod" -> {
							suppressedMods.remove(args[0]);
							if (this.hull.isBuiltInMod((String) args[0]) || permaMods.contains(args[0])) hullMods.add((String) args[0]);
							yield null;
						}
						case "setSource" -> null;
						default -> null;
					});
		}
	}
}
