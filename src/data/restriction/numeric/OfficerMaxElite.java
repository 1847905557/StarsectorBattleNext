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
		for (int index = 0; index < assembledFleet.getDefinition().getMembers().size(); index++) {
			BattleNext_MemberModel member = assembledFleet.getDefinition().getMembers().get(index);
			if (member.getSkills().isEmpty()) continue;
			Map<String, Integer> counts = new HashMap<>();
			for (String skill : member.getSkills()) {
				counts.merge(skill, 1, Integer::sum);
			}
			long elite = counts.values().stream().filter(count -> count > 1).count();
			reasons.addAll(BattleNext_RestrictionUtils.maximum(restriction,
					assembledFleet.getDefinition().getId() + " member[" + index + "]", elite));
		}
		return reasons;
	}
}
