package data.scripts.ui.page;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.report.BattleNext_DamageSourceReport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				"projectile_spec", "source_type", "damage_type"}) {
			BattleNext_ReportSourceDisplay.SourceVisual visual =
					BattleNext_ReportSourceDisplay.resolve(source(kind, "source-id"), missing);
			assertEquals("source-id", visual.name());
			assertEquals("", visual.primarySprite());
			assertFalse(visual.resolved());
		}
		assertEquals("W", BattleNext_ReportSourceDisplay.resolve(source("weapon", "x"), missing).placeholder());
		assertEquals("F", BattleNext_ReportSourceDisplay.resolve(source("fighter_wing", "x"), missing).placeholder());
		assertEquals("S", BattleNext_ReportSourceDisplay.resolve(source("ship", "x"), missing).placeholder());
		assertEquals("P", BattleNext_ReportSourceDisplay.resolve(source("projectile_spec", "x"), missing).placeholder());
		assertEquals("T", BattleNext_ReportSourceDisplay.resolve(source("source_type", "x"), missing).placeholder());
		assertEquals("D", BattleNext_ReportSourceDisplay.resolve(source("damage_type", "x"), missing).placeholder());
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
