package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip.VariantSection;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.json.JSONException;

import java.io.IOException;
import java.util.*;

public class BattleNext_VariantSourceProvider extends BattleNext_BaseSourceProvider {

	public static final String PINNED_PREFIX = "pinned:";
	private final BattleNext_PinnedVariantStore pinnedVariants;

	public BattleNext_VariantSourceProvider() {
		this(new BattleNext_PinnedVariantStore());
	}

	public BattleNext_VariantSourceProvider(BattleNext_PinnedVariantStore pinnedVariants) {
		this.pinnedVariants = pinnedVariants;
	}

	private final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private final BattleNext_I18nSection commonStrings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	@Override
	public int getMaxStack(String id) {
		return 1;
	}

	@Override
	public List<OptionChoice> getOptions() {
		List<OptionChoice> options = new ArrayList<>();
		List<OptionChoice> standardOptions = new ArrayList<>();
		List<OptionChoice> pinnedOptions = new ArrayList<>();
		for (String id : Global.getSettings().getAllVariantIds()) {
			ShipVariantAPI variant = loadVariant(id);
			if (variant == null || variant.isFighter()) continue;
			standardOptions.add(new OptionChoice(id, label(variant)));
		}
		standardOptions.sort(Comparator.comparing(OptionChoice::getId));
		try {
			pinnedVariants.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.options.pins.unavailable",
				BattleNext_Logger.fields().put("load_error", pinnedVariants.getLoadError()).build(), exception);
		}
		if (pinnedVariants.isLoaded()) {
			for (BattleNext_PinnedVariantModel pin : pinnedVariants.getAll()) {
				ShipVariantAPI variant = pinnedVariants.resolve(pin.getPinId());
				if (variant.isFighter()) continue;
				pinnedOptions.add(new OptionChoice(PINNED_PREFIX + pin.getPinId(), pin.getDisplayName()));
			}
		}
		pinnedOptions.sort(Comparator.comparing(OptionChoice::getId));
		options.addAll(pinnedOptions);
		options.addAll(standardOptions);
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
			List<String> properties = List.of(
					variantStrings.get("flux_capacitors") + ": " + variant.getNumFluxCapacitors(),
					variantStrings.get("flux_vents") + ": " + variant.getNumFluxVents());
			List<VariantSection> sections = List.of(weaponSection(variant, option.getId()),
					wingSection(variant, option.getId()), moduleSection(variant, option.getId()),
					hullmodSection(variant, option.getId()));
			BattleNext_EquipmentTooltip.renderVariant(tooltip, new BattleNext_EquipmentTooltip.Content(
					option.getLabel(), option.getId(), facts, properties), sections);
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.tooltip.degraded",
				BattleNext_Logger.fields().put("variant_id", option.getId()).put("name", option.getLabel()).build(), exception);
			BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(
					option.getLabel(), option.getId(), "", List.of(exception.toString())));
		}
	}

	private VariantSection weaponSection(ShipVariantAPI variant, String selectionId) {

		Map<String, Integer> counts = new TreeMap<>();
		Map<String, String> names = new TreeMap<>();
		for (String slot : variant.getFittedWeaponSlots()) {
			counts.merge(variant.getWeaponId(slot), 1, Integer::sum);
		}
		for (String id : counts.keySet()) {
			BattleNext_WeaponDisplay.Visual display = BattleNext_WeaponDisplay.resolve(id, Global.getSettings());
			String name = display.name();
			if (!display.resolved()) {
				BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.tooltip.weapon.unavailable",
						BattleNext_Logger.fields().put("variant_id", selectionId).put("weapon_id", id)
								.put("count", counts.get(id)).put("fallback", id).build());
				name = commonStrings.get("unavailable") + ": " + id;
			}
			names.put(id, name);
		}
		return countedSection(variantStrings.get("weapons"), counts, names);
	}

	private VariantSection wingSection(ShipVariantAPI variant, String selectionId) {

		Map<String, Integer> counts = new TreeMap<>();
		Map<String, String> names = new TreeMap<>();
		for (String id : variant.getFittedWings()) {
			counts.merge(id, 1, Integer::sum);
		}
		for (String id : counts.keySet()) {
			try {
				var spec = Global.getSettings().getFighterWingSpec(id);
				if (spec == null) throw new IllegalArgumentException("Unknown fighter wing: " + id);
				String name = spec.getWingName();
				if (name == null || name.isBlank()) throw new IllegalArgumentException("Missing fighter wing name: " + id);
				names.put(id, name);
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.tooltip.wing.unavailable",
						BattleNext_Logger.fields().put("variant_id", selectionId).put("wing_id", id)
								.put("count", counts.get(id)).put("fallback", id).build(), exception);
				names.put(id, commonStrings.get("unavailable") + ": " + id);
			}
		}
		return countedSection(variantStrings.get("fighter_wings"), counts, names);
	}

	private VariantSection moduleSection(ShipVariantAPI variant, String selectionId) {

		Map<String, Integer> counts = new TreeMap<>();
		Map<String, String> names = new TreeMap<>();
		for (var entry : new TreeMap<>(variant.getStationModules()).entrySet()) {
			String id = entry.getValue();
			String name;
			try {
				ShipVariantAPI module = variant.getModuleVariant(entry.getKey());
				if (module == null) throw new IllegalArgumentException("Missing module variant: " + id);
				ShipHullSpecAPI hull = module.getHullSpec();
				if (hull == null) throw new IllegalArgumentException("Missing module hull: " + id);
				id = hull.getHullId();
				name = hull.getHullName();
				if (name == null || name.isBlank()) throw new IllegalArgumentException("Missing module hull name: " + id);
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.tooltip.module.unavailable",
						BattleNext_Logger.fields().put("variant_id", selectionId).put("module_slot", entry.getKey())
								.put("module_variant_id", entry.getValue()).put("object_id", id)
								.put("fallback", id).build(), exception);
				name = commonStrings.get("unavailable") + ": " + id;
			}
			counts.merge(id, 1, Integer::sum);
			names.put(id, name);
		}
		return countedSection(variantStrings.get("modules"), counts, names);
	}

	private VariantSection hullmodSection(ShipVariantAPI variant, String selectionId) {

		Set<String> ids = new TreeSet<>(variant.getHullMods());
		Set<String> builtIns = new TreeSet<>(variant.getHullSpec().getBuiltInMods());
		Set<String> permanent = variant.getPermaMods();
		Set<String> sMods = variant.getSMods();
		Set<String> builtInSMods = variant.getSModdedBuiltIns();
		Set<String> suppressed = variant.getSuppressedMods();
		ids.addAll(builtIns);
		ids.addAll(permanent);
		ids.addAll(sMods);
		ids.addAll(builtInSMods);
		ids.addAll(suppressed);
		List<String> rows = new ArrayList<>();
		for (String id : ids) {
			List<String> states = new ArrayList<>();
			if (builtIns.contains(id)) states.add(variantStrings.get("built_in"));
			if (permanent.contains(id)) states.add(variantStrings.get("permanent"));
			if (sMods.contains(id)) states.add(variantStrings.get("s_mod"));
			if (builtInSMods.contains(id)) states.add(variantStrings.get("built_in_s_mod"));
			if (suppressed.contains(id)) states.add(variantStrings.get("suppressed"));
			String name;
			try {
				HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
				if (spec == null) throw new IllegalArgumentException("Unknown hullmod: " + id);
				if (spec.isHiddenEverywhere()) continue;
				if (spec.hasTag(Tags.HULLMOD_DMOD)) states.add(variantStrings.get("d_mod"));
				name = spec.getDisplayName();
				if (name == null || name.isBlank()) throw new IllegalArgumentException("Missing hullmod name: " + id);
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.tooltip.hullmod.unavailable",
						BattleNext_Logger.fields().put("variant_id", selectionId).put("hullmod_id", id)
								.put("states", states).put("fallback", id).build(), exception);
				name = commonStrings.get("unavailable") + ": " + id;
			}
			rows.add(name + (states.isEmpty() ? "" : " [" + String.join(", ", states) + "]"));
		}
		return new VariantSection(variantStrings.get("hullmods"), rows.size(), rows);
	}

	private VariantSection countedSection(String title, Map<String, Integer> counts, Map<String, String> names) {
		List<String> rows = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			rows.add(names.get(entry.getKey()) + " ×" + entry.getValue());
		}
		return new VariantSection(title, counts.values().stream().mapToInt(Integer::intValue).sum(), rows);
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
			BattleNext_Logger.warn(BattleNext_VariantSourceProvider.class, "variant.resolve.degraded",
				BattleNext_Logger.fields().put("variant_id", id).put("fallback", "Omitted option or missing icon").build(), exception);
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
