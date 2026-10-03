package data.scripts.ui.editor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_TextField;
import org.json.JSONException;

import java.io.IOException;
import java.util.*;

public class BattleNext_RuleEditor implements BattleNext_EditorView {

	private BattleNext_UiInputRouter inputRouter = new BattleNext_UiInputRouter();

	@Override
	public void setInputRouter(BattleNext_UiInputRouter router) {
		this.inputRouter = router;
	}

	private static final float RESTRICTION_TITLE_HEIGHT = 24f;
	private static final float DETAILS_FIELD_RIGHT_INSET = 12f;
	private static final float STATUS_LINE_GAP = 4f;

	private static final float[] COLUMN_SHARES = {0.3f, 0.34f, 0.16f, 0.2f};
	private static final int DESCRIPTION_COLUMN = 0;
	private static final int VALUE_COLUMN = 1;
	private static final int ENABLED_COLUMN = 2;
	private static final int ACTIONS_COLUMN = 3;

	private static final float RULE_PICKER_MAX_WIDTH = 190f;
	private static final float MISSING_VALUE_TRUNCATION_PADDING = 12f;

	private static final float FIELD_MIN_WIDTH = 40f;
	private static final float DETAILS_FIELD_MIN_WIDTH = 90f;
	private static final float DETAILS_FIELD_LABEL_GAP = 6f;

	private static final int ACTION_BUTTON_COUNT = 3;
	private static final float ACTION_BUTTON_MIN_WIDTH = 22f;
	private static final float ACTION_BUTTON_SIDE_PADDING = 8f;

	private static final float RESTRICTION_TOOLTIP_WIDTH = 320f;
	private static final float RESTRICTION_TOOLTIP_META_GAP = 5f;

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_RuleStore store;
	private final Map<String, ButtonAPI> enabledFields = new LinkedHashMap<>();
	private final Map<String, TextFieldAPI> valueFields = new LinkedHashMap<>();
	private final List<BattleNext_TextField> textFields = new ArrayList<>();
	private CustomPanelAPI panel;
	private UIRect contentRect;
	private BattleNext_RuleModel draft;
	private BattleNext_RuleModel baseline;
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private LabelAPI statusLabel;
	private BattleNext_OptionPicker restrictionPicker;
	private String savedSnapshot = "";
	private String status = "";
	private BattleNext_TableWidget tableWidget;

	public BattleNext_RuleEditor(BattleNext_RuleStore store) {
		this.store = store;
	}

	public void open(BattleNext_RuleModel rule) throws JSONException {
		baseline = rule == null ? null : copy(rule);
		draft = rule == null ? new BattleNext_RuleModel() : copy(rule);
		idField = null;
		nameField = null;
		status = "";
	}

	public String getDraftId() {
		return idField.getText().trim();
	}

	public void closePicker() {
		if (restrictionPicker != null) restrictionPicker.closeOverlays();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return restrictionPicker != null && restrictionPicker.handleGlobalInput(events);
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		boolean opened = idField == null;
		this.panel = panel;
		createContents();
		if (opened) {
			savedSnapshot = serializeState();
		}
	}

	@Override
	public void createContents() {
		contentRect = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		refreshContent();
	}

