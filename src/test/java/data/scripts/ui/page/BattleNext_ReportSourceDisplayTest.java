package data.scripts.ui.page;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.report.BattleNext_DamageSourceReport;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class BattleNext_ReportSourceDisplayTest {

	@Test
	void weaponWingAndShipUseRegisteredNamesAndImages() {
		WeaponSpecAPI weapon = proxy(WeaponSpecAPI.class, name -> switch (name) {
			case "getWeaponName" -> "Light Machine Gun";
			case "getTurretSpriteName" -> "graphics/weapons/turret.png";
			case "getHardpointSpriteName" -> "graphics/weapons/hardpoint.png";
			default -> null;
		});
		ShipHullSpecAPI fighterHull = hull("Talon", "graphics/ships/talon.png");
		ShipVariantAPI fighterVariant = proxy(ShipVariantAPI.class, name ->
				"getHullSpec".equals(name) ? fighterHull : null);
		FighterWingSpecAPI wing = proxy(FighterWingSpecAPI.class, name -> switch (name) {
			case "getWingName" -> "Talon Wing";
			case "getVariant" -> fighterVariant;
			default -> null;
		});
		ShipHullSpecAPI ship = hull("Wolf", "graphics/ships/wolf.png");
		SettingsAPI settings = proxy(SettingsAPI.class, name -> switch (name) {
			case "getWeaponSpec" -> weapon;
			case "getFighterWingSpec" -> wing;
			case "getHullSpec" -> ship;
			default -> null;
		});

		BattleNext_ReportSourceDisplay.SourceVisual weaponVisual =
				BattleNext_ReportSourceDisplay.resolve(source("weapon", "lightmg"), settings);
		assertEquals("Light Machine Gun", weaponVisual.name());
		assertEquals("graphics/weapons/turret.png", weaponVisual.primarySprite());
		assertEquals("graphics/weapons/hardpoint.png", weaponVisual.secondarySprite());
		assertTrue(weaponVisual.resolved());
		BattleNext_ReportSourceDisplay.SourceVisual wingVisual =
				BattleNext_ReportSourceDisplay.resolve(source("fighter_wing", "talon_wing"), settings);
		assertEquals("Talon Wing", wingVisual.name());
		assertEquals("graphics/ships/talon.png", wingVisual.primarySprite());
		BattleNext_ReportSourceDisplay.SourceVisual shipVisual =
				BattleNext_ReportSourceDisplay.resolve(source("ship", "wolf_hull"), settings);
		assertEquals("Wolf", shipVisual.name());
		assertEquals("graphics/ships/wolf.png", shipVisual.primarySprite());
	}

	@Test
	void missingAssetsAndOtherKindsRetainSourceIdsAndCategoryPlaceholders() {
		SettingsAPI missing = proxy(SettingsAPI.class, name -> null);
		for (String kind : new String[]{"weapon", "fighter_wing", "ship",
				"source_type", "damage_type"}) {
			BattleNext_ReportSourceDisplay.SourceVisual visual =
					BattleNext_ReportSourceDisplay.resolve(source(kind, "source-id"), missing);
			assertEquals("source-id", visual.name());
			assertEquals("", visual.primarySprite());
			assertFalse(visual.resolved());
		}
		assertEquals("W", BattleNext_ReportSourceDisplay.resolve(source("weapon", "x"), missing).placeholder());
		assertEquals("F", BattleNext_ReportSourceDisplay.resolve(source("fighter_wing", "x"), missing).placeholder());
		assertEquals("S", BattleNext_ReportSourceDisplay.resolve(source("ship", "x"), missing).placeholder());
		assertEquals("T", BattleNext_ReportSourceDisplay.resolve(source("source_type", "x"), missing).placeholder());
		assertEquals("D", BattleNext_ReportSourceDisplay.resolve(source("damage_type", "x"), missing).placeholder());
	}

	@Test
	void arcUsesRegisteredEnergyLashIconAndName() throws Exception {
		SettingsAPI settings = proxy(SettingsAPI.class, name -> switch (name) {
			case "getString" -> "Arc";
			case "getSpriteName" -> "graphics/icons/hullsys/energy_lash.png";
			default -> null;
		});
		BattleNext_ReportSourceDisplay.SourceVisual visual =
				BattleNext_ReportSourceDisplay.resolve(source("arc", "arc"), settings);

		assertEquals("Arc", visual.name());
		assertEquals("graphics/icons/hullsys/energy_lash.png", visual.primarySprite());
		assertTrue(visual.accentIcon());
		assertTrue(visual.resolved());
		assertEquals("A", visual.placeholder());
		JSONObject config = new JSONObject(Files.readString(Path.of("data/config/settings.json"), StandardCharsets.UTF_8));
		assertEquals(visual.primarySprite(), config.getJSONObject("graphics")
				.getJSONObject("report").getString("BattleNext_arc"));
		assertTrue(Files.exists(Path.of("..", "..", "starsector-core", visual.primarySprite())));
	}

	@Test
	void sourceTypeUsesRegisteredXCircleWithoutTint() throws Exception {
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
					if (!"getSpriteName".equals(method.getName())) return null;
					assertEquals("report", args[0]);
					assertEquals("BattleNext_source_type", args[1]);
					return "graphics/ui/icons/64x_xcircle.png";
				});
		BattleNext_ReportSourceDisplay.SourceVisual visual = BattleNext_ReportSourceDisplay.resolve(
				source("source_type", "example.DamageSource"), settings);

		assertEquals("example.DamageSource", visual.name());
		assertEquals("graphics/ui/icons/64x_xcircle.png", visual.primarySprite());
		assertFalse(visual.accentIcon());
		assertFalse(visual.resolved());
		JSONObject config = new JSONObject(Files.readString(Path.of("data/config/settings.json"), StandardCharsets.UTF_8));
		assertEquals(visual.primarySprite(), config.getJSONObject("graphics")
				.getJSONObject("report").getString("BattleNext_source_type"));
		assertTrue(Files.exists(Path.of("..", "..", "starsector-core", visual.primarySprite())));
	}

	@Test
	void damageTypesUseFiveUntintedVanillaIcons() {
		Map<DamageType, String> suffixes = Map.of(
				DamageType.ENERGY, "energy",
				DamageType.FRAGMENTATION, "fragmentation",
				DamageType.HIGH_EXPLOSIVE, "high_explosive",
				DamageType.KINETIC, "kinetic",
				DamageType.OTHER, "other");
		for (Map.Entry<DamageType, String> entry : suffixes.entrySet()) {
			String expected = "graphics/ui/icons/damagetype_" + entry.getValue() + ".png";
			SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
						if (!"getSpriteName".equals(method.getName())) return null;
						assertEquals("ui", args[0]);
						assertEquals("icon_" + entry.getValue(), args[1]);
						return expected;
					});
			BattleNext_ReportSourceDisplay.SourceVisual visual = BattleNext_ReportSourceDisplay.resolve(
					source("damage_type", entry.getKey().name()), settings);
			assertEquals(expected, visual.primarySprite());
			assertEquals(entry.getKey().name(), visual.name());
			assertFalse(visual.accentIcon());
			assertFalse(visual.resolved());
			assertTrue(Files.exists(Path.of("..", "..", "starsector-core", visual.primarySprite())));
		}
	}

	private BattleNext_DamageSourceReport source(String kind, String id) {
		return new BattleNext_DamageSourceReport(kind, id);
	}

	private ShipHullSpecAPI hull(String name, String sprite) {
		return proxy(ShipHullSpecAPI.class, method -> switch (method) {
			case "getHullName" -> name;
			case "getSpriteName" -> sprite;
			default -> null;
		});
	}

	private interface Call {
		Object invoke(String name);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> call.invoke(method.getName())));
	}
}
