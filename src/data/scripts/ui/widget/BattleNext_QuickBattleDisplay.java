package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.*;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;

public class BattleNext_QuickBattleDisplay extends BattleNext_PanelBasedWidget {

	public enum Column {
		NUMBER, PLAYER, ENEMY, MAP, SCALE, STATUS, ENABLED, ACTIONS
	}

	public record Action(String label, Object id, boolean enabled) { }

	public record Presentation(List<Column> columns, List<Float> columnShares, boolean editable,
			Function<Row, Action> action, Consumer<Object> onAction) {
		public Presentation {
			columns = List.copyOf(columns);
			columnShares = List.copyOf(columnShares);
		}
	}

	public record ViewState(float scroll, Map<Object, BattleNext_TextField.EditState> fields, Map<Object, Boolean> enabled) {
		public ViewState {
			fields = Map.copyOf(fields);
			enabled = Map.copyOf(enabled);
		}
	}

	public static class Row {
		private final int index;
		private final Object key;
		private BattleNext_QuickBattleModel model;
		private BattleNext_TextField playerField;
		private BattleNext_TextField enemyField;
		private BattleNext_TextField mapField;
		private BattleNext_TextField mapScaleField;
		private BattleNext_TextField rawField;
		private ButtonAPI enabledCheckbox;
		private LabelAPI statusLabel;
		private ButtonAPI actionButton;

		private Row(int index, Object key, BattleNext_QuickBattleModel model) {
			this.index = index;
			this.key = key;
			this.model = model;
		}

		public int getIndex() {
			return index;
		}

		public BattleNext_QuickBattleModel getModel() {
			return model;
		}

		public void setModel(BattleNext_QuickBattleModel model) {
			this.model = model;
		}

		public BattleNext_TextField getField(Column column) {
			return switch (column) {
				case PLAYER -> playerField;
				case ENEMY -> enemyField;
				case MAP -> mapField;
				case SCALE -> mapScaleField;
				default -> throw new IllegalArgumentException("Column does not contain an input: " + column);
			};
		}

		public BattleNext_TextField getRawField() {
			return rawField;
		}

		public boolean isEnabled() {
			return enabledCheckbox == null ? model.isEnabled() : enabledCheckbox.isChecked();
		}

		public void setEnabled(boolean enabled) {
			enabledCheckbox.setChecked(enabled);
		}
	}

	private final List<BattleNext_QuickBattleModel> entries;
	private final Presentation presentation;
	private final Function<BattleNext_QuickBattleModel, Object> identity;
	private final Function<Row, List<String>> validation;
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final Map<Object, BattleNext_TextField> fields = new LinkedHashMap<>();
	private final List<Row> rows = new ArrayList<>();
	private BattleNext_TableWidget tableWidget;

