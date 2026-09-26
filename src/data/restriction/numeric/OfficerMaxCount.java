package data.restriction.numeric;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.List;

public class OfficerMaxCount implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		long count = assembledFleet.getDefinition().getMembers().stream()
				.filter(member -> !member.getSkills().isEmpty()).count();
		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(), count);
	}
}
