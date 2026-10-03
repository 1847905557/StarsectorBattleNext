package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.ui.page.BattleNext_ReportSourceDisplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_WeaponDisplayTest {

	@ParameterizedTest
	@ValueSource(strings = {"Ion Pulser", "", "   ", "null"})
	void sharedVisualMatchesTheReportWeaponSource(String name) {
		String displayName = name.equals("null") ? null : name;
		WeaponSpecAPI spec = (WeaponSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{WeaponSpecAPI.class}, (instance, method, arguments) -> switch (method.getName()) {
					case "getWeaponName" -> displayName;
					case "getTurretSpriteName" -> "graphics/turret.png";
					case "getHardpointSpriteName" -> "graphics/hardpoint.png";
					default -> null;
				});
		SettingsAPI settings = settings(spec, false);
		var visual = BattleNext_WeaponDisplay.resolve("ion", settings);
		var report = BattleNext_ReportSourceDisplay.resolve(new BattleNext_DamageSourceReport("weapon", "ion"), settings);
		assertEquals(displayName == null || displayName.isBlank() ? "ion" : displayName, visual.name());
		assertTrue(visual.resolved());
		assertEquals("graphics/turret.png", visual.primarySprite());
		assertEquals("graphics/hardpoint.png", visual.secondarySprite());
		assertEquals(visual.name(), report.name());
		assertEquals(visual.primarySprite(), report.primarySprite());
		assertEquals(visual.secondarySprite(), report.secondarySprite());
		assertEquals(visual.resolved(), report.resolved());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void unavailableSpecsRetainTheirOriginalIdentity(boolean exception) {
		SettingsAPI settings = settings(null, exception);
		var visual = BattleNext_WeaponDisplay.resolve("missing", settings);
		var report = BattleNext_ReportSourceDisplay.resolve(new BattleNext_DamageSourceReport("weapon", "missing"), settings);
		assertEquals("missing", visual.name());
		assertFalse(visual.resolved());
		assertEquals("", visual.primarySprite());
		assertEquals("", visual.secondarySprite());
		assertEquals(visual.name(), report.name());
		assertFalse(report.resolved());
	}

	@Test
	void nullImagePathsAreNormalizedAndEmptySlotsHaveAnUnresolvedVisual() {
		WeaponSpecAPI spec = (WeaponSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{WeaponSpecAPI.class}, (instance, method, arguments) ->
						method.getName().equals("getWeaponName") ? "Name" : null);
		var visual = BattleNext_WeaponDisplay.resolve("id", settings(spec, false));
		assertEquals("", visual.primarySprite());
		assertEquals("", visual.secondarySprite());
		assertTrue(visual.resolved());
		var empty = BattleNext_WeaponDisplay.resolve("", settings(null, true));
		assertEquals("", empty.name());
		assertFalse(empty.resolved());
	}

	private SettingsAPI settings(WeaponSpecAPI spec, boolean exception) {
		return (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getWeaponSpec")) {
				if (exception) throw new IllegalArgumentException("Missing spec");
				return spec;
			}
			return null;
		});
	}
}
