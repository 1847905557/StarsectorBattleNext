package data.restriction.numeric;

import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionScript;
import data.restriction.BattleNext_RestrictionUtils;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.HashSet;
import java.util.List;

public class FleetSkillMaxCount implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(),
				new HashSet<>(assembledFleet.getDefinition().getSkills()).size());
	}
}
