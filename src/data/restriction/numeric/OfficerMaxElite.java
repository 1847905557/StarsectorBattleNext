package data.restriction.numeric;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionScript;
import data.restriction.BattleNext_RestrictionUtils;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.member.BattleNext_MemberModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfficerMaxElite implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		List<String> reasons = new ArrayList<>();
		List<BattleNext_MemberModel> members = assembledFleet.getDefinition().getMembersReadonly();
		for (int index = 0; index < members.size(); index++) {
			BattleNext_MemberModel member = members.get(index);
			if (member.getSkillsReadonly().isEmpty()) continue;
			Map<String, Integer> counts = new HashMap<>();
			for (String skill : member.getSkillsReadonly()) counts.merge(skill, 1, Integer::sum);
			long elite = 0;
			for (int count : counts.values()) {
				if (count > 1) elite++;
			}

			reasons.addAll(BattleNext_RestrictionUtils.maximum(restriction,
					assembledFleet.getDefinition().getId() + " member[" + index + "]", elite));
		}
		return reasons;
	}
}
