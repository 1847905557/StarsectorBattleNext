package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BattleNext_VariantSourceProvider implements BattleNext_OptionPicker.OptionChoiceSource {

	private static final int VARIANT_SELECTION_MAX_COUNT = 1;
	private static final float VARIANT_ID_TOOLTIP_GAP = 5f;

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
		tooltip.addTitle(option.getLabel());
		tooltip.addPara(option.getId(), VARIANT_ID_TOOLTIP_GAP);
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
