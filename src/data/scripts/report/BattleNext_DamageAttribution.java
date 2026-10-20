package data.scripts.report;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;

public class BattleNext_DamageAttribution {
	public static final String ARC_SOURCE_ID = "arc";

	public record SourceKey(String kind, String id) { }

	private BattleNext_DamageAttribution() {
	}

	public static ShipAPI sourceShip(Object source) {
		if (source instanceof ShipAPI ship) return ship;
		if (source instanceof DamagingProjectileAPI projectile) {
			if (projectile.getSource() != null) return projectile.getSource();
			return projectile.getWeapon() == null ? null : projectile.getWeapon().getShip();
		}
		if (source instanceof BeamAPI beam) {
			if (beam.getSource() != null) return beam.getSource();
			return beam.getWeapon() == null ? null : beam.getWeapon().getShip();
		}
		if (source instanceof WeaponAPI weapon) return weapon.getShip();
		return null;
	}

	public static ShipAPI sourceShip(Object source, Object damageParam) {
		ShipAPI ship = sourceShip(source);
		return ship == null ? sourceShip(damageParam) : ship;
	}

	public static ShipAPI memberShip(ShipAPI ship) {
		if (ship == null) return null;
		if (ship.isFighter()) {
			FighterWingAPI wing = ship.getWing();
			if (wing == null) return null;
			ship = wing.getSourceShip();
		}
		while (ship != null && ship.isStationModule()) {
			ship = ship.getParentStation();
		}
		return ship;
	}

	public static SourceKey sourceKey(Object source, Object damageParam, ApplyDamageResultAPI result) {
		ShipAPI ship = sourceShip(source, damageParam);
		if (ship != null && ship.isFighter() && ship.getWing() != null) {
			return new SourceKey("fighter_wing", idOrUnknown(ship.getWing().getWingId()));
		}
		if (damageParam instanceof EmpArcEntityAPI
				|| DamageDealtModifier.EMP_SHIP_SYSTEM_PARAM.equals(damageParam)) {
			return new SourceKey("arc", ARC_SOURCE_ID);
		}
		SourceKey weapon = weaponSource(damageParam);
		if (weapon == null) weapon = weaponSource(source);
		if (weapon != null) return weapon;
		if (damageParam instanceof DamagingProjectileAPI || source instanceof DamagingProjectileAPI
				|| damageParam instanceof BeamAPI || source instanceof BeamAPI) {
			return new SourceKey("damage_type", result.getType() == null ? "unknown" : result.getType().name());
		}
		if (ship != null) return new SourceKey("ship", shipId(memberShip(ship)));
		if (source != null) return new SourceKey("source_type", source.getClass().getName());
		if (damageParam != null) return new SourceKey("source_type", damageParam.getClass().getName());
		return new SourceKey("damage_type", result.getType() == null ? "unknown" : result.getType().name());
	}

	static SourceKey weaponKey(WeaponAPI weapon) {
		String id = weapon.getSpec() == null ? "" : weapon.getSpec().getWeaponId();
		if (id == null || id.isEmpty()) id = weapon.getId();
		return new SourceKey("weapon", idOrUnknown(id));
	}

	private static SourceKey weaponSource(Object source) {
		if (source instanceof DamagingProjectileAPI projectile) {
			SourceKey key = BattleNext_ProximityDamageAttribution.sourceKey(projectile);
			if (key != null) return key;
			return projectile.getWeapon() == null ? null : weaponKey(projectile.getWeapon());
		}
		if (source instanceof BeamAPI beam) return beam.getWeapon() == null ? null : weaponKey(beam.getWeapon());
		if (source instanceof WeaponAPI weapon) return weaponKey(weapon);
		return null;
	}

	private static String shipId(ShipAPI ship) {
		return ship == null || ship.getHullSpec() == null ? "unknown"
				: idOrUnknown(ship.getHullSpec().getHullId());
	}

	private static String idOrUnknown(String id) {
		return id == null || id.isEmpty() ? "unknown" : id;
	}
}
