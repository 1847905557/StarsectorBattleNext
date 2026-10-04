package data.scripts.ui.widget;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_EquipmentTooltip {

	public static final float WIDTH = 360f;
	private static final float KEY_GAP = 5f;
	private static final float BODY_GAP = 8f;
	private static final float ROW_GAP = 2f;
	private static final float BOTTOM_INSET = 8f;
	private static final BattleNext_I18nSection STRINGS = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	public record Content(String title, String id, String description, List<String> notes) { }

	private BattleNext_EquipmentTooltip() {

	}

	public static void render(TooltipMakerAPI tooltip, Content content) {
		render(tooltip, content, List.of());
	}

	public static void render(TooltipMakerAPI tooltip, Content content, List<List<String>> sections) {

		renderHeader(tooltip, content.title(), content.id());
		String paragraph = firstParagraph(content.description());
		if (!paragraph.isEmpty()) tooltip.addPara(paragraph, BattleNext_UiTheme.TEXT, BODY_GAP);
		for (String note : content.notes()) {
			if (note == null || note.isBlank()) continue;
			tooltip.addPara(note, BattleNext_UiTheme.TEXT, BODY_GAP);
		}
		for (List<String> section : sections) {
			for (int index = 0; index < section.size(); index++) {
				tooltip.addPara(section.get(index), BattleNext_UiTheme.TEXT, index == 0 ? BODY_GAP : ROW_GAP);
			}
		}
		tooltip.addSpacer(BOTTOM_INSET);
	}

	public static String firstParagraph(String text) {
		if (text == null || text.isBlank()) return "";
		return text.strip().split("\\R", 2)[0].replaceAll("\\s+", " ").trim();
	}

	public static Content description(String title, String id, String descriptionId, Description.Type type,
			SettingsAPI settings, List<String> notes) {
		List<String> resultNotes = new ArrayList<>(notes);
		String text = "";
		if (descriptionId != null && !descriptionId.isBlank()) {
			try {
				Description description = settings.getDescription(descriptionId, type);
				if (description != null && description.hasText1()) text = description.getText1();
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_EquipmentTooltip.class, "equipment.description.degraded",
				BattleNext_Logger.fields().put("title", title).put("id", id).put("description_id", descriptionId).put("description_type", type).build(), exception);
				resultNotes.add(STRINGS.get("load_failed") + ": " + exception);
			}
		}
		return new Content(title, id, firstParagraph(text), List.copyOf(resultNotes));
	}

	public static Content hull(String title, String id, ShipHullSpecAPI hull,
			SettingsAPI settings, List<String> notes) {
		return description(title, id, hull.getDescriptionId(), Description.Type.SHIP, settings, notes);
	}

	public static void renderHullmod(TooltipMakerAPI tooltip, String title, String id, HullModSpecAPI spec,
			HullSize size, String variantId, Supplier<List<String>> notes) {

		renderHeader(tooltip, title, id);
		if (size != null) {
			for (String phase : List.of("description", "post_description", "s_mod", "notes")) {
				try {
					switch (phase) {
						case "description" -> {
							HullModEffect effect = spec.getEffect();
							if (effect.shouldAddDescriptionToTooltip(size, null, false)) {
								String pattern = spec.getDescriptionFormat();
								if (pattern != null && !pattern.isBlank()) {
									String[] parameters = new String[10];
									for (int index = 0; index < parameters.length; index++) {
										parameters[index] = effect.getDescriptionParam(index, size, null);
									}
									tooltip.addPara(pattern, BODY_GAP, Misc.getHighlightColor(), parameters);
								}
							}
						}
						case "post_description" -> spec.getEffect().addPostDescriptionSection(
								tooltip, size, null, tooltip.getWidthSoFar(), false);
						case "s_mod" -> {
							HullModEffect effect = spec.getEffect();
							if (effect.hasSModEffect() || effect.hasSModEffectSection(size, null, false)) {
								effect.addSModSection(tooltip, size, null, tooltip.getWidthSoFar(), false, false);
							}
						}
						case "notes" -> {
							for (String note : notes.get()) {
								addFullText(tooltip, note);
							}
						}
					}
				} catch (RuntimeException exception) {
					BattleNext_Logger.warn(BattleNext_EquipmentTooltip.class, "hullmod.description.degraded",
							BattleNext_Logger.fields().put("title", title).put("hullmod_id", id)
									.put("hull_size", size).put("variant_id", variantId).put("phase", phase)
									.put("ship", null).put("is_for_mod_spec", false).put("width", tooltip.getWidthSoFar())
									.build(), exception);
					tooltip.setParaFontDefault();
					tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
					tooltip.addPara(STRINGS.get("load_failed") + ": " + exception, BattleNext_UiTheme.TEXT, BODY_GAP);
				} finally {
					tooltip.setParaFontDefault();
					tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
				}
			}
		}
		tooltip.addSpacer(BOTTOM_INSET);
	}

	private static void renderHeader(TooltipMakerAPI tooltip, String title, String id) {
		tooltip.setTitleSmallOrbitron();
		tooltip.setTitleFontColor(BattleNext_UiTheme.ACCENT);
		tooltip.addTitle(title);
		if (!id.isEmpty()) {
			BattleNext_PageLayout.addMutedIdLine(tooltip, id, KEY_GAP);
		}
		tooltip.setParaFontDefault();
		tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
	}

	private static void addFullText(TooltipMakerAPI tooltip, String text) {
		if (text == null || text.isBlank()) return;
		for (String paragraph : text.strip().split("\\R")) {
			if (paragraph.isBlank()) continue;
			tooltip.addPara(paragraph, BattleNext_UiTheme.TEXT, BODY_GAP);
		}
	}

	public static Content wing(String title, String id, SettingsAPI settings, List<String> notes) {
		try {
			var wing = settings.getFighterWingSpec(id);
			if (wing == null) throw new IllegalArgumentException("Unknown fighter wing: " + id);
			var variant = wing.getVariant();
			if (variant == null) throw new IllegalArgumentException("Missing fighter variant: " + id);
			var hull = variant.getHullSpec();
			if (hull == null) throw new IllegalArgumentException("Missing fighter hull: " + id);
			return hull(title, id, hull, settings, notes);
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_EquipmentTooltip.class, "wing.description.degraded",
				BattleNext_Logger.fields().put("title", title).put("wing_id", id).build(), exception);
			List<String> resultNotes = new ArrayList<>(notes);
			resultNotes.add(STRINGS.get("load_failed") + ": " + exception);
			return new Content(title, id, "", List.copyOf(resultNotes));
		}
	}
}
