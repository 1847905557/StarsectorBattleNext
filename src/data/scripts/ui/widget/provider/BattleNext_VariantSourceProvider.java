package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.Description.Type;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_VariantSourceProvider extends BattleNext_BaseSourceProvider {

	private static final int VARIANT_SELECTION_MAX_COUNT = 1;
	private static final float FACT_GAP = 5f;
	private static final float FACT_RESERVE = 26f;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");

	@Override
	public int getMaxCount(String id) {
		return VARIANT_SELECTION_MAX_COUNT;
	}

	@Override
	public List<OptionChoice> getOptions() {
		List<OptionChoice> options = new ArrayList<>();
		for (String id : Global.getSettings().getAllVariantIds()) {
			ShipVariantAPI variant = loadVariant(id);
			if (variant == null || variant.isFighter()) continue;
			options.add(new OptionChoice(id, label(variant)));
		}
		options.sort(Comparator.comparing(OptionChoice::getId));
		return options;
	}

	@Override
	public String getIconPath(String id) {
		ShipVariantAPI variant = loadVariant(id);
		if (variant == null || variant.getHullSpec() == null) return "";
		String sprite = variant.getHullSpec().getSpriteName();
		return sprite == null ? "" : sprite;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		addHeader(tooltip, option.getLabel(), option.getId());
		ShipVariantAPI variant = loadVariant(option.getId());
		if (variant == null || variant.getHullSpec() == null) return;
		ShipHullSpecAPI hull = variant.getHullSpec();
		String descriptionId = hull.getDescriptionId();
		Description description = descriptionId == null || descriptionId.isBlank() ? null
				: Global.getSettings().getDescription(descriptionId, Type.SHIP);
		if (description != null) addExcerpt(tooltip, description.getText1FirstPara(), FACT_RESERVE);
		String size = hull.getHullSize().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
		String facts = strings.get("refit_hull_size") + ": " + size + " - "
				+ strings.get("refit_base_dp") + ": " + hull.getSuppliesToRecover();
		if (tooltip.getHeightSoFar() + FACT_RESERVE <= tooltip.getPosition().getHeight()) {
			tooltip.addPara(facts, FACT_GAP);
		}
	}

	private ShipVariantAPI loadVariant(String id) {
		if (id == null || id.isEmpty()) return null;
		try {
			return Global.getSettings().getVariant(id);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private String label(ShipVariantAPI variant) {
		ShipHullSpecAPI hull = variant.getHullSpec();
		String hullName = hull.getHullName();
		String variantName = variant.getDisplayName();
		return hullName + " - " + variantName;
	}
}
