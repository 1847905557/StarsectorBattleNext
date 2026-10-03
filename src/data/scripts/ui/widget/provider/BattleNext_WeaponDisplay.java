package data.scripts.ui.widget.provider;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

public class BattleNext_WeaponDisplay {

	public record Visual(String name, String primarySprite, String secondarySprite, boolean resolved) { }

	private BattleNext_WeaponDisplay() {

	}

	public static Visual resolve(String id, SettingsAPI settings) {
		if (id.isEmpty()) return new Visual(id, "", "", false);
		try {
			WeaponSpecAPI spec = settings.getWeaponSpec(id);
			if (spec != null) {
				String name = spec.getWeaponName();
				String primary = spec.getTurretSpriteName();
				String secondary = spec.getHardpointSpriteName();
				if (name == null || name.isBlank()) {
					BattleNext_Logger.warn(BattleNext_WeaponDisplay.class, "weapon.name.fallback",
				BattleNext_Logger.fields().put("weapon_id", id).put("input", name).put("fallback", id).build());
				}
				return new Visual(name == null || name.isBlank() ? id : name,
						primary == null ? "" : primary, secondary == null ? "" : secondary, true);
			}
		} catch (RuntimeException ignored) {
			BattleNext_Logger.warn(BattleNext_WeaponDisplay.class, "weapon.resolve.degraded",
				BattleNext_Logger.fields().put("weapon_id", id).put("fallback", id).build(), ignored);
			return new Visual(id, "", "", false);
		}
		BattleNext_Logger.warn(BattleNext_WeaponDisplay.class, "weapon.spec.missing",
				BattleNext_Logger.fields().put("weapon_id", id).put("fallback", id).build());
		return new Visual(id, "", "", false);
	}
}
