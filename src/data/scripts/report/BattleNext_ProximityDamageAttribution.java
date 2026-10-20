package data.scripts.report;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ProximityExplosionEffect;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.loading.ShotBehaviorSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;

import java.util.Map;
import java.util.WeakHashMap;

public class BattleNext_ProximityDamageAttribution {

	private static final String SOURCE_KEY = "BattleNext_proximityWeaponSource";
	private static final Map<ShotBehaviorSpecAPI, String> ORIGINAL_EFFECTS = new WeakHashMap<>();

	private BattleNext_ProximityDamageAttribution() {
	}

	@LifecycleEntry
	public static void register() {
		for (WeaponSpecAPI weapon : Global.getSettings().getAllWeaponSpecs()) {
			Object spec = weapon.getProjectileSpec();
			ShotBehaviorSpecAPI behavior = null;
			if (spec instanceof ProjectileSpecAPI projectile) {
				behavior = projectile.getBehaviorSpec();
			} else if (spec instanceof MissileSpecAPI missile) {
				behavior = missile.getBehaviorSpec();
			}
			if (behavior == null || !"PROXIMITY_FUSE".equals(behavior.getBehavorString())) continue;
			if (ExplosionEffect.class.getName().equals(behavior.getOnExplosionClassName())) continue;
			ORIGINAL_EFFECTS.put(behavior, behavior.getOnExplosionClassName());
			behavior.setOnExplosionClassName(ExplosionEffect.class.getName());
		}
	}

	static BattleNext_DamageAttribution.SourceKey sourceKey(DamagingProjectileAPI projectile) {
		return (BattleNext_DamageAttribution.SourceKey) projectile.getCustomData().get(SOURCE_KEY);
	}

	public static class ExplosionEffect implements ProximityExplosionEffect {
		@Override
		public void onExplosion(DamagingProjectileAPI explosion, DamagingProjectileAPI originalProjectile) {
			WeaponAPI weapon = originalProjectile.getWeapon();
			if (weapon != null) {
				explosion.setCustomData(SOURCE_KEY, BattleNext_DamageAttribution.weaponKey(weapon));
			}
			ShotBehaviorSpecAPI behavior = originalProjectile instanceof MissileAPI missile
					? missile.getSpec().getBehaviorSpec() : originalProjectile.getProjectileSpec().getBehaviorSpec();
			String original = ORIGINAL_EFFECTS.get(behavior);
			if (original != null) {
				ProximityExplosionEffect effect = (ProximityExplosionEffect) Global.getSettings().getInstanceOfScript(original);
				effect.onExplosion(explosion, originalProjectile);
			}
		}
	}
}
