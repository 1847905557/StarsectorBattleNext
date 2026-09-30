package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class BattleNext_RefitSModSourceProvider extends BattleNext_BaseSourceProvider {

	private final Supplier<ShipVariantAPI> variantSupplier;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");

	public BattleNext_RefitSModSourceProvider(Supplier<ShipVariantAPI> variantSupplier) {
		this.variantSupplier = variantSupplier;
	}

	@Override
	public List<OptionChoice> getOptions() {
		ShipVariantAPI variant = variantSupplier.get();
		if (variant == null) return List.of();
		Set<String> ids = new LinkedHashSet<>(variant.getHullMods());
		ids.addAll(variant.getHullSpec().getBuiltInMods());
		List<OptionChoice> result = new ArrayList<>();
		for (String id : ids) {
			HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
			if (spec == null || spec.hasTag(Tags.HULLMOD_DMOD)) continue;
			result.add(new OptionChoice(id, spec.getDisplayName()));
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
		addHeader(tooltip, spec.getDisplayName(), choice.getId());
		if (variant == null) return;
		boolean effect = spec.getEffect() != null && spec.getEffect().hasSModEffect();
		String sModText = effect && spec.getSModEffectFormat() != null
				&& !spec.getSModEffectFormat().isBlank() ? spec.getSModDescription(variant.getHullSize()) : "";
		if (spec.getEffect() != null) {
			addExcerpt(tooltip, spec.getDescription(variant.getHullSize()), sModText.isBlank() ? 0f : 40f);
		}
		if (!sModText.isBlank()) addExcerpt(tooltip, sModText);
		else if (variant.getHullSpec().isBuiltInMod(choice.getId())) {
			addExcerpt(tooltip, strings.get("refit_smod_no_effect"));
		}
	}
}
