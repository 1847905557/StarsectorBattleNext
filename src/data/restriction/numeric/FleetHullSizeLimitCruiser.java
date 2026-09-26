package data.restriction.numeric;

import com.fs.starfarer.api.combat.ShipAPI;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionUtils;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.List;

public class FleetHullSizeLimitCruiser implements BattleNext_RestrictionScript {

	@Override
	public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
		return BattleNext_RestrictionUtils.maximum(restriction, assembledFleet.getDefinition().getId(),
				BattleNext_RestrictionUtils.hullSizeCount(assembledFleet, ShipAPI.HullSize.CRUISER));
	}
}
