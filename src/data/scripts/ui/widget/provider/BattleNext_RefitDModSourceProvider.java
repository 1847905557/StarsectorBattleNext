package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_RefitDModSourceProvider extends BattleNext_HullModSourceProvider {

	public BattleNext_RefitDModSourceProvider(Supplier<ShipVariantAPI> variantSupplier) {
		super(variantSupplier);
	}

	@Override
	protected List<HullModSpecAPI> getHullMods(ShipVariantAPI variant) {
		List<HullModSpecAPI> result = new ArrayList<>();
		for (HullModSpecAPI spec : super.getHullMods(variant)) {
			if (!spec.hasTag(Tags.HULLMOD_DMOD)) continue;
			result.add(spec);
		}
		return result;
	}
}
