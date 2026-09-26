package data.restriction.numeric;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.List;

public class RefitMaxDecks implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		int decks = 0;
		for (FleetMemberAPI member : assembledFleet.getMembers()) {
			decks += member.getVariant().getWings().size();
		}
		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(), decks);
	}
}
