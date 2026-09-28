package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import data.missions.BattleNext_BattleMap;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_TextField;
import org.json.JSONException;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_QuickBattleEditor implements BattleNext_EditorView {

	private static final float[] COLUMN_SHARES = {0.025f, 0.29f, 0.29f, 0.05f, 0.08f, 0.095f, 0.07f, 0.1f};
	private static final int ROW_NUMBER_COLUMN = 0;
	private static final int PLAYER_COLUMN = 1;
	private static final int ENEMY_COLUMN = 2;
	private static final int MAP_COLUMN = 3;
	private static final int MAP_SCALE_COLUMN = 4;
	private static final int STATUS_COLUMN = 5;
	private static final int ENABLED_COLUMN = 6;
	private static final int ACTIONS_COLUMN = 7;

	private static final float ACTION_BUTTON_MAX_WIDTH = 120f;
	private static final float ACTION_BUTTON_SIDE_PADDING = 8f;

	private static final int FOOTER_BUTTON_COUNT = 3;
	private static final float FOOTER_BUTTON_GAP = 10f;
	private static final float FOOTER_BUTTON_MAX_WIDTH = 150f;

	private static final float STATUS_CONTENT_WIDTH_RESERVE = 10f;
	private static final float STATUS_INITIAL_TEXT_RESERVE = 15f;
	private static final float STATUS_TOP_INSET = 5f;
	private static final float STATUS_TOOLTIP_WIDTH = 260f;
	private static final float STATUS_TOOLTIP_LINE_GAP = 4f;

	private static final int RANDOM_MAP_ID = -1;
	private static final int LAST_MAP_ID = BattleNext_BattleMap.MAP_COUNT - 1;

	private final Runnable backAction;
	private final CustomUIPanelPlugin inputOwner;
	private final BattleNext_QuickBattleStore store = new BattleNext_QuickBattleStore();
	private final BattleNext_FleetStore fleetStore = new BattleNext_FleetStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final List<BattleNext_QuickBattleModel> entries = new ArrayList<>();
	private final List<QuickBattleEditorRow> rows = new ArrayList<>();
	private CustomPanelAPI panel;
	private UIRect rowsArea;
	private UIRect rowsRect;
	private LabelAPI status;
	private TooltipMakerAPI statusContent;
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
		if (!fleetStore.isLoaded()) {
			setStatus(text("load_failed") + ": " + fleetStore.getLoadError());
			return false;
		}
		if (!store.isLoaded()) {
			setStatus(text("load_failed") + ": " + store.getLoadError());
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
			setStatus(text("save_failed") + ": " + exception.getMessage());
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

	private void load() {
		loadError = "";
		try {
			fleetStore.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			loadError = text("load_failed") + ": " + exception.getMessage();
		}
		try {
			store.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			loadError = text("load_failed") + ": " + exception.getMessage();
		}
		entries.clear();
		entries.addAll(store.getAll());
		loaded = true;
	}

	@Override
	public void createContents() {

		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] bottom = root.splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		UIRect[] table = bottom[0].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f)
				.splitVertically(BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		float tableWidth = table[0].getWidth() - BattleNext_PageLayout.TABLE_SIDE_MARGIN * 2f;
		rowsArea = new UIRect(BattleNext_PageLayout.TABLE_SIDE_MARGIN
				- BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET, table[1].getY(),
				tableWidth + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, table[1].getHeight());

		UIRect tableHeader = new UIRect(BattleNext_PageLayout.TABLE_SIDE_MARGIN,
				table[0].getY(), tableWidth, BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		TooltipMakerAPI headerBackground = panel.createUIElement(tableWidth,
				BattleNext_PageLayout.TABLE_HEADER_HEIGHT, false);
		LabelAPI headerStrip = headerBackground.addSectionHeading("", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID,
				tableWidth - BattleNext_PageLayout.SECTION_HEADING_EXTRA_WIDTH, 0f);
		headerStrip.getPosition().inTL(0f, 0f);
		headerBackground.setHeightSoFar(BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		panel.addUIElement(headerBackground).inTL(tableHeader.getX(), tableHeader.getY());
		float stripHeight = Math.min(BattleNext_PageLayout.TABLE_HEADER_HEIGHT,
				headerStrip.getPosition().getHeight());
		UIRect[] headings = columns(tableHeader.getWidth(), stripHeight);
		String[] labels = {"#", text("battle_player_short"), text("battle_enemy_short"), text("battle_map_short"),
				text("battle_map_scale_short"), text("status"), text("enabled"), text("actions")};
		for (int index = 0; index < headings.length; index++) {
			UIRect cell = headings[index];
			addLabel(panel, new UIRect(tableHeader.getX() + cell.getX(), tableHeader.getY(),
					cell.getWidth(), cell.getHeight()), labels[index], BattleNext_UiTheme.TEXT, true);
		}

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		float buttonWidth = Math.min(FOOTER_BUTTON_MAX_WIDTH,
				(footerRect.getWidth() - FOOTER_BUTTON_GAP * (FOOTER_BUTTON_COUNT - 1)) / FOOTER_BUTTON_COUNT);
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX(), footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				text("add"), "add", CutStyle.ALL, true);
		ButtonAPI saveButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + buttonWidth + FOOTER_BUTTON_GAP,
						footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				text("save"), "save", CutStyle.ALL, true);
		saveButton.setEnabled(fleetStore.isLoaded());
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + 2f * (buttonWidth + FOOTER_BUTTON_GAP),
						footerRect.getY(), buttonWidth, BattleNext_PageLayout.FOOTER_HEIGHT),
				text("back"), "back", CutStyle.ALL, true);
		float statusX = footerRect.getX() + FOOTER_BUTTON_COUNT * (buttonWidth + FOOTER_BUTTON_GAP);
		float statusWidth = footerRect.getWidth() - (statusX - footerRect.getX());
		statusContent = panel.createUIElement(statusWidth - STATUS_CONTENT_WIDTH_RESERVE,
				BattleNext_PageLayout.FOOTER_HEIGHT, false);
		status = statusContent.addPara(statusContent.shortenString(
				loadError.isEmpty() ? text("ready") : loadError, statusWidth - STATUS_INITIAL_TEXT_RESERVE),
				0f, BattleNext_UiTheme.MUTED);
		status.getPosition().inTL(0f, STATUS_TOP_INSET);
		statusContent.setHeightSoFar(BattleNext_PageLayout.FOOTER_HEIGHT);
		panel.addUIElement(statusContent).inTL(statusX, footerRect.getY());

		refreshContent();
	}

	@Override
	public void refreshContent() {
		if (panel == null) return;
		BattleNext_TextField.clearRegisteredFields();
		if (rowsRect != null) rowsRect.removeLatestSubPanel();
		rows.clear();

		if (entries.isEmpty()) return;
		float visibleHeight = Math.min(rowsArea.getHeight(),
				entries.size() * BattleNext_PageLayout.TABLE_ROW_HEIGHT
						+ BattleNext_PageLayout.TABLE_ROW_BOTTOM_PADDING);
		rowsRect = new UIRect(rowsArea.getX(), rowsArea.getY(), rowsArea.getWidth(), visibleHeight);
		CustomPanelAPI contentPanel = rowsRect.createSubPanel(panel, inputOwner).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(rowsRect.getWidth(), visibleHeight, true);
		for (int index = 0; index < entries.size(); index++) {
			addRow(index, entries.get(index), content);
		}
		contentPanel.addUIElement(content).inTL(0f, 0f);

		refreshRowStatuses();
	}

	private void addRow(int index, BattleNext_QuickBattleModel entry, TooltipMakerAPI content) {

		float width = rowsRect.getWidth() - BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH;
		CustomPanelAPI card = Global.getSettings().createCustom(width, BattleNext_PageLayout.TABLE_ROW_HEIGHT, inputOwner);
		new UIRect(0f, 0f, width, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBoarder(card,
				BattleNext_UiTheme.HEADER, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		UIRect[] cells = columns(width, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		QuickBattleEditorRow row = new QuickBattleEditorRow();
		row.id = entry.getId();
		row.model = entry;
		addLabel(card, cells[ROW_NUMBER_COLUMN], Integer.toString(index + 1), BattleNext_UiTheme.TEXT, true);
		if (entry.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			float rawWidth = cells[MAP_SCALE_COLUMN].getX() + cells[MAP_SCALE_COLUMN].getWidth()
					- cells[PLAYER_COLUMN].getX();
			row.raw = addField(new UIRect(cells[PLAYER_COLUMN].getX(), 0f, rawWidth,
					BattleNext_PageLayout.TABLE_ROW_HEIGHT), card,
					entry.getRawContent(), content);
		} else {
			row.player = addField(cells[PLAYER_COLUMN], card, entry.getPlayer(), content);
			row.enemy = addField(cells[ENEMY_COLUMN], card, entry.getEnemy(), content);
			row.map = addField(cells[MAP_COLUMN], card, Integer.toString(entry.getMap()), content);
			row.mapScale = addField(cells[MAP_SCALE_COLUMN], card, Float.toString(entry.getMapScale()), content);
		}

		UIRect statusRect = cells[STATUS_COLUMN];
		float statusLabelWidth = statusRect.getWidth() - BattleNext_PageLayout.CELL_LABEL_SIDE_INSET * 2f;
		TooltipMakerAPI state = card.createUIElement(statusLabelWidth,
				BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
		state.setParaSmallInsignia();
		row.statusContent = state;
		row.status = state.addPara(text("unavailable"), 0f, BattleNext_UiTheme.MUTED);
		row.status.setAlignment(Alignment.MID);
		row.status.getPosition().setSize(statusLabelWidth, row.status.getPosition().getHeight());
		row.status.getPosition().inTL(0f, Math.max(0f,
				(BattleNext_PageLayout.TABLE_ROW_HEIGHT - row.status.getPosition().getHeight()) * 0.5f));
		state.addTooltipTo(new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return STATUS_TOOLTIP_WIDTH;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				List<String> errors = validationErrors(row);
				if (errors.isEmpty()) {
					tooltip.addPara(text("available"), 0f, BattleNext_UiTheme.TEXT);
				} else {
					for (String error : errors) {
						tooltip.addPara(error, STATUS_TOOLTIP_LINE_GAP, BattleNext_UiTheme.MUTED);
					}
				}
			}
		}, state.getPrev(), TooltipLocation.LEFT, true);
		state.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		card.addUIElement(state).inTL(statusRect.getX() + BattleNext_PageLayout.CELL_LABEL_SIDE_INSET,
				statusRect.getY());

		UIRect enabledRect = cells[ENABLED_COLUMN];
		float checkboxWidth = Math.min(BattleNext_PageLayout.TABLE_CHECKBOX_MAX_WIDTH, enabledRect.getWidth());
		TooltipMakerAPI enabledHost = card.createUIElement(checkboxWidth,
				BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
		row.enabled = enabledHost.addCheckbox(checkboxWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, "",
				"enabled:" + index, ButtonAPI.UICheckboxSize.SMALL, 0f);
		row.enabled.setChecked(entry.isEnabled());
		row.enabled.setEnabled(entry.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW);
		enabledHost.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		card.addUIElement(enabledHost).inTL(enabledRect.getX() + (enabledRect.getWidth() - checkboxWidth) * 0.5f,
				enabledRect.getY());

		UIRect actionsRect = cells[ACTIONS_COLUMN];
		float buttonWidth = Math.min(ACTION_BUTTON_MAX_WIDTH,
				actionsRect.getWidth() - ACTION_BUTTON_SIDE_PADDING);
		BattleNext_PageLayout.fixedButton(card,
				new UIRect(actionsRect.getX() + (actionsRect.getWidth() - buttonWidth) * 0.5f,
						(BattleNext_PageLayout.TABLE_ROW_HEIGHT - BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT) * 0.5f,
						buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT),
				text("delete"), "delete:" + index, CutStyle.ALL, true);

		content.addCustom(card, 0f);
		rows.add(row);
	}

	private TextFieldAPI addField(UIRect rect, CustomPanelAPI card, String value, TooltipMakerAPI... viewports) {
		float width = rect.getWidth() - BattleNext_PageLayout.TABLE_FIELD_LEFT_INSET
				- BattleNext_PageLayout.TABLE_FIELD_RIGHT_INSET;
		TooltipMakerAPI maker = card.createUIElement(width, BattleNext_PageLayout.FORM_FIELD_HEIGHT, false);
		TextFieldAPI field = BattleNext_TextField.addTo(maker, card, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, value, viewports);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		maker.setHeightSoFar(BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		card.addUIElement(maker).inTL(rect.getX() + BattleNext_PageLayout.TABLE_FIELD_LEFT_INSET,
				rect.getY() + (rect.getHeight() - BattleNext_PageLayout.FORM_FIELD_HEIGHT) * 0.5f);
		return field;
	}

	private UIRect[] columns(float width, float height) {
		float available = width - BattleNext_PageLayout.TABLE_COLUMN_GAP * (COLUMN_SHARES.length - 1);
		UIRect[] result = new UIRect[COLUMN_SHARES.length];
		float x = 0f;
		for (int index = 0; index < COLUMN_SHARES.length; index++) {
			float nextX = index == COLUMN_SHARES.length - 1 ? width : x + available * COLUMN_SHARES[index];
			result[index] = new UIRect(x, 0f, nextX - x, height);
			x = nextX + BattleNext_PageLayout.TABLE_COLUMN_GAP;
		}
		return result;
	}

	private void addLabel(CustomPanelAPI host, UIRect rect, String value, Color color,
			boolean compactFont) {
		float labelWidth = rect.getWidth() - BattleNext_PageLayout.CELL_LABEL_SIDE_INSET * 2f;
		TooltipMakerAPI maker = host.createUIElement(labelWidth, rect.getHeight(), false);
		if (compactFont) maker.setParaSmallInsignia();
		LabelAPI label = maker.addPara(maker.shortenString(value,
				rect.getWidth() - BattleNext_PageLayout.CELL_LABEL_TRUNCATION_PADDING), 0f, color);
		label.setAlignment(Alignment.MID);
		label.getPosition().setSize(labelWidth, label.getPosition().getHeight());
		label.getPosition().inTL(0f, Math.max(0f, (rect.getHeight() - label.getPosition().getHeight()) * 0.5f));
		maker.setHeightSoFar(rect.getHeight());
		host.addUIElement(maker).inTL(rect.getX() + BattleNext_PageLayout.CELL_LABEL_SIDE_INSET,
				rect.getY());
	}

	public void refreshRowStatuses() {
		if (promoteEditedRawRows()) {
			return;
		}
		for (QuickBattleEditorRow row : rows) {
			if (row.model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				row.status.setText(text("raw_row"));
				row.status.setColor(BattleNext_UiTheme.MUTED);
				continue;
			}
			boolean available = validationErrors(row).isEmpty();
			String label = text(available ? "available" : "unavailable");
			if (!label.equals(row.status.getText())) {
				row.status.setText(label);
				row.status.setColor(available ? BattleNext_UiTheme.ACCENT : BattleNext_UiTheme.MUTED);
			}
		}
	}

	private boolean promoteEditedRawRows() {
		for (QuickBattleEditorRow row : rows) {
			if (row.model.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW || row.raw.hasFocus()) {
				continue;
			}
			BattleNext_QuickBattleModel parsed = BattleNext_QuickBattleModel.parseLine(
					row.raw.getText(), row.model.usesLegacyIds());
			if (parsed != null && parsed.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				row.model.promoteRaw(row.raw.getText());
				refreshContent();
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
		if (fleetStore.get(row.player.getText().trim()) == null) errors.add(text("invalid_player"));
		if (fleetStore.get(row.enemy.getText().trim()) == null) errors.add(text("invalid_enemy"));
		try {
			int map = Integer.parseInt(row.map.getText().trim());
			if (map < RANDOM_MAP_ID || map > LAST_MAP_ID) errors.add(text("invalid_map"));
		} catch (NumberFormatException exception) {
			errors.add(text("invalid_map"));
		}
		try {
			float mapScale = Float.parseFloat(row.mapScale.getText().trim());
			if (!Float.isFinite(mapScale) || mapScale <= 0f) errors.add(text("invalid_map_scale"));
		} catch (NumberFormatException exception) {
			errors.add(text("invalid_map_scale"));
		}
		return errors;
	}

	private void removeRow(int index) {
		if (index < 0 || index >= rows.size() || !captureFields(index)) return;
		refreshContent();
	}

	private boolean captureFields() {
		return captureFields(-1);
	}

	private boolean captureFields(int skipIndex) {
		for (int index = 0; index < rows.size(); index++) {
			if (index == skipIndex) {
				continue;
			}
			QuickBattleEditorRow row = rows.get(index);
			BattleNext_QuickBattleModel model = row.model;
			if (model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				model.promoteRaw(row.raw.getText());
				continue;
			}
			try {
				int map = Integer.parseInt(row.map.getText().trim());
				float mapScale = Float.parseFloat(row.mapScale.getText().trim());
				if (row.player.getText().trim().isEmpty() || row.enemy.getText().trim().isEmpty()
						|| map < RANDOM_MAP_ID || map > LAST_MAP_ID || !Float.isFinite(mapScale) || mapScale <= 0f) {
					setStatus(text("invalid_map_scale_combined"));
					return false;
				}
				model.setPlayer(row.player.getText());
				model.setEnemy(row.enemy.getText());
				model.setMap(map);
				model.setMapScale(mapScale);
				model.setEnabled(row.enabled.isChecked());
				model.setRowType(model.isEnabled() ? BattleNext_QuickBattleModel.QuickBattleRowType.BATTLE
						: model.getRowType());
			} catch (NumberFormatException exception) {
				setStatus(text("invalid_map_scale_combined"));
				return false;
			}
		}
		if (skipIndex >= 0) {
			entries.remove(skipIndex);
		}
		return true;
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

	@Override
	public String serializeState() {
		StringBuilder value = new StringBuilder();
		for (QuickBattleEditorRow row : rows) {
			value.append(row.model.getRowType()).append(row.enabled != null && row.enabled.isChecked());
			for (TextFieldAPI field : new TextFieldAPI[]{row.player, row.enemy, row.map, row.mapScale, row.raw}) {
				if (field != null) {
					value.append(field.getText().length()).append(':').append(field.getText());
				}
			}
		}
		return value.toString();
	}

	private void setStatus(String value) {
		statusText = value;
		if (status != null) {
			status.setText(statusContent.shortenString(value,
					statusContent.getPosition().getWidth() - STATUS_CONTENT_WIDTH_RESERVE));
		}
	}

	private String text(String key) {
		return strings.get(key);
	}

	private static final class QuickBattleEditorRow {

		private String id = "";
		private BattleNext_QuickBattleModel model;
		private TextFieldAPI player;
		private TextFieldAPI enemy;
		private TextFieldAPI map;
		private TextFieldAPI mapScale;
		private TextFieldAPI raw;
		private ButtonAPI enabled;
		private LabelAPI status;
		private TooltipMakerAPI statusContent;

		private QuickBattleEditorRow() {
		}
	}
}
