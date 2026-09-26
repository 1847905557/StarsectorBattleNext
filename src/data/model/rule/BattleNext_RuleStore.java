package data.model.rule;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.rule.io.BattleNext_RuleSaver;
import data.model.rule.io.BattleNext_RuleStandardLoader;

import java.util.List;

public class BattleNext_RuleStore extends BattleNext_BaseStore<BattleNext_RuleModel> {

	private final BattleNext_RuleStandardLoader standardLoader = new BattleNext_RuleStandardLoader();
	private final BattleNext_RuleSaver saver = new BattleNext_RuleSaver();

	public BattleNext_RuleStore() {
		super("Rules catalog has not been loaded");
	}

	@Override
	protected BattleNext_RuleSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_RuleModel>> sources() {
		return List.of(standardLoader);
	}
}
