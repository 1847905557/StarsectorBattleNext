package data.model.quickbattle.io;

import data.model.BattleNext_BaseSaver;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_QuickBattleSaver extends BattleNext_BaseSaver<BattleNext_QuickBattleModel> {

	private static final String USER_PATH = "tournament/quick_battle.csv";
	private static final String HEADER = "player,enemy,map,size";

	private final Supplier<List<BattleNext_QuickBattleModel>> entries;
	private final Supplier<List<String>> comments;

	public BattleNext_QuickBattleSaver(Supplier<List<BattleNext_QuickBattleModel>> entries,
			Supplier<List<String>> comments) {
		this.entries = entries;
		this.comments = comments;
	}

	@Override
	public String save(BattleNext_QuickBattleModel model) throws IOException, JSONException {
		write(upsert(entries.get(), model));
		return USER_PATH;
	}

	@Override
	public void delete(BattleNext_QuickBattleModel model) throws IOException {
		List<BattleNext_QuickBattleModel> view = new ArrayList<>();
		for (BattleNext_QuickBattleModel entry : entries.get()) {
			if (!entry.getId().equals(model.getId())) view.add(entry);
		}
		write(view);
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
		if (!replaced) view.add(model);
		return view;
	}

	private void write(List<BattleNext_QuickBattleModel> models) throws IOException {
		StringBuilder text = new StringBuilder();
		for (String comment : comments.get()) {
			text.append(comment).append('\n');
		}
		text.append(HEADER).append('\n');
		for (BattleNext_QuickBattleModel model : models) {
			text.append(model.toCsvLine()).append('\n');
		}
		BattleNext_ModFileManager.writeText(USER_PATH, text.toString());
	}
}
