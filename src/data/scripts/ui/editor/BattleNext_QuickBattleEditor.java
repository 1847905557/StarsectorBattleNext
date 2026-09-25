package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_QuickBattleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleEditor implements BattleNext_EditorView {

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final List<Row> rows = new ArrayList<>();
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private LabelAPI status;
	private String statusText = "";
	private String savedFields = "";
	private String loadError = "";
	private boolean loaded;

	public BattleNext_QuickBattleEditor(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		if (!loaded) {
			load();
		}
		createContents();
		if (savedFields.isEmpty()) savedFields = serializeState();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return !serializeState().equals(savedFields);
	}

	@Override
	public String getStatusText() {
		return statusText;
	}

	@Override
	public boolean serialize() {
		if (!captureFields()) return false;
		try {
			store.save();
		} catch (IOException exception) {
			setStatus(text("save_failed"));
			return false;
		}
		savedFields = serializeState();
		setStatus(text("saved"));
		return true;
	}

	@Override
	public void deserialize() {
		load();
		refreshContent();
		savedFields = serializeState();
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("add".equals(buttonId)) {
			if (!captureFields()) return;
			store.add(new BattleNext_Match("quick-" + store.getMatches().size(), "", "", -1, 1f));
			refreshContent();
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
			loadError = text("load_failed") + ": " + exception.getMessage();
		}
		loaded = true;
	}

	// heading (static) / battle rows (scrollable) / footer buttons (static)
	@Override
	public void createContents() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] bottom = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		contentRect = bottom[0];

		BattleNext_PageLayout.heading(header[0], panel, text("quick_battle"));

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		TooltipMakerAPI footer = BattleNext_PageLayout.footer(footerRect, panel);
		BattleNext_PageLayout.button(footer, text("add"), "add", 150f).getPosition().inBL(0f, 0f);
		BattleNext_PageLayout.button(footer, text("save"), "save", 150f).getPosition().inBL(160f, 0f);
		BattleNext_PageLayout.button(footer, text("back"), "back", 150f).getPosition().inBL(320f, 0f);

		refreshContent();
	}

	@Override
	public void refreshContent() {
		if (panel == null) return;
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, inputOwner).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(contentRect.getWidth(), contentRect.getHeight(), true);
		content.addTitle(text("quick_battle"));
		status = content.addPara(loadError.isEmpty() ? text("ready") : loadError,
				8f, BattleNext_UiTheme.MUTED);
		rows.clear();
		List<BattleNext_Match> matches = store.getMatches();
		for (int index = 0; index < matches.size(); index++) {
			addRow(index, matches.get(index), content);
		}
		contentPanel.addUIElement(content).inTL(0f, 0f);
	}

	// one battle card: title bar on top, four input columns, action buttons on the right
	private void addRow(int index, BattleNext_Match match, TooltipMakerAPI content) {
		float width = panel.getPosition().getWidth() - 20f;
		CustomPanelAPI card = Global.getSettings().createCustom(width, 112f, inputOwner);
		UIRect cardRect = new UIRect(0f, 0f, width, 112f);
		UIRect[] cardRows = cardRect.splitVertically(30f);
		UIRect[] body = cardRows[1].splitHorizontallyReverse(160f);
		UIRect fieldsRect = body[0];
		UIRect actionsRect = body[1];

		TooltipMakerAPI title = cardRows[0].beginTooltip(card, false);
		title.addSectionHeading(text("battle_row") + " " + (index + 1),
				BattleNext_UiTheme.TEXT, BattleNext_UiTheme.HEADER,
				com.fs.starfarer.api.ui.Alignment.MID, 0f).getPosition().inTL(0f, 0f);
		cardRows[0].addTooltip();

		float fieldWidth = fieldsRect.getWidth() * 0.25f;
		UIRect[] fieldRects = fieldsRect.splitHorizontally(fieldWidth, fieldWidth, fieldWidth, fieldWidth);
		Row row = new Row(index);
		row.player = addField(fieldRects[0], card, text("battle_player"), match.getPlayer());
		row.enemy = addField(fieldRects[1], card, text("battle_enemy"), match.getEnemy());
		row.map = addField(fieldRects[2], card, text("battle_map"), Integer.toString(match.getMap()));
		row.size = addField(fieldRects[3], card, text("battle_size"), Float.toString(match.getSize()));

		TooltipMakerAPI actions = actionsRect.beginTooltip(card, false);
		BattleNext_PageLayout.button(actions, text("duplicate"), "duplicate:" + index, 150f);
		BattleNext_PageLayout.button(actions, text("delete"), "delete:" + index, 150f);
		actionsRect.addTooltip();

		content.addCustom(card, 8f);
		boolean invalidPlayer = fleetStore.get(match.getPlayer()) == null;
		boolean invalidEnemy = fleetStore.get(match.getEnemy()) == null;
		if (invalidPlayer) {
			content.addPara(text("invalid_player"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (invalidEnemy) {
			content.addPara(text("invalid_enemy"), 4f, BattleNext_UiTheme.MUTED);
		}
		if (invalidPlayer || invalidEnemy) {
			content.addPara(text("skipped_battle"), 4f, BattleNext_UiTheme.MUTED);
		}
		rows.add(row);
	}

	private TextFieldAPI addField(UIRect rect, CustomPanelAPI card, String label, String value) {
		TooltipMakerAPI maker = rect.beginTooltip(card, false);
		maker.addPara(label, 0f, BattleNext_UiTheme.MUTED);
		TextFieldAPI field = maker.addTextField(rect.getWidth() - 15f, 2f);
		field.setText(value);
		field.setMaxChars(120);
		rect.addTooltip();
		return field;
	}

	private void removeRow(int index) {
		if (index < 0 || index >= rows.size() || !captureFields(index)) return;
		refreshContent();
	}

	private void duplicateRow(int index) {
		if (!captureFields()) return;
		List<BattleNext_Match> values = new ArrayList<>(store.getMatches());
		if (index >= 0 && index < values.size()) {
			BattleNext_Match source = values.get(index);
			values.add(new BattleNext_Match("quick-" + values.size(), source.getPlayer(), source.getEnemy(), source.getMap(), source.getSize()));
			store.replaceAll(values);
			refreshContent();
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
					setStatus(text("invalid_map_size"));
					return false;
				}
				values.add(new BattleNext_Match("quick-" + values.size(), row.player.getText(), row.enemy.getText(), map, size));
			} catch (NumberFormatException exception) {
				setStatus(text("invalid_map_size"));
				return false;
			}
		}
		store.replaceAll(values);
		return true;
	}

	@Override
	public String serializeState() {
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
