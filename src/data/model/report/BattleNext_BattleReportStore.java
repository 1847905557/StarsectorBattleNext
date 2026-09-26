package data.model.report;

import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.report.io.BattleNext_BattleReportStandardLoader;
import data.model.report.io.BattleNext_BattleReportSaver;

import java.util.List;

public class BattleNext_BattleReportStore extends BattleNext_BaseStore<BattleNext_BattleReportModel> {

	private final BattleNext_BattleReportStandardLoader standardLoader = new BattleNext_BattleReportStandardLoader();
	private final BattleNext_BattleReportSaver saver = new BattleNext_BattleReportSaver();

	public BattleNext_BattleReportStore() {
		super("Report catalog has not been loaded");
	}

	@Override
	protected BattleNext_BattleReportSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_BattleReportModel>> sources() {
		return List.of(standardLoader);
	}
}
