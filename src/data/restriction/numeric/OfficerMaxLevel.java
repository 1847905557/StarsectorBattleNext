package data.restriction.numeric;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.member.BattleNext_MemberModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class OfficerMaxLevel implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		List<String> reasons = new ArrayList<>();
		for (int index = 0; index < assembledFleet.getDefinition().getMembers().size(); index++) {
			BattleNext_MemberModel member = assembledFleet.getDefinition().getMembers().get(index);
			if (member.getSkills().isEmpty()) continue;
			reasons.addAll(BattleNext_RestrictionUtils.maximum(restriction,
					assembledFleet.getDefinition().getId() + " member[" + index + "]",
					new HashSet<>(member.getSkills()).size()));
		}
		return reasons;
	}
}
