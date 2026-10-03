package data.scripts.fleet;

import data.scripts.tools.BattleNext_Logger;

import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.*;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_RulesCheck {

	public List<String> missingRestrictions(BattleNext_RuleModel rule) {
		List<String> reasons = new ArrayList<>();
		for (BattleNext_RuleRestriction restriction : rule.getRestrictions()) {
			if (restriction.isEnabled() && BattleNext_RestrictionRegistry.getInstance().get(restriction.getId()) == null) {
				reasons.add("Missing restriction script: " + restriction.getId());
			}
		}
		if (!reasons.isEmpty()) {
			BattleNext_Logger.warn(BattleNext_RulesCheck.class, "rule.restrictions.rejected",
				BattleNext_Logger.fields().put("rule", rule).put("reasons", reasons).build());
		}
		return reasons;
	}

	public List<String> validateFleet(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleModel rule) {
		List<String> reasons = new ArrayList<>();
		if (rule == null) return reasons;
		for (BattleNext_RuleRestriction restriction : rule.getRestrictions()) {
			if (!restriction.isEnabled()) continue;
			BattleNext_RestrictionSpec entry = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
			if (entry == null) {
				reasons.add(assembledFleet.getDefinition().getId() + " missing restriction: " + restriction.getId());
				continue;
			}
			BattleNext_RestrictionScript implementation = (BattleNext_RestrictionScript)BattleNext_RestrictionRegistry
					.getInstance().create(entry.getId());
			reasons.addAll(implementation.validate(assembledFleet, restriction));
		}
		if (!reasons.isEmpty()) {
			BattleNext_Logger.warn(BattleNext_RulesCheck.class, "rule.fleet.rejected",
				BattleNext_Logger.fields().put("fleet", assembledFleet.getDefinition()).put("rule", rule).put("reasons", reasons).build());
		}
		return reasons;
	}

	public List<String> validateEquipment(BattleNext_RuleModel rule, String context,
			String category, String itemId, String source) {
		List<String> reasons = new ArrayList<>();
		for (BattleNext_RuleRestriction restriction : rule.getRestrictions()) {
			if (!restriction.isEnabled()) continue;
			BattleNext_RestrictionSpec entry = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
			if (entry != null && "list".equals(entry.getType())) {
				BattleNext_RestrictionScript implementation = (BattleNext_RestrictionScript)BattleNext_RestrictionRegistry
						.getInstance().create(entry.getId());
				if (implementation instanceof BattleNext_EquipmentRestriction filter) {
					reasons.addAll(filter.validateItem(restriction,
							new BattleNext_RestrictionUtils.RestrictionEquipmentItem(context, category, itemId, source)));
				}
			}
		}
		if (!reasons.isEmpty()) {
			BattleNext_Logger.warn(BattleNext_RulesCheck.class, "rule.equipment.rejected",
				BattleNext_Logger.fields().put("rule", rule).put("context", context).put("category", category).put("item_id", itemId).put("source", source).put("reasons", reasons).build());
		}
		return reasons;
	}
}
