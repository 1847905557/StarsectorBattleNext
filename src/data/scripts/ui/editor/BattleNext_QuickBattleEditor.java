package data.scripts.ui.editor;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TableWidget;
import org.json.JSONException;

import java.io.IOException;
import java.util.*;

public class BattleNext_QuickBattleEditor extends BattleNext_EditorView {

	private static final float[] COLUMN_SHARES = {0.025f, 0.29f, 0.29f, 0.05f, 0.08f, 0.095f, 0.07f, 0.1f};
	private static final int ROW_NUMBER_COLUMN = 0;
	private static final int PLAYER_COLUMN = 1;
	private static final int ENEMY_COLUMN = 2;
	private static final int MAP_COLUMN = 3;
	private static final int MAP_SCALE_COLUMN = 4;
	private static final int STATUS_COLUMN = 5;
	private static final int ENABLED_COLUMN = 6;
	private static final int ACTIONS_COLUMN = 7;

	private static final int FOOTER_BUTTON_COUNT = 3;
	private static final float FOOTER_BUTTON_GAP = 10f;
	private static final float FOOTER_BUTTON_MAX_WIDTH = 150f;

	private static final float STATUS_CONTENT_WIDTH_RESERVE = 10f;
	private static final float STATUS_INITIAL_TEXT_RESERVE = 15f;
	private static final float STATUS_TOP_INSET = 5f;
	private static final float STATUS_TOOLTIP_WIDTH = 260f;
	private static final float STATUS_TOOLTIP_LINE_GAP = 4f;

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final Map<String, List<String>> fleetErrors = new LinkedHashMap<>();
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final List<BattleNext_QuickBattleModel> entries = new ArrayList<>();
	private final List<QuickBattleEditorRow> rows = new ArrayList<>();
	private final Map<BattleNext_QuickBattleModel, Object> rowKeys = new IdentityHashMap<>();
	private Map<BattleNext_QuickBattleModel, RowInput> pendingInputs = new IdentityHashMap<>();
	private BattleNext_TableWidget tableWidget;
	private LabelAPI statusLabel;
	private TooltipMakerAPI statusContent;
	private String loadError = "";
	private boolean loaded;

	private static final class QuickBattleEditorRow {

		private String id = "";
		private BattleNext_QuickBattleModel model;
		private TextFieldAPI playerField;
		private TextFieldAPI enemyField;
		private TextFieldAPI mapField;
		private TextFieldAPI mapScaleField;
		private TextFieldAPI rawField;
		private ButtonAPI enabledCheckbox;
		private LabelAPI statusLabel;

		private QuickBattleEditorRow() {
		}
	}

	public BattleNext_QuickBattleEditor(Runnable backAction, CustomUIPanelPlugin inputOwner) {
		this.backAction = backAction;
		this.inputOwner = inputOwner;
	}

