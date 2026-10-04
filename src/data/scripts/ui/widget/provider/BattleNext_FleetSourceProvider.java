package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip.FleetMemberLine;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.TreeMap;

public class BattleNext_FleetSourceProvider extends BattleNext_BaseSourceProvider {

	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection commonStrings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	public BattleNext_FleetSourceProvider(BattleNext_FleetStore fleetStore) {
		this.fleetStore = fleetStore;
	}

	@Override
	public List<OptionChoice> getOptions() {
		List<OptionChoice> options = new ArrayList<>();
		for (BattleNext_FleetModel fleet : fleetStore.getAll()) {
			options.add(new OptionChoice(fleet.getId(), fleet.getName()));
		}
		return options;
	}

	@Override
	public int getMaxStack(String id) {
		return 1;
	}

	@Override
	public String getIconPath(String id) {
		return "";
	}

	@Override
	public boolean expectsIcon() {
		return false;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {

		BattleNext_FleetModel fleet = fleetStore.get(option.getId());
		if (fleet == null) {
			IllegalArgumentException exception = new IllegalArgumentException("Unknown fleet: " + option.getId());
			BattleNext_Logger.warn(BattleNext_FleetSourceProvider.class, "fleet.tooltip.unavailable",
					BattleNext_Logger.fields().put("fleet_id", option.getId()).build(), exception);
			BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(
					option.getLabel(), option.getId(), "", List.of(commonStrings.get("unavailable"))));
			return;
		}
		Preview preview = new Preview();
		Map<String, String> personalities = new LinkedHashMap<>();
		if (!fleet.getMembers().isEmpty()) {
			try {
				for (OptionChoice personality : new BattleNext_PersonalitySourceProvider().getOptions()) {
					personalities.put(personality.getId(), personality.getLabel());
				}
			} catch (RuntimeException exception) {
				reportFailure(preview, fleet, -1, "", "personalities", exception);
			}
		}
		for (int index = 0; index < fleet.getMembers().size(); index++) {
			BattleNext_MemberModel member = fleet.getMembers().get(index);
			String hullName = commonStrings.get("unavailable") + ": " + member.getVariantId();
			ShipVariantAPI variant;
			try {
				BattleNext_VariantBuilder local = fleet.getVariant(member.getVariantId());
				variant = local == null ? Global.getSettings().getVariant(member.getVariantId()) : local.build();
				if (variant == null) throw new IllegalArgumentException("Unknown variant: " + member.getVariantId());
			} catch (RuntimeException exception) {
				reportFailure(preview, fleet, index, "", "variant", exception);
				variant = null;
			}
			if (variant != null) {
				ShipHullSpecAPI hull = null;
				try {
					hull = variant.getHullSpec();
					if (hull == null) throw new IllegalArgumentException("Missing hull: " + member.getVariantId());
					hullName = hull.getHullName();
				} catch (RuntimeException exception) {
					reportFailure(preview, fleet, index, "", "hull", exception);
				}
				if (hull != null) {
					for (String phase : List.of("base_dp", "base_fp")) {
						try {
							if (phase.equals("base_dp")) {
								preview.dp += hull.getSuppliesToRecover();
							} else {
								preview.fp += hull.getFleetPoints();
							}
						} catch (RuntimeException exception) {
							reportFailure(preview, fleet, index, "", phase, exception);
						}
					}
				}
				countWings(preview, fleet, index, variant, "", Collections.newSetFromMap(new IdentityHashMap<>()));
			}
			Map<String, Integer> skills = new LinkedHashMap<>();
			for (String skill : member.getSkills()) {
				skills.merge(skill, 1, Integer::sum);
			}
			String personalityId = skills.isEmpty() || member.getPersonality().isEmpty() ? "steady" : member.getPersonality();
			String personality = personalities.get(personalityId);
			if (personality == null) {
				personality = personalityId;
				reportFailure(preview, fleet, index, "", "personality",
						new IllegalArgumentException("Unknown personality: " + personalityId));
			}
			String officer = skills.isEmpty() ? fleetStrings.get("officer_none")
					: fleetStrings.get("officer_level") + " " + skills.size();
			int elite = (int) skills.values().stream().filter(count -> count > 1).count();
			preview.members.add(new FleetMemberLine(hullName, officer, elite, personality));
		}
		String partial = " (" + fleetStrings.get("partial") + ")";
		List<String> summary = List.of(fleetStrings.get("ships") + ": " + fleet.getMembers().size() + " - "
						+ fleetStrings.get("fighter_wings") + ": " + preview.wings + (preview.partialWings ? partial : ""),
				fleetStrings.get("base_dp") + ": " + BattleNext_I18nSection.buildFactor(preview.dp)
						+ (preview.partialDp ? partial : "") + " - " + fleetStrings.get("base_fp") + ": "
						+ preview.fp + (preview.partialFp ? partial : ""));
		BattleNext_EquipmentTooltip.renderFleet(tooltip, new BattleNext_EquipmentTooltip.Content(
				option.getLabel(), option.getId(), "", preview.errors), summary,
				fleetStrings.get("members_heading"), preview.members);
	}