	@Override
	public void refreshContent() {
		String idValue = idField == null ? draft.getId() : idField.getText();
		String nameValue = nameField == null ? draft.getName() : nameField.getText();
		Map<String, String> values = new LinkedHashMap<>();
		if (idField != null) {
			for (var entry : valueFields.entrySet()) {
				values.put(entry.getKey(), entry.getValue().getText());
			}
			captureInputs(false);
		}
		closePicker();
		if (restrictionPicker != null) restrictionPicker.close();
		if (tableWidget != null) tableWidget.close();
		for (BattleNext_TextField field : textFields) {
			field.close();
		}
		textFields.clear();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		float usableWidth = Math.max(BattleNext_PageLayout.EDITOR_MIN_SPLIT_WIDTH,
				contentRect.getWidth() - BattleNext_PageLayout.EDITOR_COLUMN_GAP);
		float leftWidth = (float) Math.floor(usableWidth * BattleNext_PageLayout.EDITOR_LEFT_COLUMN_SHARE);
		float rightWidth = usableWidth - leftWidth;
		TooltipMakerAPI details = contentPanel.createUIElement(leftWidth, contentRect.getHeight(), true);
		details.addTitle(ruleStrings.get("editor_title"));
		statusLabel = details.addPara(status, STATUS_LINE_GAP);
		idField = field(details, panel, ruleStrings.get("id"), idValue,
				leftWidth - DETAILS_FIELD_RIGHT_INSET, details);
		nameField = field(details, panel, ruleStrings.get("name"), nameValue,
				leftWidth - DETAILS_FIELD_RIGHT_INSET, details);
		contentPanel.addUIElement(details).inTL(0f, 0f);

		TooltipMakerAPI restrictions = contentPanel.createUIElement(rightWidth, contentRect.getHeight(), true);
		restrictions.addTitle(ruleStrings.get("restrictions"));
		contentPanel.addUIElement(restrictions).inTL(leftWidth + BattleNext_PageLayout.EDITOR_COLUMN_GAP, 0f);
		float tableY = RESTRICTION_TITLE_HEIGHT;
		float tableHeight = Math.max(1f, contentRect.getHeight() - tableY);
		CustomPanelAPI tablePanel = panel.createCustomPanel(rightWidth, tableHeight, panel.getPlugin());
		restrictions.addComponent(tablePanel).inTL(0f, tableY);
		String[] labels = {ruleStrings.get("restriction"), ruleStrings.get("value_or_items"), strings.get("enabled"), strings.get("actions")};
		enabledFields.clear();
		valueFields.clear();
		tableWidget = new BattleNext_TableWidget(panel.getPlugin(), labels, COLUMN_SHARES,
				draft.getRestrictions().size(),
				(index, rowWidth, cells, content) -> addRestrictionRow(index, rowWidth, cells, content, restrictions),
				(rowWidth, content) -> {
					restrictionPicker = new BattleNext_OptionPicker(inputRouter, panel, Math.min(190f, rowWidth),
							BattleNext_PageLayout.FORM_FIELD_HEIGHT, ruleStrings.get("add_restriction"), 2f,
							new RestrictionChoiceSource(), BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE,
							List::of, this::addRestrictions, content, restrictions);
					restrictionPicker.attach(content, panel);
				});
		tableWidget.attach(tablePanel);
		for (var entry : values.entrySet()) {
			if (valueFields.containsKey(entry.getKey())) {
				valueFields.get(entry.getKey()).setText(entry.getValue());
			}
		}
	}

