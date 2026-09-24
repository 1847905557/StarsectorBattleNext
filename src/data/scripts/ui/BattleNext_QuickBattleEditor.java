package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.match.BattleNext_Match;
import data.scripts.match.BattleNext_MatchBook;
import data.scripts.util.BattleNext_I18nSection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleEditor {

	private final Runnable backAction;
	private final BattleNext_MatchBook book = new BattleNext_MatchBook();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("AI_Battles_Next", "");
	private final List<Row> rows = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI content;
	private LabelAPI status;
	private String statusText = "";
	private String savedFields = "";
	private String loadError = "";
	private boolean loaded;

	public BattleNext_QuickBattleEditor(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		if (this.panel != panel) content = null;
		this.panel = panel;
		if (!loaded) {
			load();
		}
		rebuild();
		if (savedFields.isEmpty()) savedFields = serializeFields();
	}

	public void reload() {
		loaded = false;
		load();
		content = null;
		savedFields = "";
	}

	public boolean hasUnsavedChanges() {
		return !serializeFields().equals(savedFields);
	}

	public String getStatusText() {
		return statusText;
	}

	public boolean save() {
		if (!captureFields()) return false;
		try {
			book.save();
			savedFields = serializeFields();
			setStatus(text("BattleNext_ui_saved"));
			return true;
		} catch (IOException exception) {
			setStatus(text("BattleNext_ui_save_failed"));
			return false;
		}
	}

	public void processInput(List<InputEventAPI> events) {
	}

	public void buttonPressed(Object buttonId) {
		if ("add".equals(buttonId)) {
			if (!captureFields()) return;
			book.add(new BattleNext_Match("quick-" + book.getMatches().size(), "", "", -1, 1f));
			rebuild();
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("back".equals(buttonId)) {
			backAction.run();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("delete:")) {
			removeRow(Integer.parseInt(((String) buttonId).substring("delete:".length())));
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("duplicate:")) {
			duplicateRow(Integer.parseInt(((String) buttonId).substring("duplicate:".length())));
		}
	}

	private void load() {
		fleetStore.reload();
		book.replaceAll(List.of());
		loadError = "";
		try {
			if (book.hasUserFile()) {
				book.load();
			} else {
				book.loadBuiltIn();
			}
		} catch (Exception exception) {
			loadError = text("BattleNext_ui_load_failed") + ": " + exception.getMessage();
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
		content = panel.createUIElement(panel.getPosition().getWidth() - 20f,
				panel.getPosition().getHeight() - 8f, true);
		BattleNext_UiTheme.configure(content);
		content.addTitle(text("BattleNext_ui_page_quick"));
		status = content.addPara(loadError.isEmpty() ? text("BattleNext_ui_ready") : loadError,
				8f, BattleNext_UiTheme.MUTED);
		rows.clear();
		List<BattleNext_Match> matches = book.getMatches();
		for (int index = 0; index < matches.size(); index++) {
			addRow(index, matches.get(index));
		}
		content.addButton(text("BattleNext_ui_add"), "add", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 150f, 30f, 8f);
		content.addButton(text("BattleNext_ui_save"), "save", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 150f, 30f, 8f);
		content.addButton(text("BattleNext_ui_back"), "back", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 150f, 30f, 8f);
		panel.addUIElement(content).inTL(10f, 0f);
	}

	private void addRow(int index, BattleNext_Match match) {
		content.addPara(text("BattleNext_ui_match_row") + " " + index, 8f, BattleNext_UiTheme.TEXT);
		Row row = new Row(index);
		row.player = addField(match.getPlayer());
		row.enemy = addField(match.getEnemy());
		row.map = addField(Integer.toString(match.getMap()));
		row.size = addField(Float.toString(match.getSize()));
		boolean invalidPlayer = fleetStore.get(match.getPlayer()) == null;
		boolean invalidEnemy = fleetStore.get(match.getEnemy()) == null;
		if (invalidPlayer) {
			content.addPara(text("BattleNext_ui_invalid_player"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (invalidEnemy) {
			content.addPara(text("BattleNext_ui_invalid_enemy"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (invalidPlayer || invalidEnemy) {
			content.addPara(text("BattleNext_ui_skipped_match"), 4f, BattleNext_UiTheme.MUTED);
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
		if (index < 0 || index >= rows.size() || !captureFields(index)) return;
		rebuild();
	}

	private void duplicateRow(int index) {
		if (!captureFields()) return;
		List<BattleNext_Match> values = new ArrayList<>(book.getMatches());
		if (index >= 0 && index < values.size()) {
			BattleNext_Match source = values.get(index);
			values.add(new BattleNext_Match("quick-" + values.size(), source.getPlayer(), source.getEnemy(), source.getMap(), source.getSize()));
			book.replaceAll(values);
			rebuild();
		}
	}

	private boolean captureFields() {
		return captureFields(-1);
	}

	private boolean captureFields(int skipIndex) {
		List<BattleNext_Match> values = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			if (index == skipIndex) continue;
			Row row = rows.get(index);
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
		return true;
	}

	private String serializeFields() {
		StringBuilder value = new StringBuilder();
		for (Row row : rows) {
			for (TextFieldAPI field : new TextFieldAPI[]{row.player, row.enemy, row.map, row.size}) {
				value.append(field.getText().length()).append(':').append(field.getText());
			}
		}
		return value.toString();
	}

	private void setStatus(String value) {
		statusText = value;
		if (status != null) {
			status.setText(value);
		}
	}

	private String text(String key) {
		return strings.get(key);
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
