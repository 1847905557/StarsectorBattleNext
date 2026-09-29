package data.scripts.report;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

public class BattleNext_DamageAttribution {

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

	public static SourceKey sourceKey(Object source, ApplyDamageResultAPI result) {
		ShipAPI ship = sourceShip(source);
		if (ship != null && ship.isFighter() && ship.getWing() != null) {
			return new SourceKey("fighter_wing", idOrUnknown(ship.getWing().getWingId()));
		}
		WeaponAPI weapon = null;
		if (source instanceof DamagingProjectileAPI projectile) weapon = projectile.getWeapon();
		if (source instanceof BeamAPI beam) weapon = beam.getWeapon();
		if (source instanceof WeaponAPI directWeapon) weapon = directWeapon;
		if (weapon != null) {
			String id = weapon.getSpec() == null ? "" : weapon.getSpec().getWeaponId();
			if (id == null || id.isEmpty()) id = weapon.getId();
			return new SourceKey("weapon", idOrUnknown(id));
		}
		if (ship != null) return new SourceKey("ship", shipId(memberShip(ship)));
		if (source instanceof DamagingProjectileAPI projectile
				&& projectile.getProjectileSpecId() != null && !projectile.getProjectileSpecId().isEmpty()) {
			return new SourceKey("projectile_spec", projectile.getProjectileSpecId());
		}
		if (source != null) return new SourceKey("source_type", source.getClass().getName());
		return new SourceKey("damage_type", result.getType() == null ? "unknown" : result.getType().name());
	}

	private static String shipId(ShipAPI ship) {
		return ship == null || ship.getHullSpec() == null ? "unknown"
				: idOrUnknown(ship.getHullSpec().getHullId());
	}

	private static String idOrUnknown(String id) {
		return id == null || id.isEmpty() ? "unknown" : id;
	}
}