	private void addRestrictionRow(int index, float tableWidth, UIRect[] cells,
			TooltipMakerAPI content, TooltipMakerAPI restrictions) {
		BattleNext_RuleRestriction restriction = draft.getRestrictions().get(index);
		BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
		String description = spec == null ? ruleStrings.get("missing_restriction") : spec.getDescription();
		CustomPanelAPI row = Global.getSettings().createCustom(tableWidth,
				BattleNext_PageLayout.TABLE_ROW_HEIGHT, panel.getPlugin());
		new UIRect(0f, 0f, tableWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBoarder(row,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		float descriptionWidth = Math.max(1f,
				cells[DESCRIPTION_COLUMN].getWidth() - BattleNext_PageLayout.CELL_LABEL_SIDE_INSET * 2f);
		TooltipMakerAPI descriptionHost = row.createUIElement(descriptionWidth,
				BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
		LabelAPI descriptionLabel = descriptionHost.addPara(
				descriptionHost.shortenString(description, Math.max(1f,
						cells[DESCRIPTION_COLUMN].getWidth() - BattleNext_PageLayout.CELL_LABEL_TRUNCATION_PADDING)), 0f);
		descriptionLabel.setAlignment(Alignment.MID);
		descriptionLabel.getPosition().setSize(descriptionWidth,
				descriptionLabel.getPosition().getHeight());
		descriptionLabel.getPosition().inTL(0f,
				Math.max(0f, (BattleNext_PageLayout.TABLE_ROW_HEIGHT
						- descriptionLabel.getPosition().getHeight()) * 0.5f));
		descriptionHost.addTooltipToPrevious(new RestrictionTooltip(restriction.getId(), restriction.getType(), description),
				TooltipMakerAPI.TooltipLocation.RIGHT);
		descriptionHost.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		row.addUIElement(descriptionHost).inTL(cells[DESCRIPTION_COLUMN].getX()
				+ BattleNext_PageLayout.CELL_LABEL_SIDE_INSET, cells[DESCRIPTION_COLUMN].getY());
		if (spec == null) {
			float missingWidth = Math.max(1f,
					cells[VALUE_COLUMN].getWidth() - BattleNext_PageLayout.CELL_LABEL_SIDE_INSET * 2f);
			TooltipMakerAPI missing = row.createUIElement(missingWidth,
					BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
			LabelAPI missingLabel = missing.addPara(missing.shortenString(displayValue(restriction),
					cells[VALUE_COLUMN].getWidth() - MISSING_VALUE_TRUNCATION_PADDING), 0f);
			missingLabel.setAlignment(Alignment.MID);
			missingLabel.getPosition().setSize(missingWidth,
					missingLabel.getPosition().getHeight());
			missingLabel.getPosition().inTL(0f,
					Math.max(0f, (BattleNext_PageLayout.TABLE_ROW_HEIGHT
							- missingLabel.getPosition().getHeight()) * 0.5f));
			missing.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
			row.addUIElement(missing).inTL(cells[VALUE_COLUMN].getX()
					+ BattleNext_PageLayout.CELL_LABEL_SIDE_INSET, 0f);
		} else {
			addField(cells[VALUE_COLUMN], row, displayValue(restriction), restriction.getId(), content, restrictions);
			float checkboxWidth = Math.min(BattleNext_PageLayout.TABLE_CHECKBOX_MAX_WIDTH,
					cells[ENABLED_COLUMN].getWidth());
			TooltipMakerAPI enabledHost = row.createUIElement(checkboxWidth,
					BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
			ButtonAPI enabled = enabledHost.addCheckbox(checkboxWidth,
					BattleNext_PageLayout.TABLE_ROW_HEIGHT, "",
					"rule:enabled:" + index, ButtonAPI.UICheckboxSize.SMALL, 0f);
			enabled.setChecked(restriction.isEnabled());
			enabledFields.put(restriction.getId(), enabled);
			enabledHost.setHeightSoFar(BattleNext_PageLayout.TABLE_ROW_HEIGHT);
			row.addUIElement(enabledHost).inTL(cells[ENABLED_COLUMN].getX()
					+ (cells[ENABLED_COLUMN].getWidth() - checkboxWidth) * 0.5f, 0f);
		}
		float buttonWidth = Math.max(ACTION_BUTTON_MIN_WIDTH,
				(cells[ACTIONS_COLUMN].getWidth() - ACTION_BUTTON_SIDE_PADDING) / ACTION_BUTTON_COUNT);
		float buttonsX = cells[ACTIONS_COLUMN].getX()
				+ (cells[ACTIONS_COLUMN].getWidth() - buttonWidth * ACTION_BUTTON_COUNT) * 0.5f;
		float buttonY = (BattleNext_PageLayout.TABLE_ROW_HEIGHT
				- BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT) * 0.5f;
		BattleNext_PageLayout.fixedButton(row, new UIRect(buttonsX, buttonY,
				buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT),
				"^", "rule:up:" + index, CutStyle.ALL, false).setEnabled(index > 0);
		BattleNext_PageLayout.fixedButton(row, new UIRect(buttonsX + buttonWidth, buttonY,
				buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT),
				"V", "rule:down:" + index, CutStyle.ALL, false)
				.setEnabled(index + 1 < draft.getRestrictions().size());
		BattleNext_PageLayout.fixedButton(row, new UIRect(buttonsX + buttonWidth * (ACTION_BUTTON_COUNT - 1),
				buttonY, buttonWidth, BattleNext_PageLayout.TABLE_ACTION_BUTTON_HEIGHT),
				"X", "rule:remove:" + index, CutStyle.ALL, false);
		content.addCustom(row, 0f);
	}

	private TextFieldAPI addField(UIRect rect, CustomPanelAPI owner, String value, String key,
			TooltipMakerAPI... viewports) {
		float width = Math.max(FIELD_MIN_WIDTH, rect.getWidth()
				- BattleNext_PageLayout.TABLE_FIELD_LEFT_INSET - BattleNext_PageLayout.TABLE_FIELD_RIGHT_INSET);
		TooltipMakerAPI maker = owner.createUIElement(width, BattleNext_PageLayout.FORM_FIELD_HEIGHT, false);
		BattleNext_TextField widget = new BattleNext_TextField(inputRouter, owner, width,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, viewports);
		textFields.add(widget);
		widget.attach(maker, owner);
		widget.setText(value);
		TextFieldAPI field = widget;
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		maker.setHeightSoFar(BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		owner.addUIElement(maker).inTL(rect.getX() + BattleNext_PageLayout.TABLE_FIELD_LEFT_INSET,
				rect.getY() + (rect.getHeight() - BattleNext_PageLayout.FORM_FIELD_HEIGHT) * 0.5f);
		valueFields.put(key, field);
		return field;
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (!(buttonId instanceof String action)) return;
		if (!action.startsWith("rule:up:") && !action.startsWith("rule:down:")
				&& !action.startsWith("rule:remove:")) return;
		try {
			captureInputs(true);
		} catch (RuntimeException exception) {
			setStatus(exception.getMessage());
			return;
		}
		int index = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>(draft.getRestrictions());
		if (action.startsWith("rule:remove:")) {
			restrictions.remove(index);
		} else {
			int target = action.startsWith("rule:up:") ? index - 1 : index + 1;
			if (target < 0 || target >= restrictions.size()) return;
			Collections.swap(restrictions, index, target);
		}
		draft.setRestrictions(restrictions);
		refreshContent();
	}

	private void addRestrictions(List<String> ids) {
		if (ids.isEmpty()) return;
		try {
			captureInputs(true);
		} catch (RuntimeException exception) {
			setStatus(exception.getMessage());
			return;
		}
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>(draft.getRestrictions());
		for (String id : ids) {
			BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(id);
			if (spec == null || draft.getRestriction(id) != null) continue;
			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id, spec.getType());
			restriction.setEnabled(true);
			restrictions.add(restriction);
		}
		draft.setRestrictions(restrictions);
		refreshContent();
	}

	private void captureInputs(boolean validate) {
		if (idField == null) return;
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>();
		for (BattleNext_RuleRestriction current : draft.getRestrictions()) {
			BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(current.getId());
			if (spec == null) {
				restrictions.add(current);
				continue;
			}
			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(current.getId(), spec.getType());
			restriction.setEnabled(enabledFields.get(current.getId()).isChecked());
			String input = valueFields.get(current.getId()).getText().trim();
			try {
				if ("number".equals(spec.getType())) {
					restriction.setValue(Double.parseDouble(input));
				} else if (!input.isEmpty()) {
					List<String> items = new ArrayList<>();
					for (String item : input.split(",", -1)) items.add(item.trim());
					restriction.setItems(items);
				}
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_RuleEditor.class, "rule.restriction.input.rejected",
				BattleNext_Logger.fields().put("rule_id", draft.getId()).put("restriction_id", current.getId()).put("input", input).put("type", spec.getType()).put("validate", validate).put("fallback", current).build(), exception);
				if (validate) throw new IllegalArgumentException(current.getId() + ": " + exception.getMessage(), exception);
				restriction = current;
			}
			restrictions.add(restriction);
		}
		draft.setRestrictions(restrictions);
	}

	@Override
	public boolean serialize() {
		try {
			captureInputs(true);
			store.save(draft);
			baseline = copy(draft);
			savedSnapshot = serializeState();
			setStatus(strings.get("saved"));
			return true;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.error(BattleNext_RuleEditor.class, "rule.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).build(), exception);
			setStatus(strings.get("save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override
	public void deserialize() {
		try {
			draft = baseline == null ? new BattleNext_RuleModel() : copy(baseline);
			idField = null;
			nameField = null;
			refreshContent();
			savedSnapshot = serializeState();
		} catch (JSONException exception) {
			setStatus(strings.get("load_failed") + ": " + exception.getMessage());
		}
	}

	@Override
	public String serializeState() {
		if (idField == null) return "";
		StringBuilder state = new StringBuilder();
		append(state, idField.getText());
		append(state, nameField.getText());
		for (BattleNext_RuleRestriction restriction : draft.getRestrictions()) {
			append(state, restriction.getId());
			if (enabledFields.containsKey(restriction.getId())) {
				append(state, Boolean.toString(enabledFields.get(restriction.getId()).isChecked()));
				append(state, valueFields.get(restriction.getId()).getText());
			} else {
				append(state, Boolean.toString(restriction.isEnabled()));
				append(state, displayValue(restriction));
			}
		}
		return state.toString();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return !serializeState().equals(savedSnapshot);
	}

	@Override
	public String getStatusText() {
		return status;
	}

	private TextFieldAPI field(TooltipMakerAPI host, CustomPanelAPI owner, String label, String value, float width,
			TooltipMakerAPI... viewports) {
		host.addPara(label, DETAILS_FIELD_LABEL_GAP);
		float fieldWidth = Math.max(DETAILS_FIELD_MIN_WIDTH, width);
		BattleNext_TextField widget = new BattleNext_TextField(inputRouter, owner, fieldWidth,
				BattleNext_PageLayout.FORM_FIELD_HEIGHT, viewports);
		textFields.add(widget);
		widget.attach(host, owner);
		widget.setText(value);
		return widget;
	}

	private String displayValue(BattleNext_RuleRestriction restriction) {
		return "number".equals(restriction.getType()) ? Double.toString(restriction.getValue())
				: String.join(",", restriction.getItems());
	}

	private void append(StringBuilder state, String value) {
		state.append(value.length()).append(':').append(value);
	}

	private BattleNext_RuleModel copy(BattleNext_RuleModel source) throws JSONException {
		BattleNext_RuleModel result = new BattleNext_RuleModel();
		result.fromJson(source.toJson());
		result.setSourceType(source.getSourceType());
		result.setSourcePath(source.getSourcePath());
		return result;
	}

	private static class RestrictionTooltip extends BaseTooltipCreator {

		private final String id;
		private final String type;
		private final String description;

		private RestrictionTooltip(String id, String type, String description) {
			this.id = id;
			this.type = type;
			this.description = description;
		}

		@Override
		public float getTooltipWidth(Object ignored) {
			return RESTRICTION_TOOLTIP_WIDTH;
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
			tooltip.addPara(description, 0f);
			tooltip.addPara(id + "  [" + type + "]", RESTRICTION_TOOLTIP_META_GAP);
		}
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private class RestrictionChoiceSource implements BattleNext_OptionPicker.OptionChoiceSource {

		@Override
		public List<BattleNext_OptionPicker.OptionChoice> getOptions() {
			List<BattleNext_OptionPicker.OptionChoice> options = new ArrayList<>();
			for (BattleNext_RestrictionSpec spec : BattleNext_RestrictionRegistry.getInstance().getAll()) {
				if (draft.getRestriction(spec.getId()) == null) {
					options.add(new BattleNext_OptionPicker.OptionChoice(spec.getId(), spec.getDescription()));
				}
			}
			return options;
		}

		@Override
		public int getMaxCount(String id) {
			return 1;
		}

		@Override
		public String getIconPath(String id) {
			return null;
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionPicker.OptionChoice option) {
			tooltip.addTitle(option.getLabel());
			BattleNext_PageLayout.addMutedIdLine(tooltip, option.getId(), 0f);
		}
	}
}
