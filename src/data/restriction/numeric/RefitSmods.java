package data.restriction.numeric;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefitSmods implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		int count = 0;
		for (FleetMemberAPI member : assembledFleet.getMembers()) {
			Set<String> fitted = new HashSet<>(member.getVariant().getSMods());
			fitted.addAll(member.getVariant().getSModdedBuiltIns());
			count += fitted.size();
		}
		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(), count);
	}
}
