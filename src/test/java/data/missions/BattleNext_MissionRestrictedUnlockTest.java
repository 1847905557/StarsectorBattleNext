package data.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_MissionRestrictedUnlockTest {

	@AfterEach
	void restore() {
		BattleNext_MissionRestrictedUnlock.getInstance().forget();
	}

	@Test
	void unlocksAllThreeEquipmentKindsAndRestoresOnlyTheRemovedTag() {
		Set<String> weaponTags = new HashSet<>(Set.of(Tags.RESTRICTED, "weapon_other"));
		Set<String> wingTags = new HashSet<>(Set.of(Tags.RESTRICTED, "wing_other"));
		Set<String> hullmodTags = new HashSet<>(Set.of(Tags.RESTRICTED, "hullmod_other"));
		Set<String> unrestrictedTags = new HashSet<>(Set.of("original"));
		SettingsAPI settings = settings(List.of(spec(WeaponSpecAPI.class, weaponTags),
				spec(WeaponSpecAPI.class, unrestrictedTags)),
				List.of(spec(FighterWingSpecAPI.class, wingTags)),
				List.of(spec(HullModSpecAPI.class, hullmodTags)));
		BattleNext_MissionRestrictedUnlock unlock = BattleNext_MissionRestrictedUnlock.getInstance();

		unlock.enable(settings);
		assertTrue(unlock.isEnabled());
		for (Set<String> tags : List.of(weaponTags, wingTags, hullmodTags)) {
			assertFalse(tags.contains(Tags.RESTRICTED));
		}
		weaponTags.add("added_during_refit");
		unrestrictedTags.add("added_elsewhere");
		unlock.enable(settings);
		unlock.restore();

		assertEquals(Set.of(Tags.RESTRICTED, "weapon_other", "added_during_refit"), weaponTags);
		assertEquals(Set.of(Tags.RESTRICTED, "wing_other"), wingTags);
		assertEquals(Set.of(Tags.RESTRICTED, "hullmod_other"), hullmodTags);
		assertEquals(Set.of("original", "added_elsewhere"), unrestrictedTags);
		assertFalse(unlock.isEnabled());
	}

	@Test
	void failedEnumerationRestoresAlreadyChangedSpecs() {
		Set<String> weaponTags = new HashSet<>(Set.of(Tags.RESTRICTED));
		WeaponSpecAPI weapon = spec(WeaponSpecAPI.class, weaponTags);
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getAllWeaponSpecs" -> List.of(weapon);
					case "getAllFighterWingSpecs" -> throw new IllegalStateException("unavailable");
					default -> null;
				});
		BattleNext_MissionRestrictedUnlock unlock = BattleNext_MissionRestrictedUnlock.getInstance();

		assertThrows(IllegalStateException.class, () -> unlock.enable(settings));
		assertTrue(weaponTags.contains(Tags.RESTRICTED));
		assertFalse(unlock.isEnabled());
	}

	@Test
	void previewIdentityAllowsRefitReopenAndReleasesOnSelectionChange() {
		Set<String> weaponTags = new HashSet<>(Set.of(Tags.RESTRICTED));
		SettingsAPI previous = Global.getSettings();
		Global.setSettings(settings(List.of(spec(WeaponSpecAPI.class, weaponTags)), List.of(), List.of()));
		MissionDefinitionAPI selected = spec(MissionDefinitionAPI.class, Set.of());
		MissionDefinitionAPI other = spec(MissionDefinitionAPI.class, Set.of());
		BattleNext_MissionRestrictedUnlock unlock = BattleNext_MissionRestrictedUnlock.getInstance();
		try {
			unlock.enable(selected);
			assertTrue(unlock.owns(selected));
			assertFalse(unlock.owns(other));
			unlock.observe(selected, false, false);
			assertTrue(weaponTags.contains(Tags.RESTRICTED));
			unlock.observe(selected, true, false);
			assertFalse(weaponTags.contains(Tags.RESTRICTED));
			unlock.observe(selected, false, true);
			assertFalse(weaponTags.contains(Tags.RESTRICTED));
			unlock.observe(other, true, false);
			assertFalse(unlock.hasPreview());
			assertTrue(weaponTags.contains(Tags.RESTRICTED));
		} finally {
			unlock.forget();
			Global.setSettings(previous);
		}
	}

	private SettingsAPI settings(List<WeaponSpecAPI> weapons, List<FighterWingSpecAPI> wings,
			List<HullModSpecAPI> hullmods) {
		return (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getAllWeaponSpecs" -> weapons;
					case "getAllFighterWingSpecs" -> wings;
					case "getAllHullModSpecs" -> hullmods;
					default -> null;
				});
	}

	@SuppressWarnings("unchecked")
	private <T> T spec(Class<T> type, Set<String> tags) {
		return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> "getTags".equals(method.getName()) ? tags : null);
	}
}
