package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import data.scripts.tools.BattleNext_Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class BattleNext_RefitSModSourceProvider extends BattleNext_HullModSourceProvider {

	public BattleNext_RefitSModSourceProvider(Supplier<ShipVariantAPI> variantSupplier) {
		super(variantSupplier);
	}

	@Override
	protected List<HullModSpecAPI> getHullMods(ShipVariantAPI variant) {
		if (variant == null) return List.of();
		Set<String> ids = new LinkedHashSet<>(variant.getHullMods());
		ids.addAll(variant.getHullSpec().getBuiltInMods());
		List<HullModSpecAPI> result = new ArrayList<>();
		for (String id : ids) {
			try {
				HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
				if (spec == null) throw new IllegalArgumentException("Unknown hullmod: " + id);
				if (spec.hasTag(Tags.HULLMOD_DMOD)) continue;
				result.add(spec);
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_RefitSModSourceProvider.class, "hullmod.source.unavailable",
						BattleNext_Logger.fields().put("hullmod_id", id).put("variant_id", variant.getHullVariantId())
								.put("fallback", "Omitted option").build(), exception);
			}
		}
		return result;
	}
}
