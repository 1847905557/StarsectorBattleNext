package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_RefitDModSourceProvider implements BattleNext_OptionPicker.OptionChoiceSource {

	private final Supplier<ShipVariantAPI> variantSupplier;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");

	public BattleNext_RefitDModSourceProvider(Supplier<ShipVariantAPI> variantSupplier) {
		this.variantSupplier = variantSupplier;
	}

	@Override
	public List<OptionChoice> getOptions() {
		List<OptionChoice> result = new ArrayList<>();
		for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
			if (!spec.hasTag(Tags.HULLMOD_DMOD)) continue;
			result.add(new OptionChoice(spec.getId(), spec.getDisplayName()));
		}
		result.sort(Comparator.comparing(OptionChoice::getLabel).thenComparing(OptionChoice::getId));
		return result;
	}

	@Override
	public int getMaxCount(String id) {
		return 1;
	}

	@Override
	public String getIconPath(String id) {
		HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
		return spec == null ? "" : spec.getSpriteName();
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice choice) {
		ShipVariantAPI variant = variantSupplier.get();
		HullModSpecAPI spec = Global.getSettings().getHullModSpec(choice.getId());
		tooltip.addTitle(spec.getDisplayName());
		tooltip.addPara(choice.getId(), 4f);
		if (variant != null) {
			tooltip.addPara(strings.get(isSuitableFor(variant, spec)
					? "refit_suitable" : "refit_unsuitable"), 6f);
		}
	}

	public static boolean isSuitableFor(ShipVariantAPI variant, HullModSpecAPI spec) {
		List<HullModSpecAPI> suitable = new ArrayList<>();
		suitable.add(spec);
		DModManager.removeUnsuitedMods(variant, suitable);
		return !suitable.isEmpty();
	}
}
