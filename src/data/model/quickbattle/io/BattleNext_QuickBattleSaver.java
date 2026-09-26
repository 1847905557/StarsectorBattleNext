package data.model.quickbattle.io;

import data.model.BattleNext_BaseSaver;
import data.model.BattleNext_LoadSource;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.tools.BattleNext_CsvLine;
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
	private static final String LEGACY_PREFIX = "ab_player";

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
			text.append(sourceLine(model, targetPath.equals(LEGACY_PATH))).append('\n');
		}
		BattleNext_ModFileManager.writeText(path, text.toString());
	}

	private String sourcePath(BattleNext_QuickBattleModel model) {
		return isLegacy(model) ? LEGACY_PATH : STANDARD_PATH;
	}

	private String sourceLine(BattleNext_QuickBattleModel model, boolean legacy) {
		if (model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			return model.getRawContent().isEmpty() ? "#" : model.getRawContent();
		}
		String player = legacy ? stripLegacyPrefix(model.getPlayer()) : model.getPlayer();
		String enemy = legacy ? stripLegacyPrefix(model.getEnemy()) : model.getEnemy();
		String line = BattleNext_CsvLine.escape(player) + "," + BattleNext_CsvLine.escape(enemy)
				+ "," + model.getMap() + "," + model.getMapScale();
		return model.isEnabled() ? line : "# " + line;
	}

	private String stripLegacyPrefix(String value) {
		return value.startsWith(LEGACY_PREFIX) ? value.substring(LEGACY_PREFIX.length()) : value;
	}
}
