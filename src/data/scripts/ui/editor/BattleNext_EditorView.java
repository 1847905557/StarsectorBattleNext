package data.scripts.ui.editor;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.*;
import data.scripts.tools.BattleNext_Logger;
import data.model.BattleNext_BaseStore.EditRejection;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class BattleNext_EditorView {

	protected BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();
	protected CustomPanelAPI panel;
	protected CustomPanelAPI overlayHost;
	protected String statusText = "";
	private CustomPanelAPI owner;
	private String baseline;

	protected final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private boolean restoring;
	private boolean draftCaptured;

	private final Map<Object, BattleNext_TextField> fields = new LinkedHashMap<>();
	private final Map<Object, BattleNext_TextField.EditState> inputs = new LinkedHashMap<>();
	private final Map<Object, ButtonAPI> checks = new LinkedHashMap<>();
	private final Map<Object, Boolean> checked = new LinkedHashMap<>();
	private final Map<Object, TooltipMakerAPI> viewports = new LinkedHashMap<>();
	private final Map<Object, Float> offsets = new LinkedHashMap<>();
	private final List<BattleNext_TooltipBasedWidget> tooltipWidgets = new ArrayList<>();
	private final List<BattleNext_PanelBasedWidget> panelWidgets = new ArrayList<>();

	public final void setInputRouter(BattleNext_UiInputRouter router) {
		inputRouter = router;
	}

	public final void setOverlayHost(CustomPanelAPI host) {
		overlayHost = host;
	}

	protected final void beginSession() {
		close();
		baseline = null;
		statusText = "";
	}

	public final void attach(CustomPanelAPI host) {
		detach();
		owner = host;
		createContents();
		if (baseline == null) baseline = draftState();
	}

	public final void detach() {
		detachContent();
		owner = null;
	}

	public final void createContents() {
		refreshContent();
	}

	public final void refreshContent() {
		if (owner == null) return;
		detachContent();
		panel = owner.createCustomPanel(owner.getPosition().getWidth(), owner.getPosition().getHeight(), owner.getPlugin());
		owner.addComponent(panel).inTL(0f, 0f);
		try {
			buildEditorContent();
			restoreState();
			afterRefresh();
		} catch (RuntimeException exception) {
			releaseContent();
			throw exception;
		}
	}

	protected abstract void buildEditorContent();

	protected void afterRefresh() {

	}

	protected final void createColumns(Object metadataKey, Object rowsKey,
			BiConsumer<TooltipMakerAPI, Float> metadataBuilder, BiConsumer<TooltipMakerAPI, Float> rowsBuilder) {
		UIRect[] columns = BattleNext_PageLayout.editorColumns(panel.getPosition().getWidth(), panel.getPosition().getHeight());
		List<BiConsumer<TooltipMakerAPI, Float>> builders = List.of(metadataBuilder, rowsBuilder);
		List<Object> keys = List.of(metadataKey, rowsKey);
		for (int index = 0; index < columns.length; index++) {
			UIRect column = columns[index];
			TooltipMakerAPI content = panel.createUIElement(column.getWidth(), column.getHeight(), true);
			BattleNext_UiTheme.configure(content);
			builders.get(index).accept(content, column.getWidth());
			panel.addUIElement(content).inTL(column.getX(), column.getY());
			bindViewport(keys.get(index), content);
		}
	}

	protected final TextFieldAPI createTextField(Object key, CustomPanelAPI hostPanel, TooltipMakerAPI fieldContent,
			float areaWidth, String label, String value, boolean editable, TooltipMakerAPI... viewports) {
		LabelAPI fieldLabel = fieldContent.addPara(label, BattleNext_PageLayout.EDITOR_LABEL_GAP);
		fieldLabel.getPosition().inTL(BattleNext_PageLayout.editorFieldInset(areaWidth),
				fieldContent.getHeightSoFar() - fieldLabel.getPosition().getHeight());
		if (!editable) {
			fieldContent.addPara(value, BattleNext_PageLayout.EDITOR_READ_ONLY_VALUE_GAP);
			return null;
		}
		BattleNext_TextField field = own(BattleNext_PageLayout.editorTextField(inputRouter, hostPanel, fieldContent,
				areaWidth, value, viewports));
		return bindField(key, field);
	}

	protected final TextFieldAPI createCellField(Object key, CustomPanelAPI hostPanel, UIRect cell,
			String value, TooltipMakerAPI... viewports) {
		UIRect bounds = BattleNext_PageLayout.editorFieldCell(cell);
		TooltipMakerAPI fieldContent = hostPanel.createUIElement(bounds.getWidth(), bounds.getHeight(), false);
		BattleNext_TextField field = own(new BattleNext_TextField(inputRouter, hostPanel, bounds.getWidth(), bounds.getHeight(), viewports));
		field.attach(fieldContent, hostPanel);
		field.setText(value);
		field.getPosition().inTL(0f, 0f);
		fieldContent.setHeightSoFar(bounds.getHeight());
		hostPanel.addUIElement(fieldContent).inTL(bounds.getX(), bounds.getY());
		return bindField(key, field);
	}

	protected final TextFieldAPI createOptionField(Object key, CustomPanelAPI hostPanel, TooltipMakerAPI fieldContent,
			float areaWidth, String label, String value, BattleNext_OptionPicker.OptionChoiceSource source,
			BattleNext_OptionField.OptionSelectionMode mode, TooltipMakerAPI... viewports) {
		LabelAPI fieldLabel = fieldContent.addPara(label, BattleNext_PageLayout.EDITOR_LABEL_GAP);
		fieldLabel.getPosition().inTL(BattleNext_PageLayout.editorFieldInset(areaWidth),
				fieldContent.getHeightSoFar() - fieldLabel.getPosition().getHeight());
		BattleNext_OptionField optionField = own(new BattleNext_OptionField(inputRouter, overlayHost,
				BattleNext_PageLayout.editorFieldWidth(areaWidth), source, mode, viewports));
		optionField.attach(fieldContent, hostPanel);
		TextFieldAPI field = optionField.getField();
		field.setMaxChars(-1);
		field.setText(value);
		fieldContent.getPrev().getPosition().inTL(BattleNext_PageLayout.editorFieldInset(areaWidth),
				fieldContent.getHeightSoFar() - BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		return bindField(key, field);
	}

	protected final BattleNext_OptionPicker createPicker(CustomPanelAPI hostPanel, TooltipMakerAPI fieldContent,
			float areaWidth, String label, BattleNext_OptionPicker.OptionChoiceSource source,
			BattleNext_OptionPicker.OptionSelectionMode mode, Supplier<List<String>> initialSelection,
			Consumer<List<String>> onConfirmed, TooltipMakerAPI... viewports) {
		BattleNext_OptionPicker picker = own(new BattleNext_OptionPicker(inputRouter, overlayHost,
				BattleNext_PageLayout.editorButtonWidth(areaWidth), BattleNext_PageLayout.EDITOR_BUTTON_HEIGHT,
				label, BattleNext_PageLayout.EDITOR_BUTTON_GAP, source, mode, initialSelection, onConfirmed, viewports));
		picker.attach(fieldContent, hostPanel);
		return picker;
	}

	protected final ButtonAPI createCheckbox(Object key, TooltipMakerAPI fieldContent, float areaWidth,
			String label, Object action, boolean selected, boolean editable) {
		ButtonAPI enabledCheckbox = fieldContent.addCheckbox(BattleNext_PageLayout.editorFieldWidth(areaWidth),
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, label, action, ButtonAPI.UICheckboxSize.SMALL,
				BattleNext_PageLayout.EDITOR_LABEL_GAP);
		enabledCheckbox.getPosition().inTL(BattleNext_PageLayout.editorFieldInset(areaWidth),
				fieldContent.getHeightSoFar() - BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		enabledCheckbox.setChecked(selected);
		enabledCheckbox.setEnabled(editable);
		bindCheck(key, enabledCheckbox);
		return enabledCheckbox;
	}

	protected final ButtonAPI createCellCheckbox(Object key, CustomPanelAPI hostPanel, UIRect cell, Object action,
			boolean selected, boolean editable, TooltipMakerAPI.TooltipCreator tooltip) {
		UIRect bounds = BattleNext_PageLayout.editorCheckboxCell(cell);
		TooltipMakerAPI fieldContent = hostPanel.createUIElement(bounds.getWidth(), bounds.getHeight(), false);
		ButtonAPI enabledCheckbox = fieldContent.addCheckbox(bounds.getWidth(), bounds.getHeight(), "", action,
				ButtonAPI.UICheckboxSize.SMALL, 0f);
		enabledCheckbox.getPosition().inTL(0f, 0f);
		enabledCheckbox.setChecked(selected);
		enabledCheckbox.setEnabled(editable);
		if (editable) bindCheck(key, enabledCheckbox);
		if (tooltip != null) fieldContent.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.RIGHT);
		fieldContent.setHeightSoFar(bounds.getHeight());
		hostPanel.addUIElement(fieldContent).inTL(bounds.getX(), bounds.getY());
		return enabledCheckbox;
	}

	protected final LabelAPI createTextCell(CustomPanelAPI hostPanel, UIRect cell, String text, boolean compact,
			TooltipMakerAPI.TooltipCreator tooltip, TooltipMakerAPI.TooltipLocation location, boolean recreate) {
		UIRect bounds = BattleNext_PageLayout.editorLabelCell(cell);
		float cellWidth = bounds.getWidth();
		TooltipMakerAPI fieldContent = hostPanel.createUIElement(cellWidth, bounds.getHeight(), false);
		fieldContent.setTextWidthOverride(cellWidth);
		if (compact) fieldContent.setParaSmallInsignia();
		LabelAPI label = fieldContent.addPara(fieldContent.shortenString(text,
				Math.max(1f, cell.getWidth() - BattleNext_PageLayout.CELL_LABEL_TRUNCATION_PADDING)), 0f);
		label.setAlignment(Alignment.MID);
		label.getPosition().setSize(cellWidth, label.getPosition().getHeight());
		label.getPosition().inTL(0f, Math.max(0f, (cell.getHeight() - label.getPosition().getHeight()) * 0.5f));
		if (tooltip != null) {
			fieldContent.addTooltipTo(tooltip, fieldContent.getPrev(), location, recreate);
		} else {
			BattleNext_PageLayout.addMetadataTooltip(fieldContent, text);
		}
		fieldContent.setHeightSoFar(cell.getHeight());
		hostPanel.addUIElement(fieldContent).inTL(bounds.getX(), bounds.getY());
		return label;
	}

	protected record EditorAction(String label, Object id, boolean enabled) { }

	protected final void createActions(CustomPanelAPI hostPanel, UIRect cell, EditorAction... actions) {
		UIRect[] bounds = BattleNext_PageLayout.editorActions(cell, actions.length);
		for (int index = 0; index < actions.length; index++) {
			EditorAction action = actions[index];
			createFixedButton(hostPanel, bounds[index], action.label(), action.id(), false, action.enabled());
		}
	}

	protected final ButtonAPI createButton(TooltipMakerAPI fieldContent, float areaWidth, String label, Object action) {
		return BattleNext_PageLayout.editorButton(fieldContent, label, action, areaWidth);
	}

	protected final ButtonAPI createFixedButton(CustomPanelAPI hostPanel, UIRect bounds, String label, Object action,
			boolean prominent, boolean enabled) {
		ButtonAPI button = BattleNext_PageLayout.fixedButton(hostPanel, bounds, label, action, CutStyle.ALL, prominent);
		button.setEnabled(enabled);
		return button;
	}

	protected final BattleNext_TableWidget createTable(Object key, TooltipMakerAPI rowsContent, float areaWidth,
			String title, String[] headers, float[] columnShares, int rowCount, BattleNext_TableWidget.RowBuilder rowBuilder,
			BiConsumer<Float, TooltipMakerAPI> contentBuilder) {
		rowsContent.addTitle(title);
		float areaHeight = Math.max(1f, panel.getPosition().getHeight() - BattleNext_PageLayout.EDITOR_TABLE_TITLE_HEIGHT);
		CustomPanelAPI tablePanel = panel.createCustomPanel(areaWidth, areaHeight, panel.getPlugin());
		rowsContent.addComponent(tablePanel).inTL(0f, BattleNext_PageLayout.EDITOR_TABLE_TITLE_HEIGHT);
		BattleNext_TableWidget tableWidget = own(new BattleNext_TableWidget(panel.getPlugin(), headers, columnShares,
				rowCount, rowBuilder, contentBuilder));
		tableWidget.attach(tablePanel);
		bindViewport(key, tableWidget.getContent());
		return tableWidget;
	}

	protected final void refreshStructure() {
		captureState();
		draftCaptured = true;
		refreshContent();
	}

	protected final void captureState() {
		for (var entry : fields.entrySet()) {
			inputs.put(entry.getKey(), entry.getValue().captureEditState());
		}
		for (var entry : checks.entrySet()) {
			checked.put(entry.getKey(), entry.getValue().isChecked());
		}
		for (var entry : viewports.entrySet()) {
			offsets.put(entry.getKey(), entry.getValue().getExternalScroller().getYOffset());
		}
	}

	protected final void restoreState() {
		for (var entry : fields.entrySet()) {
			BattleNext_TextField.EditState state = inputs.get(entry.getKey());
			if (state != null) entry.getValue().restoreEditState(state);
		}
		for (var entry : checks.entrySet()) {
			if (checked.containsKey(entry.getKey())) entry.getValue().setChecked(checked.get(entry.getKey()));
		}
		for (var entry : viewports.entrySet()) {
			entry.getValue().getExternalScroller().setYOffset(offsets.getOrDefault(entry.getKey(), 0f));
		}
	}

	protected final TextFieldAPI bindField(Object key, TextFieldAPI field) {
		BattleNext_TextField widget = (BattleNext_TextField) field;
		widget.setMaxChars(-1);
		fields.put(key, widget);
		return field;
	}

	protected final void bindCheck(Object key, ButtonAPI button) {
		checks.put(key, button);
	}

	protected final void bindViewport(Object key, TooltipMakerAPI viewport) {
		viewports.put(key, viewport);
	}

	protected final <T extends BattleNext_TooltipBasedWidget> T own(T widget) {
		tooltipWidgets.add(widget);
		return widget;
	}

	protected final <T extends BattleNext_PanelBasedWidget> T own(T widget) {
		panelWidgets.add(widget);
		return widget;
	}

	protected final void release(BattleNext_TooltipBasedWidget widget) {
		widget.close();
		tooltipWidgets.remove(widget);
	}

	public abstract void buttonPressed(Object buttonId);

	public final boolean handleGlobalInput(List<InputEventAPI> events) {
		for (BattleNext_TooltipBasedWidget widget : List.copyOf(tooltipWidgets)) {
			if (widget.handleGlobalInput(events)) return true;
		}
		for (BattleNext_PanelBasedWidget widget : List.copyOf(panelWidgets)) {
			if (widget.handleGlobalInput(events)) return true;
		}
		return false;
	}

	public final boolean hasUnsavedChanges() {
		return baseline != null && !draftState().equals(baseline);
	}

	public final String serializeState() {
		return draftState();
	}

	protected abstract String draftState();

	public final String getStatusText() {
		return statusText;
	}

	public final boolean save() {
		return serialize();
	}

	public final boolean serialize() {
		if (!submitDraft()) return false;
		baseline = draftState();
		statusText = strings.get("saved");
		return true;
	}

	protected abstract boolean submitDraft();

	protected final boolean validateName(String name, String message) {
		if (!name.isBlank()) return true;
		statusText = message;
		BattleNext_Logger.warn(getClass(), "editor.name.rejected", BattleNext_Logger.fields()
				.put("name", name).put("input_state", draftState()).put("criterion", "Nonblank name").build());
		return false;
	}

	protected final String failureStatus(Exception exception) {
		if (exception instanceof EditRejection rejection) {
			return switch (rejection.getReason()) {
				case DUPLICATE_ID -> strings.get("duplicate_id") + ": " + exception.getMessage();
				case SOURCE_UNAVAILABLE -> strings.get("source_unavailable") + ": " + exception.getMessage();
				case DUPLICATE_FOLDER -> BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_").get("duplicate_folder") + ": " + exception.getMessage();
			};
		}
		return strings.get("save_failed") + ": " + exception.getMessage();
	}

	protected void captureDraftInputs() {

	}

	public final void discard() {
		deserialize();
	}

	public final void deserialize() {
		restoring = true;
		try {
			restoreDraft();
			inputs.clear();
			checked.clear();
			offsets.clear();
			refreshContent();
			baseline = draftState();
		} finally {
			restoring = false;
		}
	}

	protected abstract void restoreDraft();

	public final void closeOverlays() {
		for (BattleNext_TooltipBasedWidget widget : tooltipWidgets) {
			widget.closeOverlays();
		}
		for (BattleNext_PanelBasedWidget widget : panelWidgets) {
			widget.closeOverlays();
		}
	}

	public final void close() {
		detach();
		inputs.clear();
		checked.clear();
		offsets.clear();
		baseline = null;
		closeEditorState();
	}

	protected void closeEditorState() {

	}

	private void detachContent() {
		if (panel == null) return;
		if (!restoring) {
			captureState();
			if (!draftCaptured) captureDraftInputs();
		}
		draftCaptured = false;
		releaseContent();
	}

	private void releaseContent() {
		for (int index = tooltipWidgets.size() - 1; index >= 0; index--) {
			tooltipWidgets.get(index).close();
		}
		for (int index = panelWidgets.size() - 1; index >= 0; index--) {
			panelWidgets.get(index).close();
		}
		tooltipWidgets.clear();
		panelWidgets.clear();
		fields.clear();
		checks.clear();
		viewports.clear();
		owner.removeComponent(panel);
		panel = null;
	}
}
