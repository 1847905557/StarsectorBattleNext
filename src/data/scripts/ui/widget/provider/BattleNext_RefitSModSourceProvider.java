package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class BattleNext_RefitSModSourceProvider extends BattleNext_BaseSourceProvider {

	private final Supplier<ShipVariantAPI> variantSupplier;
	private final BattleNext_I18nSection refitStrings = BattleNext_I18nSection.getInstance("Refit", "BattleNext_refit_");

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
	public float getTooltipWidth() {
		return BattleNext_EquipmentTooltip.WIDTH;
	}

	@Override
	public boolean isTooltipScrollable() {
		return true;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice choice) {

		ShipVariantAPI variant = variantSupplier.get();
		HullModSpecAPI spec = Global.getSettings().getHullModSpec(choice.getId());
		List<String> notes = new ArrayList<>();
		if (variant != null) {
			boolean effect = spec.getEffect() != null && spec.getEffect().hasSModEffect();
			String text = effect && spec.getSModEffectFormat() != null && !spec.getSModEffectFormat().isBlank()
					? BattleNext_EquipmentTooltip.firstParagraph(spec.getSModDescription(variant.getHullSize())) : "";
			if (!text.isBlank()) notes.add(text);
			else if (variant.getHullSpec().isBuiltInMod(choice.getId())) {
				notes.add(refitStrings.get("s_mod_no_effect"));
			}
		}
		BattleNext_EquipmentTooltip.render(tooltip, BattleNext_EquipmentTooltip.hullmod(spec.getDisplayName(),
				choice.getId(), spec, variant == null ? null : variant.getHullSize(), notes));
	}}
