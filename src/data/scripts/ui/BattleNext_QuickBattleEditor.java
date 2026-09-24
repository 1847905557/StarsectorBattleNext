package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.match.BattleNext_Match;
import data.scripts.match.BattleNext_MatchBook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleEditor {

	private final Runnable backAction;
	private final BattleNext_MatchBook book = new BattleNext_MatchBook();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final List<Row> rows = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI content;
	private LabelAPI status;
	private String lastAttempt = "";
	private boolean loaded;

	public BattleNext_QuickBattleEditor(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		if (!loaded) {
			load();
		}
		rebuild();
	}

	public void advance() {
		String current = serializeFields();
		if (!current.equals(lastAttempt)) {
			lastAttempt = current;
			trySave(false);
		}
	}

	public void processInput(List<InputEventAPI> events) {
	}

	public void buttonPressed(Object buttonId) {
		if ("add".equals(buttonId)) {
			book.add(new BattleNext_Match("quick-" + book.getMatches().size(), "", "", -1, 1f));
			rebuild();
		} else if ("save".equals(buttonId)) {
			trySave(true);
		} else if ("back".equals(buttonId)) {
			trySave(true);
			backAction.run();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("delete:")) {
			removeRow(Integer.parseInt(((String) buttonId).substring("delete:".length())));
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("duplicate:")) {
			duplicateRow(Integer.parseInt(((String) buttonId).substring("duplicate:".length())));
		}
	}

	private void load() {
		fleetStore.reload();
		try {
			book.load();
		} catch (IOException exception) {
			try {
				book.loadBuiltIn();
			} catch (Exception ignored) {
			}
		}
		if (book.getMatches().isEmpty()) {
			try {
				book.loadBuiltIn();
			} catch (Exception ignored) {
			}
		}
		loaded = true;
	}

	private void rebuild() {
		if (panel == null) {
			return;
		}
		if (content != null) {
			panel.removeComponent(content);
		}
		content = panel.createUIElement(720f, 480f, false);
		BattleNext_UiTheme.configure(content);
		content.addTitle(text("BattleNext_ui_page_quick"));
		status = content.addPara(text("BattleNext_ui_ready"), 8f, BattleNext_UiTheme.MUTED);
		rows.clear();
		List<BattleNext_Match> matches = book.getMatches();
		for (int index = 0; index < matches.size(); index++) {
			addRow(index, matches.get(index));
		}
		content.addButton(text("BattleNext_ui_add"), "add", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 150f, 30f, 8f);
		content.addButton(text("BattleNext_ui_save"), "save", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 150f, 30f, 8f);
		content.addButton(text("BattleNext_ui_back"), "back", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 150f, 30f, 8f);
		panel.addUIElement(content).inMid();
		lastAttempt = serializeFields();
	}

	private void addRow(int index, BattleNext_Match match) {
		content.addPara(text("BattleNext_ui_match_row") + " " + index, 8f, BattleNext_UiTheme.TEXT);
		Row row = new Row(index);
		row.player = addField(match.getPlayer());
		row.enemy = addField(match.getEnemy());
		row.map = addField(Integer.toString(match.getMap()));
		row.size = addField(Float.toString(match.getSize()));
		if (fleetStore.get(match.getPlayer()) == null) {
			content.addPara(text("BattleNext_ui_invalid_player"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (fleetStore.get(match.getEnemy()) == null) {
			content.addPara(text("BattleNext_ui_invalid_enemy"), 4f, BattleNext_UiTheme.MUTED);
		}
		content.addButton(text("BattleNext_ui_duplicate"), "duplicate:" + index, BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 130f, 28f, 4f);
		content.addButton(text("BattleNext_ui_delete"), "delete:" + index, BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 130f, 28f, 4f);
		rows.add(row);
	}

	private TextFieldAPI addField(String value) {
		TextFieldAPI field = content.addTextField(155f, 26f);
		field.setText(value);
		field.setMaxChars(120);
		return field;
	}

	private void removeRow(int index) {
		List<BattleNext_Match> values = new ArrayList<>(book.getMatches());
		if (index >= 0 && index < values.size()) {
			values.remove(index);
			book.replaceAll(values);
			rebuild();
			trySave(true);
		}
	}

	private void duplicateRow(int index) {
		List<BattleNext_Match> values = new ArrayList<>(book.getMatches());
		if (index >= 0 && index < values.size()) {
			BattleNext_Match source = values.get(index);
			values.add(new BattleNext_Match("quick-" + values.size(), source.getPlayer(), source.getEnemy(), source.getMap(), source.getSize()));
			book.replaceAll(values);
			rebuild();
			trySave(true);
		}
	}

	private boolean trySave(boolean reportSuccess) {
		List<BattleNext_Match> values = new ArrayList<>();
		for (Row row : rows) {
			try {
				int map = Integer.parseInt(row.map.getText().trim());
				float size = Float.parseFloat(row.size.getText().trim());
				if (map < -1 || map > 11 || !Float.isFinite(size) || size <= 0f) {
					setStatus(text("BattleNext_ui_invalid_map_size"));
					return false;
				}
				values.add(new BattleNext_Match("quick-" + values.size(), row.player.getText(), row.enemy.getText(), map, size));
			} catch (NumberFormatException exception) {
				setStatus(text("BattleNext_ui_invalid_map_size"));
				return false;
			}
		}
		book.replaceAll(values);
		try {
			book.save();
			if (reportSuccess) {
				setStatus(text("BattleNext_ui_saved"));
			}
			return true;
		} catch (IOException exception) {
			setStatus(text("BattleNext_ui_save_failed"));
			return false;
		}
	}

	private String serializeFields() {
		StringBuilder value = new StringBuilder();
		for (Row row : rows) {
			value.append(row.player.getText()).append('|').append(row.enemy.getText()).append('|')
					.append(row.map.getText()).append('|').append(row.size.getText()).append(';');
		}
		return value.toString();
	}

	private void setStatus(String value) {
		if (status != null) {
			status.setText(value);
		}
	}

	private String text(String key) {
		try {
			return Global.getSettings().getString("AI_Battles_Next", key);
		} catch (RuntimeException exception) {
			return key;
		}
	}

	private static final class Row {

		private final int index;
		private TextFieldAPI player;
		private TextFieldAPI enemy;
		private TextFieldAPI map;
		private TextFieldAPI size;

		private Row(int index) {
			this.index = index;
		}
	}
}
