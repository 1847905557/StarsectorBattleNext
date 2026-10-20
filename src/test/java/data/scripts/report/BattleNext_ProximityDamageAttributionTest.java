package data.scripts.report;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.ShotBehaviorSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.BattleNextModPlugin;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_ProximityDamageAttributionTest extends BattleNext_TestGameFixture {

	private final List<WeaponSpecAPI> weapons = new ArrayList<>();
	private final List<ProximityExplosionEffect> effects = new ArrayList<>();
	private final List<String> loadedEffects = new ArrayList<>();
	private final List<DamagingProjectileAPI> forwarded = new ArrayList<>();
	private Map<ShotBehaviorSpecAPI, String> registrations;
	private Map<ShotBehaviorSpecAPI, String> previousRegistrations;
	private RuntimeException effectFailure;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void bindWeaponsAndPreserveRegistrations() throws ReflectiveOperationException {

		Field field = BattleNext_ProximityDamageAttribution.class.getDeclaredField("ORIGINAL_EFFECTS");
		field.setAccessible(true);
		registrations = (Map<ShotBehaviorSpecAPI, String>) field.get(null);
		previousRegistrations = new HashMap<>(registrations);
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("getAllWeaponSpecs")) return weapons;
			if (method.getName().equals("getInstanceOfScript")) {
				loadedEffects.add((String) args[0]);
				ProximityExplosionEffect effect = new ProximityExplosionEffect() {
					@Override
					public void onExplosion(DamagingProjectileAPI explosion, DamagingProjectileAPI original) {
						assertEquals(original.getWeapon() == null ? null : BattleNext_DamageAttribution.weaponKey(original.getWeapon()),
								BattleNext_ProximityDamageAttribution.sourceKey(explosion));
						forwarded.add(explosion);
						forwarded.add(original);
						if (effectFailure != null) throw effectFailure;
					}
				};
				effects.add(effect);
				return effect;
			}
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	@AfterEach
	void restoreRegistrations() {
		registrations.clear();
		registrations.putAll(previousRegistrations);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void sharedBehaviorUsesEachProjectileWeaponAndForwardsFreshEffects(boolean missile) {

		Behavior shared = new Behavior("PROXIMITY_FUSE", "effect.shared");
		Object spec = spec(shared.api, missile);
		weapons.add(weaponSpec("first", spec));
		weapons.add(weaponSpec("second", spec));
		weapons.add(weaponSpec("clone", spec(shared.api, missile)));
		Behavior custom = new Behavior("CUSTOM", "effect.custom");
		weapons.add(weaponSpec("custom", spec(custom.api, missile)));
		weapons.add(weaponSpec("beam", null));
		BattleNext_ProximityDamageAttribution.register();
		BattleNext_ProximityDamageAttribution.register();

		DamagingProjectileAPI first = projectile(weapon("first", "instance_first"), spec, missile);
		DamagingProjectileAPI second = projectile(weapon("second", "instance_second"), spec, missile);
		DamagingProjectileAPI firstExplosion = projectile(null, null, false);
		DamagingProjectileAPI secondExplosion = projectile(null, null, false);
		new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(firstExplosion, first);
		new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(secondExplosion, second);

		assertEquals(1, shared.writes);
		assertEquals("effect.custom", custom.effect);
		assertEquals(List.of("effect.shared", "effect.shared"), loadedEffects);
		assertNotSame(effects.get(0), effects.get(1));
		assertEquals(List.of(firstExplosion, first, secondExplosion, second), forwarded);
		assertEquals(new BattleNext_DamageAttribution.SourceKey("weapon", "first"), key(firstExplosion));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("weapon", "second"), key(secondExplosion));
		assertEquals(key(firstExplosion), BattleNext_DamageAttribution.sourceKey(firstExplosion, null, damage()));
		assertEquals(key(firstExplosion), BattleNext_DamageAttribution.sourceKey(weapon("other", "other"), firstExplosion, damage()));
	}

	@Test
	void reloadRegistersNewSpecsWhileInFlightProjectilesKeepOriginalEffects() {

		Behavior old = new Behavior("PROXIMITY_FUSE", "effect.old");
		Object oldSpec = spec(old.api, false);
		weapons.add(weaponSpec("old", oldSpec));
		new BattleNextModPlugin().onDevModeF8Reload();
		DamagingProjectileAPI inFlight = projectile(weapon("old", "old"), oldSpec, false);
		Behavior replacement = new Behavior("PROXIMITY_FUSE", "effect.reloaded");
		Object newSpec = spec(replacement.api, true);
		weapons.clear();
		weapons.add(weaponSpec("new", newSpec));
		new BattleNextModPlugin().onDevModeF8Reload();
		new BattleNextModPlugin().onDevModeF8Reload();
		DamagingProjectileAPI oldExplosion = projectile(null, null, false);
		DamagingProjectileAPI newExplosion = projectile(null, null, false);
		new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(oldExplosion, inFlight);
		new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(newExplosion,
				projectile(weapon("new", "new"), newSpec, true));

		assertEquals(List.of("effect.old", "effect.reloaded"), loadedEffects);
		assertEquals("old", key(oldExplosion).id());
		assertEquals("new", key(newExplosion).id());
		assertEquals(1, old.writes);
		assertEquals(1, replacement.writes);
	}

	@Test
	void weaponIdResolutionAndMissingWeaponFollowExistingAttributionRules() {
		Behavior behavior = new Behavior("PROXIMITY_FUSE", null);
		Object spec = spec(behavior.api, false);
		weapons.add(weaponSpec("gun", spec));
		BattleNext_ProximityDamageAttribution.register();
		for (String id : List.of("instance_id", "")) {
			DamagingProjectileAPI explosion = projectile(null, null, false);
			new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(explosion,
					projectile(weapon("", id), spec, false));
			assertEquals(new BattleNext_DamageAttribution.SourceKey("weapon", id.isEmpty() ? "unknown" : id), key(explosion));
		}
		DamagingProjectileAPI explosion = projectile(null, null, false);
		new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(explosion, projectile(null, spec, false));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "HIGH_EXPLOSIVE"), key(explosion));
	}

	@Test
	void originalEffectFailurePropagatesAfterSourceIsRecorded() {
		Behavior behavior = new Behavior("PROXIMITY_FUSE", "effect.failure");
		Object spec = spec(behavior.api, false);
		weapons.add(weaponSpec("gun", spec));
		BattleNext_ProximityDamageAttribution.register();
		effectFailure = new IllegalStateException("Original proximity effect failed");
		DamagingProjectileAPI explosion = projectile(null, null, false);
		assertSame(effectFailure, assertThrows(IllegalStateException.class,
				() -> new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(explosion,
						projectile(weapon("gun", "gun"), spec, false))));
		assertEquals(new BattleNext_DamageAttribution.SourceKey("weapon", "gun"), key(explosion));
		assertEquals(1, effects.size());
		assertEquals(2, forwarded.size());
	}

	@Test
	void projectileWithoutWeaponStillRunsItsOriginalExplosionEffect() {
		Behavior behavior = new Behavior("PROXIMITY_FUSE", "effect.unarmed");
		Object spec = spec(behavior.api, false);
		weapons.add(weaponSpec("gun", spec));
		BattleNext_ProximityDamageAttribution.register();
		DamagingProjectileAPI original = projectile(null, spec, false);
		DamagingProjectileAPI explosion = projectile(null, null, false);
		new BattleNext_ProximityDamageAttribution.ExplosionEffect().onExplosion(explosion, original);
		assertEquals(List.of("effect.unarmed"), loadedEffects);
		assertEquals(List.of(explosion, original), forwarded);
		assertEquals(new BattleNext_DamageAttribution.SourceKey("damage_type", "HIGH_EXPLOSIVE"), key(explosion));
	}

	private Object spec(ShotBehaviorSpecAPI behavior, boolean missile) {
		return missile ? proxy(MissileSpecAPI.class, (name, args) -> name.equals("getBehaviorSpec") ? behavior : null)
				: proxy(ProjectileSpecAPI.class, (name, args) -> name.equals("getBehaviorSpec") ? behavior : null);
	}

	private WeaponSpecAPI weaponSpec(String id, Object projectile) {
		return proxy(WeaponSpecAPI.class, (name, args) -> switch (name) {
			case "getProjectileSpec" -> projectile;
			case "getWeaponId" -> id;
			default -> null;
		});
	}

	private WeaponAPI weapon(String id, String instanceId) {
		return proxy(WeaponAPI.class, (name, args) -> switch (name) {
			case "getSpec" -> weaponSpec(id, null);
			case "getId" -> instanceId;
			default -> null;
		});
	}

	private DamagingProjectileAPI projectile(WeaponAPI weapon, Object spec, boolean missile) {
		Map<String, Object> data = new HashMap<>();
		Handler handler = (name, args) -> switch (name) {
			case "getWeapon" -> weapon;
			case "getSpec", "getProjectileSpec" -> spec;
			case "getCustomData" -> data;
			case "setCustomData" -> data.put((String) args[0], args[1]);
			default -> null;
		};
		return missile ? proxy(MissileAPI.class, handler) : proxy(DamagingProjectileAPI.class, handler);
	}

	private BattleNext_DamageAttribution.SourceKey key(DamagingProjectileAPI explosion) {
		return BattleNext_DamageAttribution.sourceKey(null, explosion, damage());
	}

	private ApplyDamageResultAPI damage() {
		return proxy(ApplyDamageResultAPI.class, (name, args) -> name.equals("getType") ? DamageType.HIGH_EXPLOSIVE : null);
	}

	private class Behavior {
		private final ShotBehaviorSpecAPI api;
		private String effect;
		private int writes;

		private Behavior(String type, String effect) {
			this.effect = effect;
			api = proxy(ShotBehaviorSpecAPI.class, (name, args) -> switch (name) {
				case "getBehavorString" -> type;
				case "getOnExplosionClassName" -> this.effect;
				case "setOnExplosionClassName" -> {
					this.effect = (String) args[0];
					writes++;
					yield null;
				}
				default -> null;
			});
		}
	}
}
