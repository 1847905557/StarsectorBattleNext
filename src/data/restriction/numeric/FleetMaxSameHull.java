package data.restriction.numeric;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FleetMaxSameHull implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		Map<String, Integer> counts = new HashMap<>();
		for (FleetMemberAPI member : assembledFleet.getMembers()) {
			counts.merge(member.getVariant().getHullSpec().getHullId(), 1, Integer::sum);
		}
		List<String> reasons = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			reasons.addAll(BattleNext_RestrictionUtils.maximum(restriction,
					assembledFleet.getDefinition().getId() + " hull " + entry.getKey(), entry.getValue()));
		}
		return reasons;
	}
}
