package data.scripts.ui.page;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.provider.BattleNext_WeaponDisplay;

import java.util.Locale;

public class BattleNext_ReportSourceDisplay {

	public record SourceVisual(String name, String primarySprite, String secondarySprite,
			String placeholder, boolean resolved, boolean accentIcon) { }

	private BattleNext_ReportSourceDisplay() {
	}

	public static SourceVisual resolve(BattleNext_DamageSourceReport source, SettingsAPI settings) {
		String id = source.getId();
		try {
			switch (source.getKind()) {
				case "weapon": {
					BattleNext_WeaponDisplay.Visual visual = BattleNext_WeaponDisplay.resolve(id, settings);
					return new SourceVisual(visual.name(), visual.primarySprite(), visual.secondarySprite(),
							"W", visual.resolved(), false);
				}
				case "fighter_wing": {
					FighterWingSpecAPI wing = settings.getFighterWingSpec(id);
					if (wing != null) {
						ShipVariantAPI variant = wing.getVariant();
						ShipHullSpecAPI hull = variant == null ? null : variant.getHullSpec();
						return new SourceVisual(nameOrId(wing.getWingName(), id),
								hull == null ? "" : path(hull.getSpriteName()), "", "F", true, false);
					}
					break;
				}
				case "ship": {
					ShipHullSpecAPI hull = settings.getHullSpec(id);
					if (hull != null) {
						return new SourceVisual(nameOrId(hull.getHullName(), id),
								path(hull.getSpriteName()), "", "S", true, false);
					}
					break;
				}
				case "arc": {
					return new SourceVisual(settings.getString("Report", "BattleNext_report_source_arc"),
							path(settings.getSpriteName("report", "BattleNext_arc")), "", "A", true, true);
				}
				case "source_type": {
					return new SourceVisual(id, path(settings.getSpriteName("report", "BattleNext_source_type")),
							"", "T", false, false);
				}
				case "damage_type": {
					String iconKey = "icon_" + DamageType.valueOf(id).name().toLowerCase(Locale.ROOT);
					return new SourceVisual(id, path(settings.getSpriteName("ui", iconKey)), "", "D", false, false);
				}
				default:
					break;
			}
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_ReportSourceDisplay.class, "report.source.degraded",
				BattleNext_Logger.fields().put("kind", source.getKind()).put("id", id).build(), exception);
		}
		BattleNext_Logger.warn(BattleNext_ReportSourceDisplay.class, "report.source.unavailable",
				BattleNext_Logger.fields().put("kind", source.getKind()).put("id", id).put("placeholder", placeholder(source.getKind())).build());
		return new SourceVisual(id, "", "", placeholder(source.getKind()), false, false);
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
			case "arc" -> "A";
			case "source_type" -> "T";
			case "damage_type" -> "D";
			default -> "?";
		};
	}
}
