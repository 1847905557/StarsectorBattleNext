package data.restriction;

import data.model.rule.BattleNext_RuleRestriction;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.ArrayList;
import java.util.List;

public interface BattleNext_EquipmentRestriction extends BattleNext_RestrictionScript {

	List<String> validateItem(BattleNext_RuleRestriction restriction, BattleNext_RestrictionUtils.RestrictionEquipmentItem item);

	@Override
	default List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		List<String> reasons = new ArrayList<>();
		for (BattleNext_RestrictionUtils.RestrictionEquipmentItem item : BattleNext_RestrictionUtils.equipment(assembledFleet)) {
			reasons.addAll(validateItem(restriction, item));
		}
		return reasons;
	}
}
