package data.scripts.ui.widget.provider;

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
				return new Visual(name == null || name.isBlank() ? id : name,
						primary == null ? "" : primary, secondary == null ? "" : secondary, true);
			}
		} catch (RuntimeException ignored) {

		}
		return new Visual(id, "", "", false);
	}
}
