package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class BattleNext_QuickBattleDisplay extends BattleNext_PanelBasedWidget {

	public enum Column {
		NUMBER, PLAYER, ENEMY, MAP, SCALE, STATUS, ENABLED, ACTIONS
	}

	public record Action(String label, Object id, boolean enabled) {

	}

	public record Presentation(List<Column> columns, List<Float> columnShares, boolean editable,
			Function<Row, Action> action, Consumer<Object> onAction) {
		public Presentation {
			columns = List.copyOf(columns);
			columnShares = List.copyOf(columnShares);
		}
	}

	public record ViewState(float scroll, Map<Object, BattleNext_TextField.EditState> fields,
			Map<Object, Map<Column, String>> fleets, Map<Object, Boolean> enabled) {
		public ViewState {
			fields = Map.copyOf(fields);
			fleets = Map.copyOf(fleets);
			enabled = Map.copyOf(enabled);
		}
	}

	public static class Row {
		private final int index;
		private final Object key;
		private BattleNext_QuickBattleModel model;
		private final Map<Column, String> fleets = new EnumMap<>(Column.class);
		private final Map<Column, LabelAPI> fleetLabels = new EnumMap<>(Column.class);
		private final Map<Column, BattleNext_OptionField> fleetFields = new EnumMap<>(Column.class);
		private BattleNext_TextField mapField;
		private BattleNext_TextField mapScaleField;
		private BattleNext_TextField rawField;
		private ButtonAPI enabledCheckbox;
		private LabelAPI statusLabel;
		private ButtonAPI actionButton;
		private String mapText;
		private String scaleText;
		private String rawText;
		private boolean enabled;

		private Row(int index, Object key, BattleNext_QuickBattleModel model) {
			this.index = index;
			this.key = key;
			this.model = model;
			fleets.put(Column.PLAYER, model.getPlayer());
			fleets.put(Column.ENEMY, model.getEnemy());
			mapText = Integer.toString(model.getMap());
			scaleText = Float.toString(model.getMapScale());
			rawText = model.getRawContent();
			enabled = model.isEnabled();
		}

		public int getIndex() {
			return index;
		}

		public BattleNext_QuickBattleModel getModel() {
			return model;
		}

		public void setModel(BattleNext_QuickBattleModel model) {
			if (isRaw() && model.getRowType() != BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				fleets.put(Column.PLAYER, model.getPlayer());
				fleets.put(Column.ENEMY, model.getEnemy());
				mapText = Integer.toString(model.getMap());
				scaleText = Float.toString(model.getMapScale());
				setEnabled(model.isEnabled());
			}
			this.model = model;
		}

		public BattleNext_TextField getField(Column column) {
			return switch (column) {
				case MAP -> mapField;
				case SCALE -> mapScaleField;
				default -> throw new IllegalArgumentException("Column does not contain an input: " + column);
			};
		}

		public String getFleetId(Column column) {
			BattleNext_OptionField field = fleetFields.get(column);
			return field == null ? fleets.get(column) : field.getField().getText().trim();
		}

		public BattleNext_TextField getRawField() {
			return rawField;
		}

		public boolean isEnabled() {
			return enabledCheckbox == null ? enabled : enabledCheckbox.isChecked();
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
			if (enabledCheckbox != null) enabledCheckbox.setChecked(enabled);
		}

		public String text(Column column) {
			BattleNext_TextField field = getField(column);
			return field == null ? column == Column.MAP ? mapText : scaleText : field.getText();
		}

		public String rawText() {
			return rawField == null ? rawText : rawField.getText();
		}

		public boolean isRaw() {
			return model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW;
		}

		private boolean hasFocus() {
			return fleetFields.values().stream().anyMatch(field -> field.getField().hasFocus())
					|| mapField != null && mapField.hasFocus() || mapScaleField != null && mapScaleField.hasFocus()
					|| rawField != null && rawField.hasFocus();
		}

	}

	private final List<BattleNext_QuickBattleModel> entries;
	private final Presentation presentation;
	private final Function<BattleNext_QuickBattleModel, Object> identity;
	private final Function<Row, List<String>> validation;
	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_FleetSourceProvider fleetSource;
	private final CustomPanelAPI overlayHost;
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final Map<Object, BattleNext_TextField> fields = new LinkedHashMap<>();
	private final Map<Object, BattleNext_TextField.EditState> savedInputs = new LinkedHashMap<>();
	private final List<Row> rows = new ArrayList<>();
	private BattleNext_TableWidget tableWidget;

	public BattleNext_QuickBattleDisplay(BattleNext_UiInputRouter inputRouter, List<BattleNext_QuickBattleModel> entries,
			Presentation presentation, Function<BattleNext_QuickBattleModel, Object> identity, Function<Row, List<String>> validation,
			BattleNext_FleetStore fleetStore, CustomPanelAPI overlayHost) {
		super(inputRouter);
		this.entries = List.copyOf(entries);
		this.presentation = presentation;
		this.identity = identity;
		this.validation = validation;
		this.fleetStore = fleetStore;
		this.fleetSource = new BattleNext_FleetSourceProvider(fleetStore);
		this.overlayHost = overlayHost;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {

		for (int index = 0; index < entries.size(); index++) rows.add(new Row(index, identity.apply(entries.get(index)), entries.get(index)));

		CustomPanelAPI tablePanel = ownPanel(panel, panel.createCustomPanel(panel.getPosition().getWidth(),
				panel.getPosition().getHeight(), panel.getPlugin()));
		panel.addComponent(tablePanel).inTL(0f, 0f);

		String[] headers = presentation.columns().stream().map(column -> switch (column) {
			case NUMBER -> "#";
			case PLAYER -> battleStrings.get("player_fleet_id_label");
			case ENEMY -> battleStrings.get("enemy_fleet_id_label");
			case MAP -> battleStrings.get("map_label");
			case SCALE -> battleStrings.get("map_scale_label");
			case STATUS -> strings.get("status");
			case ENABLED -> strings.get("enabled");
			case ACTIONS -> strings.get("actions");
		}).toArray(String[]::new);
		double total = presentation.columnShares().stream().mapToDouble(Float::doubleValue).sum();
		float[] shares = new float[headers.length];
		for (int index = 0; index < shares.length; index++) {
			shares[index] = (float) (presentation.columnShares().get(index) / total);
		}

		tableWidget = new BattleNext_TableWidget(panel.getPlugin(), headers, shares, entries.size(), this::addRow,
				this::releaseRow, index -> rows.get(index).hasFocus());
		tableWidget.attach(tablePanel);
		refreshRowStatuses();
	}

	private void addRow(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI content) {

		TooltipMakerAPI viewport = tableWidget.getContent();
		BattleNext_QuickBattleModel entry = entries.get(index);
		Row row = rows.get(index);
		Map<Column, UIRect> columns = new EnumMap<>(Column.class);
		for (int column = 0; column < cells.length; column++) {
			columns.put(presentation.columns().get(column), cells[column]);
		}

		CustomPanelAPI rowPanel = panel.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, new RowActionPlugin(row));
		new UIRect(0f, 0f, rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBorder(rowPanel,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		if (columns.containsKey(Column.NUMBER)) {
			BattleNext_PageLayout.cellLabel(rowPanel, columns.get(Column.NUMBER), Integer.toString(index + 1),
					true, null, TooltipMakerAPI.TooltipLocation.RIGHT, false);
		}

		if (entry.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			UIRect first = columns.get(Column.PLAYER);
			UIRect last = columns.get(Column.SCALE);
			row.rawField = addField(row, "raw", rowPanel, new UIRect(first.getX(), 0f,
					last.getX() + last.getWidth() - first.getX(), BattleNext_PageLayout.TABLE_ROW_HEIGHT), row.rawText, viewport);
		} else {
			addFleetCell(row, Column.PLAYER, rowPanel, columns.get(Column.PLAYER), viewport);
			addFleetCell(row, Column.ENEMY, rowPanel, columns.get(Column.ENEMY), viewport);
			row.mapField = addField(row, "map", rowPanel, columns.get(Column.MAP), row.mapText, viewport);
			row.mapScaleField = addField(row, "scale", rowPanel, columns.get(Column.SCALE), row.scaleText, viewport);
		}

		row.statusLabel = BattleNext_PageLayout.cellLabel(rowPanel, columns.get(Column.STATUS), strings.get("unavailable"), true,
				new BaseTooltipCreator() {
					@Override
					public float getTooltipWidth(Object tooltipParam) {
						return 260f;
					}

					@Override
					public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
						List<String> reasons = validation.apply(row);
						if (reasons.isEmpty()) {
							tooltip.addPara(strings.get("available"), 0f);
						}
						for (String reason : reasons) {
							tooltip.addPara(reason, BattleNext_PageLayout.CONTROL_GAP);
						}
					}
				}, TooltipMakerAPI.TooltipLocation.LEFT, presentation.editable());

		if (columns.containsKey(Column.ENABLED)) {
			UIRect bounds = BattleNext_PageLayout.editorCheckboxCell(columns.get(Column.ENABLED));
			TooltipMakerAPI checkboxContent = rowPanel.createUIElement(bounds.getWidth(), bounds.getHeight(), false);
			row.enabledCheckbox = checkboxContent.addCheckbox(bounds.getWidth(), bounds.getHeight(), "", "enabled:" + index,
					ButtonAPI.UICheckboxSize.SMALL, 0f);
			row.enabledCheckbox.getPosition().inTL(0f, 0f);
			row.enabledCheckbox.setChecked(row.enabled);
			row.enabledCheckbox.setEnabled(presentation.editable() && row.rawField == null);
			checkboxContent.setHeightSoFar(bounds.getHeight());

			rowPanel.addUIElement(checkboxContent).inTL(bounds.getX(), bounds.getY());
		}

		Action action = presentation.action().apply(row);
		row.actionButton = BattleNext_PageLayout.fixedButton(rowPanel, BattleNext_PageLayout.editorActions(columns.get(Column.ACTIONS), 1)[0],
				action.label(), action.id(), CutStyle.ALL, false);
		row.actionButton.setEnabled(action.enabled());

		content.addCustom(rowPanel, 0f);
	}

	private void releaseRow(int index) {

		Row row = rows.get(index);
		row.mapText = row.text(Column.MAP);
		row.scaleText = row.text(Column.SCALE);
		row.rawText = row.rawText();
		row.enabled = row.isEnabled();
		for (Column column : List.of(Column.PLAYER, Column.ENEMY)) row.fleets.put(column, row.getFleetId(column));

		for (BattleNext_OptionField field : row.fleetFields.values()) {
			rememberField((BattleNext_TextField) field.getField());
			fields.values().remove(field.getField());
			field.close();
		}
		row.fleetFields.clear();

		for (BattleNext_TextField field : new BattleNext_TextField[]{row.mapField, row.mapScaleField, row.rawField}) {
			if (field == null) continue;
			rememberField(field);
			fields.values().remove(field);
			field.close();
		}

		row.mapField = null;
		row.mapScaleField = null;
		row.rawField = null;
		row.statusLabel = null;
		row.enabledCheckbox = null;
		row.actionButton = null;
	}

	private void rememberField(BattleNext_TextField field) {
		for (Map.Entry<Object, BattleNext_TextField> entry : fields.entrySet()) {
			if (entry.getValue() == field) savedInputs.put(entry.getKey(), field.captureEditState());
		}
	}

	private void addFleetCell(Row row, Column column, CustomPanelAPI owner, UIRect cell, TooltipMakerAPI viewport) {

		String fleetId = row.getFleetId(column);
		BaseTooltipCreator preview = new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return fleetSource.getTooltipWidth();
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				String id = row.getFleetId(column);
				if (id.isEmpty()) {
					tooltip.addPara(strings.get("unavailable"), 0f);
					return;
				}
				BattleNext_FleetModel selectedFleet = fleetStore.get(id);
				fleetSource.createTooltip(tooltip, new BattleNext_OptionPicker.OptionChoice(id, selectedFleet == null ? id : selectedFleet.getName()));
			}
		};

		if (presentation.editable()) {
			UIRect bounds = BattleNext_PageLayout.editorFieldCell(cell);
			TooltipMakerAPI content = owner.createUIElement(bounds.getWidth(), bounds.getHeight(), false);
			BattleNext_OptionField option = new BattleNext_OptionField(inputRouter, overlayHost, bounds.getWidth(),
					fleetSource, BattleNext_OptionField.OptionSelectionMode.SINGLE, viewport);
			option.attach(content, owner);

			BattleNext_TextField field = (BattleNext_TextField) option.getField();
			field.setText(fleetId);
			field.setMaxChars(-1);
			content.getPrev().getPosition().inTL(0f, 0f);
			content.addTooltipTo(preview, content.getPrev(), TooltipMakerAPI.TooltipLocation.RIGHT, true);
			content.setHeightSoFar(bounds.getHeight());

			owner.addUIElement(content).inTL(bounds.getX(), bounds.getY());

			row.fleetFields.put(column, option);
			fields.put(List.of(row.key, column.name()), field);
			BattleNext_TextField.EditState state = savedInputs.get(List.of(row.key, column.name()));
			if (state != null) field.restoreEditState(state);
			return;
		}

		row.fleetLabels.put(column, BattleNext_PageLayout.cellLabel(owner, cell, fleetId, true,
				preview, TooltipMakerAPI.TooltipLocation.RIGHT, true));
	}

	private BattleNext_TextField addField(Row row, String role, CustomPanelAPI rowPanel,
			UIRect cell, String value, TooltipMakerAPI viewport) {
		BattleNext_TextField field = BattleNext_PageLayout.cellTextField(inputRouter, rowPanel, cell, value, presentation.editable(), viewport);
		fields.put(List.of(row.key, role), field);
		BattleNext_TextField.EditState state = savedInputs.get(List.of(row.key, role));
		if (state != null) field.restoreEditState(state);
		return field;
	}

	public List<Row> getRowsReadonly() {
		return Collections.unmodifiableList(rows);
	}

	public TooltipMakerAPI getContent() {
		return tableWidget.getContent();
	}

	public ViewState captureState() {

		Map<Object, Boolean> enabled = new LinkedHashMap<>();
		Map<Object, Map<Column, String>> fleets = new LinkedHashMap<>();
		for (Row row : rows) {
			if (!row.isRaw()) {
				fleets.put(row.key, Map.of(Column.PLAYER, row.getFleetId(Column.PLAYER), Column.ENEMY, row.getFleetId(Column.ENEMY)));
			}
			enabled.put(row.key, row.isEnabled());
		}

		Map<Object, BattleNext_TextField.EditState> inputs = new LinkedHashMap<>(savedInputs);
		inputs.putAll(inputRouter.getSession().captureFields(fields));
		return new ViewState(getContent().getExternalScroller().getYOffset(), inputs, fleets, enabled);
	}

	public void restoreState(ViewState state) {

		savedInputs.putAll(state.fields());
		inputRouter.getSession().restoreFields(fields, state.fields(), presentation.editable());

		for (Row row : rows) {
			if (presentation.editable() && !row.isRaw() && state.fleets().containsKey(row.key)) {
				row.fleets.putAll(state.fleets().get(row.key));
			}
			BattleNext_TextField.EditState map = state.fields().get(List.of(row.key, "map"));
			BattleNext_TextField.EditState scale = state.fields().get(List.of(row.key, "scale"));
			BattleNext_TextField.EditState raw = state.fields().get(List.of(row.key, "raw"));
			if (map != null) row.mapText = map.text();
			if (scale != null) row.scaleText = scale.text();
			if (raw != null) row.rawText = raw.text();
			if (state.enabled().containsKey(row.key)) row.setEnabled(state.enabled().get(row.key));
		}

		getContent().getExternalScroller().setYOffset(state.scroll());
		refreshRowStatuses();
	}

	public void refreshRowStatuses() {
		for (Row row : rows) {
			if (row.statusLabel == null) continue;

			boolean raw = row.rawField != null;
			List<String> reasons = validation.apply(row);
			row.statusLabel.setText(raw ? battleStrings.get("raw_row_status") : strings.get(reasons.isEmpty() ? "available" : "unavailable"));
			row.statusLabel.setColor(raw || !reasons.isEmpty() ? BattleNext_UiTheme.PANEL : BattleNext_UiTheme.ACCENT);
			row.actionButton.setEnabled(presentation.action().apply(row).enabled());
		}
	}

	private class RowActionPlugin extends BaseCustomUIPanelPlugin {
		private final Row row;

		private RowActionPlugin(Row row) {
			this.row = row;
		}

		@Override
		public void buttonPressed(Object id) {
			if (!inputRouter.isEmpty()) return;
			Action action = presentation.action().apply(row);
			if (!action.enabled() || !action.id().equals(id)) return;
			presentation.onAction().accept(id);
		}
	}

	@Override
	protected void clearOverlays() {
		for (Row row : rows) {
			for (BattleNext_OptionField field : row.fleetFields.values()) {
				field.closeOverlays();
			}
		}
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		for (Row row : rows) {
			for (BattleNext_OptionField field : row.fleetFields.values()) {
				if (field.handleGlobalInput(events)) return true;
			}
		}
		return false;
	}

	@Override
	protected void clearContent() {
		if (tableWidget != null) tableWidget.close();

		for (Row row : rows) {
			for (BattleNext_OptionField field : row.fleetFields.values()) {
				fields.values().remove(field.getField());
				field.close();
			}
		}
		for (BattleNext_TextField field : fields.values()) {
			field.close();
		}

		fields.clear();
		savedInputs.clear();
		rows.clear();
		tableWidget = null;
	}
}
