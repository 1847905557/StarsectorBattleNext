package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.Description.Type;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
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
	private static final float FACT_GAP = 5f;
	private static final float FACT_RESERVE = 26f;
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
		addHeader(tooltip, option.getLabel(), option.getId());
		ShipVariantAPI variant = loadVariant(option.getId());
		if (variant == null || variant.getHullSpec() == null) return;
		ShipHullSpecAPI hull = variant.getHullSpec();
		String descriptionId = hull.getDescriptionId();
		Description description = descriptionId == null || descriptionId.isBlank() ? null
				: Global.getSettings().getDescription(descriptionId, Type.SHIP);
		if (description != null) addExcerpt(tooltip, description.getText1FirstPara(), FACT_RESERVE);
		String size = hull.getHullSize().name().replace('_', ' ').toLowerCase(Locale.ROOT);
		String facts = variantStrings.get("hull_size") + ": " + size + " - "
				+ variantStrings.get("base_deployment_points") + ": " + hull.getSuppliesToRecover();
		if (tooltip.getHeightSoFar() + FACT_RESERVE <= tooltip.getPosition().getHeight()) {
			tooltip.addPara(facts, FACT_GAP);
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
