package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BattleNext_VariantSourceProvider extends BattleNext_BaseSourceProvider {
	public static final String PINNED_PREFIX = "pinned:";
	private final BattleNext_PinnedVariantStore pinnedVariants;

	public BattleNext_VariantSourceProvider() {
		this(new BattleNext_PinnedVariantStore());
	}

	public BattleNext_VariantSourceProvider(BattleNext_PinnedVariantStore pinnedVariants) {
		this.pinnedVariants = pinnedVariants;
	}

	private static final int VARIANT_SELECTION_MAX_COUNT = 1;
	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");

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
		try {
			pinnedVariants.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			Global.getLogger(BattleNext_VariantSourceProvider.class).warn("Unable to load pinned variants", exception);
		}
		if (pinnedVariants.isLoaded()) {
			for (BattleNext_PinnedVariantModel pin : pinnedVariants.getAll()) {
				ShipVariantAPI variant = pinnedVariants.resolve(pin.getPinId());
				if (variant.isFighter()) continue;
				options.add(new OptionChoice(PINNED_PREFIX + pin.getPinId(), pin.getDisplayName()));
			}
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

		try {
			ShipVariantAPI variant = resolve(option.getId());
			if (variant == null || variant.getHullSpec() == null) {
				throw new IllegalArgumentException("Unknown variant: " + option.getId());
			}
			ShipHullSpecAPI hull = variant.getHullSpec();
			String size = hull.getHullSize().name().replace('_', ' ').toLowerCase(Locale.ROOT);
			String facts = variantStrings.get("hull_size") + ": " + size + " - "
					+ variantStrings.get("base_deployment_points") + ": " + hull.getSuppliesToRecover();
			BattleNext_EquipmentTooltip.render(tooltip, BattleNext_EquipmentTooltip.hull(
					option.getLabel(), option.getId(), hull, Global.getSettings(), List.of(facts)));
		} catch (RuntimeException exception) {
			BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(
					option.getLabel(), option.getId(), "", List.of(exception.toString())));
		}
	}
	public ShipVariantAPI resolve(String id) {
		if (!id.startsWith(PINNED_PREFIX)) return Global.getSettings().getVariant(id);
		if (!pinnedVariants.isLoaded()) {
			try {
				pinnedVariants.reload();
			} catch (IOException | JSONException exception) {
				throw new IllegalStateException("Unable to load pinned variants", exception);
			}
		}
		return pinnedVariants.resolve(id.substring(PINNED_PREFIX.length()));
	}

	private ShipVariantAPI loadVariant(String id) {
		if (id == null || id.isEmpty()) return null;
		try {
			return resolve(id);
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
