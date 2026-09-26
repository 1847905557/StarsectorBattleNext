package data.model.rules;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.rules.io.BattleNext_RulesSaver;
import data.model.rules.io.BattleNext_RulesStandardLoader;

import java.util.List;

public class BattleNext_RulesStore extends BattleNext_BaseStore<BattleNext_RulesModel> {

	private final BattleNext_RulesStandardLoader standardLoader = new BattleNext_RulesStandardLoader();
	private final BattleNext_RulesSaver saver = new BattleNext_RulesSaver();

	public BattleNext_RulesStore() {
		super("Rules catalog has not been loaded");
	}

	@Override
	protected BattleNext_RulesSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_RulesModel>> sources() {
		return List.of(standardLoader);
	}
}