	public BattleNext_QuickBattleDisplay(BattleNext_UiInputRouter inputRouter, List<BattleNext_QuickBattleModel> entries,
			Presentation presentation, Function<BattleNext_QuickBattleModel, Object> identity, Function<Row, List<String>> validation) {
		super(inputRouter);
		this.entries = List.copyOf(entries);
		this.presentation = presentation;
		this.identity = identity;
		this.validation = validation;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {

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
		tableWidget = new BattleNext_TableWidget(panel.getPlugin(), headers, shares, entries.size(), this::addRow);
		tableWidget.attach(tablePanel);
		refreshRowStatuses();
	}

	private void addRow(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI content) {

		BattleNext_QuickBattleModel entry = entries.get(index);
		Row row = new Row(index, identity.apply(entry), entry);
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
					last.getX() + last.getWidth() - first.getX(), BattleNext_PageLayout.TABLE_ROW_HEIGHT), entry.getRawContent(), content);
		} else {
			row.playerField = addField(row, "player", rowPanel, columns.get(Column.PLAYER), entry.getPlayer(), content);
			row.enemyField = addField(row, "enemy", rowPanel, columns.get(Column.ENEMY), entry.getEnemy(), content);
			row.mapField = addField(row, "map", rowPanel, columns.get(Column.MAP), Integer.toString(entry.getMap()), content);
			row.mapScaleField = addField(row, "scale", rowPanel, columns.get(Column.SCALE), Float.toString(entry.getMapScale()), content);
		}
		row.statusLabel = BattleNext_PageLayout.cellLabel(rowPanel, columns.get(Column.STATUS), strings.get("unavailable"), true,
				new BaseTooltipCreator() {
					@Override
					public float getTooltipWidth(Object ignored) {
						return 260f;
					}

					@Override
					public void createTooltip(TooltipMakerAPI target, boolean expanded, Object ignored) {
						List<String> reasons = validation.apply(row);
						if (reasons.isEmpty()) {
							target.addPara(strings.get("available"), 0f);
						}
						for (String reason : reasons) {
							target.addPara(reason, 4f);
						}
					}
				}, TooltipMakerAPI.TooltipLocation.LEFT, presentation.editable());
		if (columns.containsKey(Column.ENABLED)) {
			UIRect bounds = BattleNext_PageLayout.editorCheckboxCell(columns.get(Column.ENABLED));
			TooltipMakerAPI checkboxContent = rowPanel.createUIElement(bounds.getWidth(), bounds.getHeight(), false);
			row.enabledCheckbox = checkboxContent.addCheckbox(bounds.getWidth(), bounds.getHeight(), "", "enabled:" + index,
					ButtonAPI.UICheckboxSize.SMALL, 0f);
			row.enabledCheckbox.getPosition().inTL(0f, 0f);
			row.enabledCheckbox.setChecked(entry.isEnabled());
			row.enabledCheckbox.setEnabled(presentation.editable() && row.rawField == null);
			checkboxContent.setHeightSoFar(bounds.getHeight());
			rowPanel.addUIElement(checkboxContent).inTL(bounds.getX(), bounds.getY());
		}
		Action action = presentation.action().apply(row);
		row.actionButton = BattleNext_PageLayout.fixedButton(rowPanel, BattleNext_PageLayout.editorActions(columns.get(Column.ACTIONS), 1)[0],
				action.label(), action.id(), CutStyle.ALL, false);
		row.actionButton.setEnabled(action.enabled());
		content.addCustom(rowPanel, 0f);
		rows.add(row);
	}

	private BattleNext_TextField addField(Row row, String role, CustomPanelAPI rowPanel,
			UIRect cell, String value, TooltipMakerAPI viewport) {
		BattleNext_TextField field = BattleNext_PageLayout.cellTextField(inputRouter, rowPanel, cell, value, presentation.editable(), viewport);
		fields.put(List.of(row.key, role), field);
		return field;
	}

	public List<Row> getRows() {
		return Collections.unmodifiableList(rows);
	}

	public TooltipMakerAPI getContent() {
		return tableWidget.getContent();
	}

	public ViewState captureState() {
		Map<Object, Boolean> enabled = new LinkedHashMap<>();
		for (Row row : rows) {
			if (row.enabledCheckbox == null) continue;
			enabled.put(row.key, row.isEnabled());
		}
		return new ViewState(getContent().getExternalScroller().getYOffset(), inputRouter.getSession().captureFields(fields), enabled);
	}

	public void restoreState(ViewState state) {
		inputRouter.getSession().restoreFields(fields, state.fields(), presentation.editable());
		for (Row row : rows) {
			if (row.enabledCheckbox == null || !state.enabled().containsKey(row.key)) continue;
			row.setEnabled(state.enabled().get(row.key));
		}
		getContent().getExternalScroller().setYOffset(state.scroll());
		refreshRowStatuses();
	}

	public void refreshRowStatuses() {
		for (Row row : rows) {
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
	protected void clearContent() {
		for (BattleNext_TextField field : fields.values()) {
			field.close();
		}
		fields.clear();
		rows.clear();
		if (tableWidget != null) {
			tableWidget.close();
			tableWidget = null;
		}
	}
}
