package data.scripts.ui.page;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import data.scripts.report.BattleNext_DamageSourceReport;

public class BattleNext_ReportSourceDisplay {

	public record SourceVisual(String name, String primarySprite, String secondarySprite,
			String placeholder, boolean resolved) { }

	private BattleNext_ReportSourceDisplay() {
	}

	public static SourceVisual resolve(BattleNext_DamageSourceReport source, SettingsAPI settings) {
		String id = source.getId();
		try {
			switch (source.getKind()) {
				case "weapon": {
					WeaponSpecAPI spec = settings.getWeaponSpec(id);
					if (spec != null) {
						return new SourceVisual(nameOrId(spec.getWeaponName(), id),
								path(spec.getTurretSpriteName()), path(spec.getHardpointSpriteName()), "W", true);
					}
					break;
				}
				case "fighter_wing": {
					FighterWingSpecAPI wing = settings.getFighterWingSpec(id);
					if (wing != null) {
						ShipVariantAPI variant = wing.getVariant();
						ShipHullSpecAPI hull = variant == null ? null : variant.getHullSpec();
						return new SourceVisual(nameOrId(wing.getWingName(), id),
								hull == null ? "" : path(hull.getSpriteName()), "", "F", true);
					}
					break;
				}
				case "ship": {
					ShipHullSpecAPI hull = settings.getHullSpec(id);
					if (hull != null) {
						return new SourceVisual(nameOrId(hull.getHullName(), id),
								path(hull.getSpriteName()), "", "S", true);
					}
					break;
				}
				default:
					break;
			}
		} catch (RuntimeException ignored) {
			// A missing current asset keeps the report's original source ID visible.
		}
		return new SourceVisual(id, "", "", placeholder(source.getKind()), false);
	}

	private static String nameOrId(String name, String id) {
		return name == null || name.isBlank() ? id : name;
	}

	private static String path(String value) {
		return value == null ? "" : value;
	}

	private static String placeholder(String kind) {
		return switch (kind) {
			case "weapon" -> "W";
			case "fighter_wing" -> "F";
			case "ship" -> "S";
			case "projectile_spec" -> "P";
			case "source_type" -> "T";
			case "damage_type" -> "D";
			default -> "?";
		};
	}
}
