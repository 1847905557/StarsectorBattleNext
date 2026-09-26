package data.restriction.filter;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_EquipmentRestriction;
import data.restriction.BattleNext_RestrictionUtils;

import java.util.List;

public class FilterBlackWing implements BattleNext_EquipmentRestriction {

	@Override
	public List<String> validateItem(BattleNext_RuleRestriction restriction, BattleNext_RestrictionUtils.RestrictionEquipmentItem item) {
		if (!"wing".equals(item.getCategory())) return List.of();
		if (!restriction.getItems().contains(item.getId())) return List.of();
		return List.of(BattleNext_RestrictionUtils.itemReason(item, "in", restriction.getId()));
	}
}
