package data.restriction;

import data.model.rule.BattleNext_RuleRestriction;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.List;

public interface BattleNext_RestrictionScript {

	List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction);
}
