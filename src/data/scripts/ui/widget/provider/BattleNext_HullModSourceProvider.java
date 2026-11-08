package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_HullModSourceProvider extends BattleNext_BaseSourceProvider {

	private final Supplier<ShipVariantAPI> variantSupplier;

	private final BattleNext_I18nSection refitStrings = BattleNext_I18nSection.getInstance("Refit", "BattleNext_refit_");

	public BattleNext_HullModSourceProvider(Supplier<ShipVariantAPI> variantSupplier) {
		this.variantSupplier = variantSupplier;
	}

	protected List<HullModSpecAPI> getHullMods(ShipVariantAPI variant) {
		return Global.getSettings().getAllHullModSpecs();
	}

	@Override
	public List<OptionChoice> getOptions() {
		return getHullMods(variantSupplier.get()).stream()
				.map(spec -> new OptionChoice(spec.getId(), spec.getDisplayName()))
				.sorted(Comparator.comparing(OptionChoice::label).thenComparing(OptionChoice::id))
				.collect(ArrayList::new, List::add, List::addAll);
	}

	@Override
	public int getMaxStack(String id) {
		return 1;
	}

	@Override
	public String getIconPath(String id) {
		try {
			HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
			if (spec == null) throw new IllegalArgumentException("Unknown hullmod: " + id);
			String path = spec.getSpriteName();
			if (path == null || path.isBlank()) throw new IllegalArgumentException("Missing hullmod icon: " + id);
			return path;
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_HullModSourceProvider.class, "hullmod.icon.degraded",
					BattleNext_Logger.fields().put("hullmod_id", id).put("fallback", "Missing icon").build(), exception);
			return "";
		}
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice choice) {

		ShipVariantAPI variant = null;
		String phase = "context";
		try {
			variant = variantSupplier.get();
			phase = "spec";
			HullModSpecAPI spec = Global.getSettings().getHullModSpec(choice.id());
			if (spec == null) throw new IllegalArgumentException("Unknown hullmod: " + choice.id());
			ShipVariantAPI context = variant;
			BattleNext_EquipmentTooltip.renderHullmod(tooltip, spec.getDisplayName(), choice.id(), spec,
					context == null ? null : context.getHullSize(), context == null ? null : context.getHullVariantId(),
					() -> auxiliaryNotes(context, spec));
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_HullModSourceProvider.class, "hullmod.tooltip.degraded",
					BattleNext_Logger.fields().put("hullmod_id", choice.id()).put("title", choice.label())
							.put("variant_id", variant == null ? null : variant.getHullVariantId())
							.put("phase", phase).build(), exception);
			BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(
					choice.label(), choice.id(), "", List.of(exception.toString())));
		}
	}

	private List<String> auxiliaryNotes(ShipVariantAPI variant, HullModSpecAPI spec) {
		if (variant == null) return List.of();
		if (spec.hasTag(Tags.HULLMOD_DMOD)) {
			return List.of(refitStrings.get(isSuitableFor(variant, spec) ? "d_mod_suitable" : "d_mod_unsuitable"));
		}
		if (variant.getHullSpec().isBuiltInMod(spec.getId())
				&& !spec.getEffect().hasSModEffect()
				&& !spec.getEffect().hasSModEffectSection(variant.getHullSize(), null, false)) {
			return List.of(refitStrings.get("s_mod_no_effect"));
		}
		return List.of();
	}

	public static boolean isSuitableFor(ShipVariantAPI variant, HullModSpecAPI spec) {
		List<HullModSpecAPI> suitable = new ArrayList<>();
		suitable.add(spec);
		DModManager.removeUnsuitedMods(variant, suitable);
		return !suitable.isEmpty();
	}
}
