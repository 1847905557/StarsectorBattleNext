package data.restriction.numeric;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionScript;
import data.restriction.BattleNext_RestrictionUtils;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.member.BattleNext_MemberModel;

import java.util.List;

public class OfficerMaxCount implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		long count = 0;
		for (BattleNext_MemberModel member : assembledFleet.getDefinition().getMembersReadonly()) {
			if (!member.getSkillsReadonly().isEmpty()) count++;
		}

		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(), count);
	}
}