	@Override
	protected void buildEditorContent() {

		if (!loaded) load();
		Map<BattleNext_QuickBattleModel, RowInput> inputs = pendingInputs;
		rows.clear();
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] bottom = root.splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		UIRect[] table = bottom[0].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f)
				.splitVertically(BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		String[] labels = {"#", battleStrings.get("player_fleet_id_label"), battleStrings.get("enemy_fleet_id_label"), battleStrings.get("map_label"),
				battleStrings.get("map_scale_label"), strings.get("status"), strings.get("enabled"), strings.get("actions")};
		UIRect tableRect = new UIRect(0f, table[0].getY(), table[0].getWidth(),
				table[0].getHeight() + table[1].getHeight());
		CustomPanelAPI tablePanel = tableRect.createSubPanel(panel, inputOwner).getPanel();
		tableWidget = own(new BattleNext_TableWidget(inputOwner, labels, COLUMN_SHARES,
				entries.size(), this::addRow));
		tableWidget.attach(tablePanel);

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		float buttonWidth = Math.min(FOOTER_BUTTON_MAX_WIDTH,
				(footerRect.getWidth() - FOOTER_BUTTON_GAP * (FOOTER_BUTTON_COUNT - 1)) / FOOTER_BUTTON_COUNT);
		createFixedButton(panel,
				new UIRect(footerRect.getX(), footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("add"), "add", true, true);
		createFixedButton(panel,
				new UIRect(footerRect.getX() + buttonWidth + FOOTER_BUTTON_GAP,
						footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("save"), "save", true, fleetStore.isLoaded());
		createFixedButton(panel,
				new UIRect(footerRect.getX() + 2f * (buttonWidth + FOOTER_BUTTON_GAP),
						footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				strings.get("back"), "back", true, true);
		float statusX = footerRect.getX() + FOOTER_BUTTON_COUNT * (buttonWidth + FOOTER_BUTTON_GAP);
		float statusWidth = footerRect.getWidth() - (statusX - footerRect.getX());
		statusContent = panel.createUIElement(statusWidth - STATUS_CONTENT_WIDTH_RESERVE,
				BattleNext_PageLayout.FOOTER_HEIGHT, false);
		statusLabel = statusContent.addPara(statusContent.shortenString(
				loadError.isEmpty() ? strings.get("ready") : loadError, statusWidth - STATUS_INITIAL_TEXT_RESERVE),
				0f);
		statusLabel.getPosition().inTL(0f, STATUS_TOP_INSET);
		statusContent.setHeightSoFar(BattleNext_PageLayout.FOOTER_HEIGHT);
		panel.addUIElement(statusContent).inTL(statusX, footerRect.getY());

		for (QuickBattleEditorRow row : rows) {
			RowInput input = inputs.get(row.model);
			if (input == null || input.raw() == (row.rawField == null)) continue;
			TextFieldAPI[] fields = input.raw() ? new TextFieldAPI[]{row.rawField} : new TextFieldAPI[]{row.playerField, row.enemyField, row.mapField, row.mapScaleField};
			for (int index = 0; index < fields.length; index++) fields[index].setText(input.values().get(index));
			row.enabledCheckbox.setChecked(input.enabled());
		}
		bindViewport("rows", tableWidget.getContent());
	}

	@Override
	protected void afterRefresh() {
		refreshRowStatuses();
	}

	private void load() {
		loadError = "";
		fleetErrors.clear();
		try {
			fleetStore.reload();
			BattleNext_FleetBuilder builder = new BattleNext_FleetBuilder();
			for (var fleet : fleetStore.getAll()) {
				List<String> errors = new ArrayList<>();
				try {
					for (String error : builder.build(fleet).getMemberErrors()) {
						errors.add(fleet.getId() + " " + error);
					}
				} catch (RuntimeException exception) {
					errors.add(fleet.getId() + ": " + exception.getMessage());
				}
				fleetErrors.put(fleet.getId(), List.copyOf(errors));
			}
		} catch (IOException | JSONException | RuntimeException exception) {
			loadError = strings.get("load_failed") + ": " + exception.getMessage();
		}
		try {
			store.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			loadError = strings.get("load_failed") + ": " + exception.getMessage();
		}
		entries.clear();
		entries.addAll(store.getAll());
		loaded = true;
		rowKeys.clear();
	}

	private Map<BattleNext_QuickBattleModel, RowInput> captureRowInputs() {
		Map<BattleNext_QuickBattleModel, RowInput> inputs = new IdentityHashMap<>();
		for (QuickBattleEditorRow row : rows) {
			boolean raw = row.rawField != null;
			List<String> values = raw ? List.of(row.rawField.getText())
					: List.of(row.playerField.getText(), row.enemyField.getText(), row.mapField.getText(), row.mapScaleField.getText());
			inputs.put(row.model, new RowInput(raw, values, row.enabledCheckbox.isChecked()));
		}
		return inputs;
	}

	private void rebuildContent(Map<BattleNext_QuickBattleModel, RowInput> inputs) {
		pendingInputs = inputs;
		refreshContent();
	}

	private void addRow(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI rowsContent) {
		BattleNext_QuickBattleModel entry = entries.get(index);
		CustomPanelAPI rowPanel = panel.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, inputOwner);
		new UIRect(0f, 0f, rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBorder(rowPanel,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		QuickBattleEditorRow row = new QuickBattleEditorRow();
		row.id = entry.getId();
		row.model = entry;
		Object rowKey = rowKeys.computeIfAbsent(entry, ignored -> new Object());
		createTextCell(rowPanel, cells[ROW_NUMBER_COLUMN], Integer.toString(index + 1), true, null, TooltipLocation.RIGHT, false);
		if (entry.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			float cellWidth = cells[MAP_SCALE_COLUMN].getX() + cells[MAP_SCALE_COLUMN].getWidth() - cells[PLAYER_COLUMN].getX();
			UIRect rawCell = new UIRect(cells[PLAYER_COLUMN].getX(), 0f, cellWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
			row.rawField = createCellField(List.of(rowKey, "raw"), rowPanel, rawCell, entry.getRawContent(), rowsContent);
		} else {
			row.playerField = createCellField(List.of(rowKey, "player"), rowPanel, cells[PLAYER_COLUMN], entry.getPlayer(), rowsContent);
			row.enemyField = createCellField(List.of(rowKey, "enemy"), rowPanel, cells[ENEMY_COLUMN], entry.getEnemy(), rowsContent);
			row.mapField = createCellField(List.of(rowKey, "map"), rowPanel, cells[MAP_COLUMN], Integer.toString(entry.getMap()), rowsContent);
			row.mapScaleField = createCellField(List.of(rowKey, "scale"), rowPanel, cells[MAP_SCALE_COLUMN], Float.toString(entry.getMapScale()), rowsContent);
		}
		row.statusLabel = createTextCell(rowPanel, cells[STATUS_COLUMN], strings.get("unavailable"), true, new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return STATUS_TOOLTIP_WIDTH;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				List<String> errors = validationErrors(row);
				if (errors.isEmpty()) {
					tooltip.addPara(strings.get("available"), 0f);
				} else {
					for (String error : errors) {
						tooltip.addPara(error, STATUS_TOOLTIP_LINE_GAP);
					}
				}
			}
		}, TooltipLocation.LEFT, true);
		row.enabledCheckbox = createCellCheckbox(List.of(rowKey, "enabled"), rowPanel, cells[ENABLED_COLUMN],
				"enabled:" + index, entry.isEnabled(), entry.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW, null);
		createActions(rowPanel, cells[ACTIONS_COLUMN], new EditorAction(strings.get("delete"), "delete:" + index, true));
		rowsContent.addCustom(rowPanel, 0f);
		rows.add(row);
	}

	private boolean promoteEditedRawRows() {
		for (QuickBattleEditorRow row : rows) {
			if (row.model.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW || row.rawField.hasFocus()) {
				continue;
			}
			BattleNext_QuickBattleModel parsed = BattleNext_QuickBattleModel.parseLine(
					row.rawField.getText(), row.model.usesLegacyIds());
			if (parsed != null && parsed.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				Map<BattleNext_QuickBattleModel, RowInput> inputs = captureRowInputs();
				row.model.promoteRaw(row.rawField.getText());
				rebuildContent(inputs);
				return true;
			}
		}
		return false;
	}

	private List<String> validationErrors(QuickBattleEditorRow row) {
		List<String> errors = new ArrayList<>();
		if (row.model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			return errors;
		}
		if (!fleetStore.isLoaded()) {
			errors.add(fleetStore.getLoadError());
			return errors;
		}
		String playerId = row.playerField.getText().trim();
		String enemyId = row.enemyField.getText().trim();
		if (fleetStore.get(playerId) == null) {
			errors.add(battleStrings.get("invalid_player_fleet"));
		} else {
			errors.addAll(fleetErrors.get(playerId));
		}
		if (fleetStore.get(enemyId) == null) {
			errors.add(battleStrings.get("invalid_enemy_fleet"));
		} else {
			errors.addAll(fleetErrors.get(enemyId));
		}
		try {
			int map = Integer.parseInt(row.mapField.getText().trim());
			if (map < BattleNext_MissionBattleMap.RANDOM_MAP_ID || map > BattleNext_MissionBattleMap.LAST_MAP_ID) errors.add(battleStrings.get("invalid_map"));
		} catch (NumberFormatException exception) {
			errors.add(battleStrings.get("invalid_map"));
		}
		try {
			float mapScale = Float.parseFloat(row.mapScaleField.getText().trim());
			if (!Float.isFinite(mapScale) || mapScale <= 0f) errors.add(battleStrings.get("invalid_map_scale"));
		} catch (NumberFormatException exception) {
			errors.add(battleStrings.get("invalid_map_scale"));
		}
		return errors;
	}

	private void removeRow(int index) {
		pendingInputs = captureRowInputs();
		entries.remove(index);
		rows.remove(index);
		refreshContent();
	}

	private void deleteRemovedEntries() throws IOException, JSONException {
		List<String> keptIds = new ArrayList<>();
		for (BattleNext_QuickBattleModel entry : entries) {
			keptIds.add(entry.getId());
		}
		for (BattleNext_QuickBattleModel stored : store.getAll()) {
			if (keptIds.contains(stored.getId())) continue;
			store.delete(stored.getId());
		}
	}

	private String mintId() {
		int index = 0;
		while (true) {
			String candidate = "quick-" + index;
			if (store.get(candidate) == null && !containsId(candidate)) return candidate;
			index++;
		}
	}

	private boolean containsId(String id) {
		for (BattleNext_QuickBattleModel entry : entries) {
			if (entry.getId().equals(id)) return true;
		}
		return false;
	}

	private void setStatus(String value) {
		statusText = value;
		if (statusLabel != null) {
			statusLabel.setText(statusContent.shortenString(value,
					statusContent.getPosition().getWidth() - STATUS_CONTENT_WIDTH_RESERVE));
		}
	}

	private record RowInput(boolean raw, List<String> values, boolean enabled) {

	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("add".equals(buttonId)) {
			pendingInputs = captureRowInputs();
			entries.add(new BattleNext_QuickBattleModel("", "", -1, 1f));
			refreshContent();
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("back".equals(buttonId)) {
			backAction.run();
		} else if (buttonId instanceof String && ((String) buttonId).startsWith("delete:")) {
			removeRow(Integer.parseInt(((String) buttonId).substring("delete:".length())));
		}
	}

	public void refreshRowStatuses() {
		if (promoteEditedRawRows()) {
			return;
		}
		for (QuickBattleEditorRow row : rows) {
			if (row.model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				row.statusLabel.setText(battleStrings.get("raw_row_status"));
				row.statusLabel.setColor(BattleNext_UiTheme.PANEL);
				continue;
			}
			boolean available = validationErrors(row).isEmpty();
			String label = strings.get(available ? "available" : "unavailable");
			if (!label.equals(row.statusLabel.getText())) {
				row.statusLabel.setText(label);
				row.statusLabel.setColor(available ? BattleNext_UiTheme.ACCENT : BattleNext_UiTheme.PANEL);
			}
		}
	}

	@Override
	protected String draftState() {
		StringBuilder value = new StringBuilder();
		for (QuickBattleEditorRow row : rows) {
			value.append(row.model.getRowType()).append(row.enabledCheckbox != null && row.enabledCheckbox.isChecked());
			for (TextFieldAPI field : new TextFieldAPI[]{row.playerField, row.enemyField, row.mapField, row.mapScaleField, row.rawField}) {
				if (field != null) {
					value.append(field.getText().length()).append(':').append(field.getText());
				}
			}
		}
		return value.toString();
	}

	@Override
	protected boolean submitDraft() {
		if (!fleetStore.isLoaded()) {
			setStatus(strings.get("load_failed") + ": " + fleetStore.getLoadError());
			return false;
		}
		if (!store.isLoaded()) {
			setStatus(strings.get("load_failed") + ": " + store.getLoadError());
			return false;
		}
		if (!captureFields()) return false;
		try {
			deleteRemovedEntries();
			for (BattleNext_QuickBattleModel entry : entries) {
				if (entry.getId().isEmpty()) entry.setId(mintId());
				store.save(entry);
			}
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.error(BattleNext_QuickBattleEditor.class, "quick_battle.editor.commit.failed",
				BattleNext_Logger.fields().put("entries", entries).put("input_state", serializeState()).build(), exception);
			setStatus(strings.get("save_failed") + ": " + exception.getMessage());
			return false;
		}
		setStatus(strings.get("saved"));
		return true;
	}

	@Override
	protected void captureDraftInputs() {
		pendingInputs = captureRowInputs();
	}

	private boolean captureFields() {
		return captureFields(-1);
	}

	private boolean captureFields(int skipIndex) {

		List<BattleNext_QuickBattleModel> parsed = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			if (index == skipIndex) {
				continue;
			}
			QuickBattleEditorRow row = rows.get(index);
			BattleNext_QuickBattleModel model = row.model.copy();
			if (row.rawField != null) {
				model.promoteRaw(row.rawField.getText());
				parsed.add(model);
				continue;
			}
			try {
				int map = Integer.parseInt(row.mapField.getText().trim());
				float mapScale = Float.parseFloat(row.mapScaleField.getText().trim());
				if (row.playerField.getText().trim().isEmpty() || row.enemyField.getText().trim().isEmpty()
						|| map < BattleNext_MissionBattleMap.RANDOM_MAP_ID || map > BattleNext_MissionBattleMap.LAST_MAP_ID || !Float.isFinite(mapScale) || mapScale <= 0f) {
					BattleNext_Logger.warn(BattleNext_QuickBattleEditor.class, "quick_battle.row.rejected",
				BattleNext_Logger.fields().put("index", index).put("player", row.playerField.getText()).put("enemy", row.enemyField.getText()).put("map", row.mapField.getText()).put("scale", row.mapScaleField.getText()).put("criterion", "Nonblank fleet references, supported map and positive finite scale").build());
					setStatus(battleStrings.get("invalid_map_settings"));
					return false;
				}
				model.setPlayer(row.playerField.getText());
				model.setEnemy(row.enemyField.getText());
				model.setMap(map);
				model.setMapScale(mapScale);
				model.setEnabled(row.enabledCheckbox.isChecked());
				model.setRowType(model.isEnabled() ? BattleNext_QuickBattleModel.QuickBattleRowType.BATTLE
						: model.getRowType());
				parsed.add(model);
			} catch (NumberFormatException exception) {
				BattleNext_Logger.warn(BattleNext_QuickBattleEditor.class, "quick_battle.row.rejected",
				BattleNext_Logger.fields().put("index", index).put("player", row.playerField.getText()).put("enemy", row.enemyField.getText()).put("map", row.mapField.getText()).put("scale", row.mapScaleField.getText()).put("criterion", "Nonblank fleet references, supported map and positive finite scale").build());
					setStatus(battleStrings.get("invalid_map_settings"));
				return false;
			}
		}
		entries.clear();
		entries.addAll(parsed);
		boolean changedShape = false;
		int target = 0;
		for (int index = 0; index < rows.size(); index++) {
			if (index == skipIndex) continue;
			QuickBattleEditorRow row = rows.get(index);
			BattleNext_QuickBattleModel next = parsed.get(target++);
			rowKeys.put(next, rowKeys.get(row.model));
			row.model = next;
			if (row.rawField != null && row.model.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				changedShape = true;
			}
		}
		if (changedShape) rebuildContent(captureRowInputs());
		return true;
	}

	@Override
	protected void restoreDraft() {
		load();
		pendingInputs.clear();
		rows.clear();
	}
}
