package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
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

	private static final float HEADER_HEIGHT = 34f;
	private static final float ROW_HEIGHT = 38f;
	private static final float FIELD_HEIGHT = 28f;
	private static final float COLUMN_GAP = 8f;
	private static final float SIDE_MARGIN = 12f;
	private static final float SCROLLBAR_WIDTH = 16f;
	private static final float[] COLUMN_SHARES = {0.02f, 0.29f, 0.29f, 0.05f, 0.08f, 0.1f, 0.07f, 0.1f};

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
				.splitVertically(HEADER_HEIGHT);
		float tableWidth = table[0].getWidth() - SIDE_MARGIN * 2f - SCROLLBAR_WIDTH;
		rowsArea = new UIRect(SIDE_MARGIN - 5f, table[1].getY(),
				tableWidth + SCROLLBAR_WIDTH, table[1].getHeight());

		UIRect tableHeader = new UIRect(SIDE_MARGIN, table[0].getY(), tableWidth, HEADER_HEIGHT);
		TooltipMakerAPI headerBackground = panel.createUIElement(tableWidth, HEADER_HEIGHT, false);
		LabelAPI headerStrip = headerBackground.addSectionHeading("", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID, tableWidth, 0f);
		headerStrip.getPosition().inTL(0f, 0f);
		headerBackground.setHeightSoFar(HEADER_HEIGHT);
		panel.addUIElement(headerBackground).inTL(tableHeader.getX(), tableHeader.getY());
		float stripHeight = Math.min(HEADER_HEIGHT, headerStrip.getPosition().getHeight());
		UIRect[] headings = columns(tableHeader.getWidth(), stripHeight);
		String[] labels = {"#", text("battle_player_short"), text("battle_enemy_short"), text("battle_map_short"),
				text("battle_map_scale_short"), text("status"), text("enabled"), text("actions")};
		for (int index = 0; index < headings.length; index++) {
			UIRect cell = headings[index];
			addLabel(panel, new UIRect(tableHeader.getX() + cell.getX(), tableHeader.getY(),
					cell.getWidth(), cell.getHeight()), labels[index], BattleNext_UiTheme.TEXT, true);
		}

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		float buttonWidth = Math.min(150f, (footerRect.getWidth() - 20f) / 3f);
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX(), footerRect.getY(), buttonWidth, 30f),
				text("add"), "add", CutStyle.ALL, true);
		ButtonAPI saveButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + buttonWidth + 10f, footerRect.getY(), buttonWidth, 30f),
				text("save"), "save", CutStyle.ALL, true);
		saveButton.setEnabled(fleetStore.isLoaded());
		BattleNext_PageLayout.fixedButton(panel,
				new UIRect(footerRect.getX() + 2f * (buttonWidth + 10f), footerRect.getY(), buttonWidth, 30f),
				text("back"), "back", CutStyle.ALL, true);
		float statusX = footerRect.getX() + 3f * buttonWidth + 30f;
		float statusWidth = footerRect.getWidth() - (statusX - footerRect.getX());
		statusContent = panel.createUIElement(statusWidth - 10f, 30f, false);
		status = statusContent.addPara(statusContent.shortenString(
				loadError.isEmpty() ? text("ready") : loadError, statusWidth - 15f),
				0f, BattleNext_UiTheme.MUTED);
		status.getPosition().inTL(0f, 5f);
		statusContent.setHeightSoFar(30f);
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
		float visibleHeight = Math.min(rowsArea.getHeight(), entries.size() * ROW_HEIGHT + 10f);
		rowsRect = new UIRect(rowsArea.getX(), rowsArea.getY(), rowsArea.getWidth(), visibleHeight);
		CustomPanelAPI contentPanel = rowsRect.createSubPanel(panel, inputOwner).getPanel();
		TooltipMakerAPI content = contentPanel.createUIElement(rowsRect.getWidth() - 10f, visibleHeight, true);
		for (int index = 0; index < entries.size(); index++) {
			addRow(index, entries.get(index), content);
		}
		contentPanel.addUIElement(content).inTL(0f, 0f);

		refreshRowStatuses();
	}

	private void addRow(int index, BattleNext_QuickBattleModel entry, TooltipMakerAPI content) {

		float width = rowsRect.getWidth() - SCROLLBAR_WIDTH;
		CustomPanelAPI card = Global.getSettings().createCustom(width, ROW_HEIGHT, inputOwner);
		new UIRect(0f, 0f, width, ROW_HEIGHT).addBoarder(card, BattleNext_UiTheme.HEADER, 2f);
		UIRect[] cells = columns(width, ROW_HEIGHT);
		QuickBattleEditorRow row = new QuickBattleEditorRow();
		row.id = entry.getId();
		row.model = entry;
		addLabel(card, cells[0], Integer.toString(index + 1), BattleNext_UiTheme.TEXT, true);
		if (entry.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			float rawWidth = cells[4].getX() + cells[4].getWidth() - cells[1].getX();
			row.raw = addField(new UIRect(cells[1].getX(), 0f, rawWidth, ROW_HEIGHT), card,
					entry.getRawContent());
		} else {
			row.player = addField(cells[1], card, entry.getPlayer());
			row.enemy = addField(cells[2], card, entry.getEnemy());
			row.map = addField(cells[3], card, Integer.toString(entry.getMap()));
			row.mapScale = addField(cells[4], card, Float.toString(entry.getMapScale()));
		}

		UIRect statusRect = cells[5];
		TooltipMakerAPI state = card.createUIElement(statusRect.getWidth() - 4f, ROW_HEIGHT, false);
		state.setParaSmallInsignia();
		row.statusContent = state;
		row.status = state.addPara(text("unavailable"), 0f, BattleNext_UiTheme.MUTED);
		row.status.getPosition().inTL(3f, Math.max(0f, (ROW_HEIGHT - row.status.getPosition().getHeight()) * 0.5f));
		state.addTooltipTo(new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 260f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				List<String> errors = validationErrors(row);
				if (errors.isEmpty()) {
					tooltip.addPara(text("available"), 0f, BattleNext_UiTheme.TEXT);
				} else {
					for (String error : errors) {
						tooltip.addPara(error, 4f, BattleNext_UiTheme.MUTED);
					}
				}
			}
		}, state.getPrev(), TooltipLocation.LEFT, true);
		state.setHeightSoFar(ROW_HEIGHT);
		card.addUIElement(state).inTL(statusRect.getX(), statusRect.getY());

		UIRect enabledRect = cells[6];
		TooltipMakerAPI enabledHost = card.createUIElement(enabledRect.getWidth(), ROW_HEIGHT, false);
		row.enabled = enabledHost.addCheckbox(enabledRect.getWidth(), ROW_HEIGHT, "",
				"enabled:" + index, ButtonAPI.UICheckboxSize.SMALL, 0f);
		row.enabled.setChecked(entry.isEnabled());
		row.enabled.setEnabled(entry.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW);
		enabledHost.setHeightSoFar(ROW_HEIGHT);
		card.addUIElement(enabledHost).inTL(enabledRect.getX(), enabledRect.getY());

		UIRect actionsRect = cells[7];
		float buttonWidth = Math.min(120f, actionsRect.getWidth() - 8f);
		BattleNext_PageLayout.fixedButton(card,
				new UIRect(actionsRect.getX() + (actionsRect.getWidth() - buttonWidth) * 0.5f,
						(ROW_HEIGHT - 28f) * 0.5f, buttonWidth, 28f),
				text("delete"), "delete:" + index, CutStyle.ALL, true);

		content.addCustom(card, 0f);
		rows.add(row);
	}

	private TextFieldAPI addField(UIRect rect, CustomPanelAPI card, String value) {
		float width = rect.getWidth() - 10f;
		TooltipMakerAPI maker = card.createUIElement(width, FIELD_HEIGHT, false);
		TextFieldAPI field = BattleNext_TextField.addTo(maker, card, width, FIELD_HEIGHT, value);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		maker.setHeightSoFar(FIELD_HEIGHT);
		card.addUIElement(maker).inTL(rect.getX() + 3f, rect.getY() + (rect.getHeight() - FIELD_HEIGHT) * 0.5f);
		return field;
	}

	private UIRect[] columns(float width, float height) {
		float available = width - COLUMN_GAP * (COLUMN_SHARES.length - 1);
		UIRect[] result = new UIRect[COLUMN_SHARES.length];
		float x = 0f;
		for (int index = 0; index < COLUMN_SHARES.length; index++) {
			float nextX = index == COLUMN_SHARES.length - 1 ? width : x + available * COLUMN_SHARES[index];
			result[index] = new UIRect(x, 0f, nextX - x, height);
			x = nextX + COLUMN_GAP;
		}
		return result;
	}

	private void addLabel(CustomPanelAPI host, UIRect rect, String value, Color color,
			boolean compactFont) {
		TooltipMakerAPI maker = host.createUIElement(rect.getWidth() - 4f, rect.getHeight(), false);
		if (compactFont) maker.setParaSmallInsignia();
		LabelAPI label = maker.addPara(maker.shortenString(value, rect.getWidth() - 10f), 0f, color);
		label.getPosition().inTL(3f, Math.max(0f, (rect.getHeight() - label.getPosition().getHeight()) * 0.5f));
		maker.setHeightSoFar(rect.getHeight());
		host.addUIElement(maker).inTL(rect.getX(), rect.getY());
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
			if (map < -1 || map > 11) errors.add(text("invalid_map"));
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
						|| map < -1 || map > 11 || !Float.isFinite(mapScale) || mapScale <= 0f) {
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
			status.setText(statusContent.shortenString(value, statusContent.getPosition().getWidth() - 10f));
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
