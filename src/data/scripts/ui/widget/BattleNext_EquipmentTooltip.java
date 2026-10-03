package data.scripts.ui.widget;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_EquipmentTooltip {

	public static final float WIDTH = 360f;
	private static final float KEY_GAP = 5f;
	private static final float BODY_GAP = 8f;
	private static final float BOTTOM_INSET = 8f;
	private static final BattleNext_I18nSection STRINGS = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	public record Content(String title, String id, String description, List<String> notes) { }

	private BattleNext_EquipmentTooltip() {

	}

	public static void render(TooltipMakerAPI tooltip, Content content) {

		tooltip.setTitleSmallOrbitron();
		tooltip.setTitleFontColor(BattleNext_UiTheme.ACCENT);
		tooltip.setParaFontDefault();
		tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
		tooltip.addTitle(content.title());
		if (!content.id().isEmpty()) {
			BattleNext_PageLayout.addMutedIdLine(tooltip, content.id(), KEY_GAP);
		}
		tooltip.setParaFontDefault();
		tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
		String paragraph = firstParagraph(content.description());
		if (!paragraph.isEmpty()) tooltip.addPara(paragraph, BattleNext_UiTheme.TEXT, BODY_GAP);
		for (String note : content.notes()) {
			if (note == null || note.isBlank()) continue;
			tooltip.addPara(note, BattleNext_UiTheme.TEXT, BODY_GAP);
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
				resultNotes.add(STRINGS.get("load_failed") + ": " + exception);
			}
		}
		return new Content(title, id, firstParagraph(text), List.copyOf(resultNotes));
	}

	public static Content hull(String title, String id, ShipHullSpecAPI hull,
			SettingsAPI settings, List<String> notes) {
		return description(title, id, hull.getDescriptionId(), Description.Type.SHIP, settings, notes);
	}

	public static Content hullmod(String title, String id, HullModSpecAPI spec, HullSize size, List<String> notes) {
		String text = "";
		List<String> resultNotes = new ArrayList<>(notes);
		if (size != null) {
			try {
				text = spec.getDescription(size);
			} catch (RuntimeException exception) {
				resultNotes.add(STRINGS.get("load_failed") + ": " + exception);
			}
		}
		return new Content(title, id, firstParagraph(text), List.copyOf(resultNotes));
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
			List<String> resultNotes = new ArrayList<>(notes);
			resultNotes.add(STRINGS.get("load_failed") + ": " + exception);
			return new Content(title, id, "", List.copyOf(resultNotes));
		}
	}
}
