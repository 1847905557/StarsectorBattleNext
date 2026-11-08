package data.restriction.numeric;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionScript;
import data.restriction.BattleNext_RestrictionUtils;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.member.BattleNext_MemberModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class OfficerMaxLevel implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		List<String> reasons = new ArrayList<>();
		List<BattleNext_MemberModel> members = assembledFleet.getDefinition().getMembersReadonly();
		for (int index = 0; index < members.size(); index++) {
			BattleNext_MemberModel member = members.get(index);
			if (member.getSkillsReadonly().isEmpty()) continue;
			reasons.addAll(BattleNext_RestrictionUtils.maximum(restriction,
					assembledFleet.getDefinition().getId() + " member[" + index + "]",
					new HashSet<>(member.getSkillsReadonly()).size()));
		}
		return reasons;
	}
}
