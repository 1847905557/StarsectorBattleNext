package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_QuickBattleStore;
import data.scripts.util.BattleNext_I18nSection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleEditor {

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "");
	private final List<Row> rows = new ArrayList<>();
	private CustomPanelAPI panel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI content;
	private TooltipMakerAPI footer;
	private LabelAPI status;
	private String statusText = "";
	private String savedFields = "";
	private String loadError = "";
	private boolean loaded;

	public BattleNext_QuickBattleEditor(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	public void attach(CustomPanelAPI panel) {
		if (this.panel != panel) {
			heading = null;
			content = null;
			footer = null;
		}
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
			store.save();
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
			store.add(new BattleNext_Match("quick-" + store.getMatches().size(), "", "", -1, 1f));
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
		store.replaceAll(List.of());
		loadError = "";
		try {
			if (store.hasUserFile()) {
				store.load();
			} else {
				store.loadBuiltIn();
			}
		} catch (Exception exception) {
			loadError = text("BattleNext_ui_load_failed") + ": " + exception.getMessage();
		}
		loaded = true;
	}

	private void rebuild() {
		if (panel == null) return;
		if (heading != null) panel.removeComponent(heading);
		if (content != null) panel.removeComponent(content);
		if (footer != null) panel.removeComponent(footer);

		heading = BattleNext_PageLayout.heading(panel, text("BattleNext_ui_quick_battle"));
		content = BattleNext_PageLayout.column(panel, 0f, panel.getPosition().getWidth());
		footer = BattleNext_PageLayout.footer(panel);
		content.addTitle(text("BattleNext_ui_quick_battle"));
		status = content.addPara(loadError.isEmpty() ? text("BattleNext_ui_ready") : loadError,
				8f, BattleNext_UiTheme.MUTED);
		rows.clear();
		List<BattleNext_Match> matches = store.getMatches();
		for (int index = 0; index < matches.size(); index++) {
			addRow(index, matches.get(index));
		}
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_add"), "add", 150f).getPosition().inBL(0f, 0f);
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_save"), "save", 150f).getPosition().inBL(160f, 0f);
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_back"), "back", 150f).getPosition().inBL(320f, 0f);
	}

	private void addRow(int index, BattleNext_Match match) {
		float width = panel.getPosition().getWidth() - 20f;
		CustomPanelAPI card = Global.getSettings().createCustom(width, 112f, inputOwner);
		TooltipMakerAPI title = card.createUIElement(width, 30f, false);
		title.addSectionHeading(text("BattleNext_ui_battle_row") + " " + (index + 1),
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.HEADER,
				com.fs.starfarer.api.ui.Alignment.MID, 0f).getPosition().inTL(0f, 0f);
		card.addUIElement(title).inTL(0f, 0f);
		float fieldWidth = (width - 180f) * 0.25f;
		Row row = new Row(index);
		row.player = addField(card, 0f, fieldWidth, text("BattleNext_ui_battle_player"), match.getPlayer());
		row.enemy = addField(card, fieldWidth, fieldWidth, text("BattleNext_ui_battle_enemy"), match.getEnemy());
		row.map = addField(card, fieldWidth * 2f, fieldWidth, text("BattleNext_ui_battle_map"), Integer.toString(match.getMap()));
		row.size = addField(card, fieldWidth * 3f, fieldWidth, text("BattleNext_ui_battle_size"), Float.toString(match.getSize()));
		TooltipMakerAPI actions = card.createUIElement(160f, 70f, false);
		BattleNext_PageLayout.button(actions, text("BattleNext_ui_duplicate"), "duplicate:" + index, 150f);
		BattleNext_PageLayout.button(actions, text("BattleNext_ui_delete"), "delete:" + index, 150f);
		card.addUIElement(actions).inTR(0f, 34f);
		content.addCustom(card, 8f);
		boolean invalidPlayer = fleetStore.get(match.getPlayer()) == null;
		boolean invalidEnemy = fleetStore.get(match.getEnemy()) == null;
		if (invalidPlayer) {
			content.addPara(text("BattleNext_ui_invalid_player"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (invalidEnemy) {
			content.addPara(text("BattleNext_ui_invalid_enemy"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (invalidPlayer || invalidEnemy) {
			content.addPara(text("BattleNext_ui_skipped_battle"), 4f, BattleNext_UiTheme.MUTED);
		}
		rows.add(row);
	}

	private TextFieldAPI addField(CustomPanelAPI card, float x, float width, String label, String value) {
		TooltipMakerAPI maker = card.createUIElement(width - 5f, 70f, false);
		maker.addPara(label, 0f, BattleNext_UiTheme.MUTED);
		TextFieldAPI field = maker.addTextField(width - 15f, 2f);
		field.setText(value);
		field.setMaxChars(120);
		card.addUIElement(maker).inTL(x, 36f);
		return field;
	}

	private void removeRow(int index) {
		if (index < 0 || index >= rows.size() || !captureFields(index)) return;
		rebuild();
	}

	private void duplicateRow(int index) {
		if (!captureFields()) return;
		List<BattleNext_Match> values = new ArrayList<>(store.getMatches());
		if (index >= 0 && index < values.size()) {
			BattleNext_Match source = values.get(index);
			values.add(new BattleNext_Match("quick-" + values.size(), source.getPlayer(), source.getEnemy(), source.getMap(), source.getSize()));
			store.replaceAll(values);
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
		store.replaceAll(values);
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
