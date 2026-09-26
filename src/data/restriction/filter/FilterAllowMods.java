package data.restriction.filter;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_EquipmentRestriction;
import data.restriction.BattleNext_RestrictionUtils;

import java.util.List;

public class FilterAllowMods implements BattleNext_EquipmentRestriction {

	@Override
	public List<String> validateItem(BattleNext_RuleRestriction restriction, BattleNext_RestrictionUtils.RestrictionEquipmentItem item) {
		if (item.getSource().isEmpty()) {
			return List.of(item.getContext() + " " + item.getCategory() + " " + item.getId() + " source mod unknown");
		}
		if (restriction.getItems().contains(item.getSource())) return List.of();
		return List.of(item.getContext() + " " + item.getCategory() + " " + item.getId()
				+ " mod " + item.getSource() + " outside " + restriction.getId());
	}
}