	private void countWings(Preview preview, BattleNext_FleetModel fleet, int index, ShipVariantAPI variant,
			String path, Set<ShipVariantAPI> visiting) {

		if (!visiting.add(variant)) {
			reportFailure(preview, fleet, index, path, "wings", new IllegalArgumentException("Cyclic variant modules"));
			return;
		}
		try {
			try {
				preview.wings += variant.getFittedWings().size();
			} catch (RuntimeException exception) {
				reportFailure(preview, fleet, index, path, "wings", exception);
			}
			Map<String, String> modules;
			try {
				modules = new TreeMap<>(variant.getStationModules());
			} catch (RuntimeException exception) {
				reportFailure(preview, fleet, index, path, "modules", exception);
				return;
			}
			for (String slot : modules.keySet()) {
				String modulePath = path.isEmpty() ? slot : path + "/" + slot;
				try {
					ShipVariantAPI module = variant.getModuleVariant(slot);
					if (module == null) throw new IllegalArgumentException("Missing module: " + modules.get(slot));
					countWings(preview, fleet, index, module, modulePath, visiting);
				} catch (RuntimeException exception) {
					reportFailure(preview, fleet, index, modulePath, "modules", exception);
				}
			}
		} finally {
			visiting.remove(variant);
		}
	}

	private void reportFailure(Preview preview, BattleNext_FleetModel fleet, int index, String path,
			String phase, RuntimeException exception) {

		boolean unresolved = phase.equals("variant") || phase.equals("hull");
		if (unresolved || phase.equals("base_dp")) {
			preview.partialDp = true;
		}
		if (unresolved || phase.equals("base_fp")) {
			preview.partialFp = true;
		}
		if (phase.equals("variant") || phase.equals("wings") || phase.equals("modules")) {
			preview.partialWings = true;
		}
		String variantId = index < 0 ? "" : fleet.getMembers().get(index).getVariantId();
		BattleNext_Logger.warn(BattleNext_FleetSourceProvider.class, "fleet.tooltip.preview.degraded",
				BattleNext_Logger.fields().put("fleet_id", fleet.getId()).put("member_index", index)
						.put("variant_id", variantId).put("module_path", path).put("phase", phase).build(), exception);
		preview.errors.add(commonStrings.get("load_failed") + ": "
				+ (index < 0 ? fleet.getId() : variantId) + (path.isEmpty() ? "" : "/" + path) + " - " + exception.getMessage());
	}

	private static class Preview {
		private float dp;
		private int fp;
		private int wings;
		private boolean partialDp;
		private boolean partialFp;
		private boolean partialWings;
		private final List<FleetMemberLine> members = new ArrayList<>();
		private final List<String> errors = new ArrayList<>();
	}
}
