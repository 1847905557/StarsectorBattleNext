package data.restriction.numeric;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.List;

public class FleetMaxDp implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		float total = 0f;
		for (FleetMemberAPI member : assembledFleet.getMembers()) {
			total += member.getDeploymentPointsCost();
		}
		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(), total);
	}
}
