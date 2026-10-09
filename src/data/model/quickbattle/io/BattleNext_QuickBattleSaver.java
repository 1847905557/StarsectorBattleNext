package data.model.quickbattle.io;

import data.model.BattleNext_BaseSaver;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_QuickBattleSaver extends BattleNext_BaseSaver<BattleNext_QuickBattleModel> {

	public static final String STANDARD_PATH = "tournament/quick_battle.csv";
	public static final String LEGACY_PATH = "tournament/PvP_matches.csv";
	private static final String HEADER = "player,enemy,map,size";

	private final Supplier<List<BattleNext_QuickBattleModel>> entries;

	public BattleNext_QuickBattleSaver(Supplier<List<BattleNext_QuickBattleModel>> entries) {
		this.entries = entries;
	}

	@Override
	public String save(BattleNext_QuickBattleModel model) throws IOException, JSONException {
		String path = isLegacy(model) ? LEGACY_PATH : STANDARD_PATH;
		write(path, upsert(entries.get(), model), path);
		return path;
	}

	@Override
	public void delete(BattleNext_QuickBattleModel model) throws IOException {
		String path = isLegacy(model) ? LEGACY_PATH : STANDARD_PATH;
		List<BattleNext_QuickBattleModel> view = new ArrayList<>();
		for (BattleNext_QuickBattleModel entry : entries.get()) {
			if (!entry.getId().equals(model.getId())) {
				view.add(entry);
			}
		}
		write(path, view, path);
	}

	private boolean isLegacy(BattleNext_QuickBattleModel model) {
		return model.getSourceType() == BattleNext_LoadSource.LEGACY
				|| LEGACY_PATH.equals(model.getSourcePath());
	}

	private List<BattleNext_QuickBattleModel> upsert(List<BattleNext_QuickBattleModel> current,
			BattleNext_QuickBattleModel model) {
		List<BattleNext_QuickBattleModel> view = new ArrayList<>();
		boolean replaced = false;
		for (BattleNext_QuickBattleModel entry : current) {
			if (entry.getId().equals(model.getId())) {
				view.add(model);
				replaced = true;
			} else {
				view.add(entry);
			}
		}
		if (!replaced) {
			view.add(model);
		}
		return view;
	}

	private void write(String path, List<BattleNext_QuickBattleModel> models, String targetPath)
			throws IOException {
		StringBuilder text = new StringBuilder();
		text.append(HEADER).append('\n');
		for (BattleNext_QuickBattleModel model : models) {
			if (!targetPath.equals(sourcePath(model))) {
				continue;
			}
			text.append(model.toSourceLine()).append('\n');
		}
		BattleNext_ModFileManager.writeText(path, text.toString());
	}

	private String sourcePath(BattleNext_QuickBattleModel model) {
		return isLegacy(model) ? LEGACY_PATH : STANDARD_PATH;
	}

}
