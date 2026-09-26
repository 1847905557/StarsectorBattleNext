package data.restriction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.rule.BattleNext_RuleRestriction;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_RestrictionUtils {

	private BattleNext_RestrictionUtils() {

	}

	public static List<String> maximum(BattleNext_RuleRestriction restriction, String context, double actual) {
		if (actual <= restriction.getValue()) return List.of();
		return List.of(context + " " + restriction.getId() + " " + actual + "/" + restriction.getValue());
	}

	public static long hullSizeCount(BattleNext_AssembledFleet assembledFleet, ShipAPI.HullSize size) {
		return assembledFleet.getMembers().stream()
				.filter(member -> member.getVariant().getHullSpec().getHullSize() == size).count();
	}

	public static List<RestrictionEquipmentItem> equipment(BattleNext_AssembledFleet assembledFleet) {

		List<RestrictionEquipmentItem> result = new ArrayList<>();
		for (int index = 0; index < assembledFleet.getMembers().size(); index++) {
			FleetMemberAPI member = assembledFleet.getMembers().get(index);
			ShipVariantAPI variant = member.getVariant();
			ShipHullSpecAPI hull = variant.getHullSpec();
			String context = assembledFleet.getDefinition().getId() + " member[" + index + "] " + hull.getHullId();
			result.add(new RestrictionEquipmentItem(context, "hull", hull.getHullId(), source(hull.getSourceMod())));
			for (String slot : variant.getNonBuiltInWeaponSlots()) {
				String weaponId = variant.getWeaponId(slot);
				if (weaponId == null || weaponId.isEmpty()) continue;
				result.add(new RestrictionEquipmentItem(context + " slot " + slot, "weapon", weaponId,
						source(Global.getSettings().getWeaponSpec(weaponId).getSourceMod())));
			}
			List<String> builtInWings = new ArrayList<>(hull.getBuiltInWings());
			for (String wingId : variant.getWings()) {
				if (builtInWings.remove(wingId)) continue;
				result.add(new RestrictionEquipmentItem(context, "wing", wingId,
						source(Global.getSettings().getFighterWingSpec(wingId).getSourceMod())));
			}
			Set<String> fittedHullmods = new HashSet<>(variant.getHullMods());
			fittedHullmods.addAll(variant.getSMods());
			fittedHullmods.addAll(variant.getSModdedBuiltIns());
			for (String hullmodId : fittedHullmods) {
				if (hull.getBuiltInMods().contains(hullmodId)) continue;
				result.add(new RestrictionEquipmentItem(context, "hullmod", hullmodId,
						source(Global.getSettings().getHullModSpec(hullmodId).getSourceMod())));
			}
		}
		return result;
	}

	public static String itemReason(RestrictionEquipmentItem item, String relation, String restrictionId) {
		return item.getContext() + " " + item.getCategory() + " " + item.getId() + " " + relation + " " + restrictionId;
	}

	private static String source(ModSpecAPI mod) {
		if (mod == null) return "vanilla";
		return mod.getId() == null ? "" : mod.getId();
	}

	public static class RestrictionEquipmentItem {

		private final String context;
		private final String category;
		private final String id;
		private final String source;

		public RestrictionEquipmentItem(String context, String category, String id, String source) {
			this.context = context;
			this.category = category;
			this.id = id;
			this.source = source;
		}

		public String getContext() {
			return context;
		}

		public String getCategory() {
			return category;
		}

		public String getId() {
			return id;
		}

		public String getSource() {
			return source;
		}
	}
}
