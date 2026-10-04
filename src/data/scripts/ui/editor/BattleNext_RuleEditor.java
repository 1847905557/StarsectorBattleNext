package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.*;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import org.json.JSONException;

import java.io.IOException;
import java.util.*;

public class BattleNext_RuleEditor extends BattleNext_EditorView {

	private static final float[] COLUMN_SHARES = {0.3f, 0.34f, 0.16f, 0.2f};
	private static final int DESCRIPTION_COLUMN = 0;
	private static final int VALUE_COLUMN = 1;
	private static final int ENABLED_COLUMN = 2;
	private static final int ACTIONS_COLUMN = 3;

	private static final float RESTRICTION_TOOLTIP_WIDTH = 320f;
	private static final float RESTRICTION_TOOLTIP_META_GAP = 5f;

	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_RuleStore store;
	private final Map<String, ButtonAPI> enabledCheckboxes = new LinkedHashMap<>();
	private final Map<String, TextFieldAPI> valueFields = new LinkedHashMap<>();

	private BattleNext_RuleModel draft;
	private BattleNext_RuleModel baseline;
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private BattleNext_OptionPicker restrictionPicker;
	private BattleNext_TableWidget tableWidget;

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
		public int getMaxStack(String id) {
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

	public BattleNext_RuleEditor(BattleNext_RuleStore store) {
		this.store = store;
	}

	public void open(BattleNext_RuleModel rule) throws JSONException {
		beginSession();
		baseline = rule == null ? null : copy(rule);
		draft = rule == null ? new BattleNext_RuleModel() : copy(rule);
		idField = null;
		nameField = null;
		statusText = "";
	}

	@Override
	protected void buildEditorContent() {
		enabledCheckboxes.clear();
		valueFields.clear();
		createColumns("metadata", "restrictions", (metadataContent, columnWidth) -> {
			metadataContent.addTitle(ruleStrings.get("editor_title"));
			idField = createTextField("id", panel, metadataContent, columnWidth, ruleStrings.get("id"), draft.getId(), true, metadataContent);
			nameField = createTextField("name", panel, metadataContent, columnWidth, ruleStrings.get("name"), draft.getName(), true, metadataContent);
		}, (rowsContent, columnWidth) -> {
			String[] headers = {ruleStrings.get("restriction"), ruleStrings.get("value_or_items"), strings.get("enabled"), strings.get("actions")};
			tableWidget = createTable("rows", rowsContent, columnWidth, ruleStrings.get("restrictions"), headers, COLUMN_SHARES,
					draft.getRestrictions().size(),
					(index, rowWidth, cells, content) -> addRestrictionRow(index, rowWidth, cells, content, rowsContent),
					(rowWidth, content) -> restrictionPicker = createPicker(panel, content, rowWidth,
							ruleStrings.get("add_restriction"), new RestrictionChoiceSource(),
							BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, this::addRestrictions, content, rowsContent));
		});
	}

	private void addRestrictionRow(int index, float rowWidth, UIRect[] cells,
			TooltipMakerAPI rowsContent, TooltipMakerAPI restrictionsContent) {
		BattleNext_RuleRestriction restriction = draft.getRestrictions().get(index);
		BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
		String description = spec == null ? ruleStrings.get("missing_restriction") : spec.getDescription();
		CustomPanelAPI rowPanel = panel.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, panel.getPlugin());
		new UIRect(0f, 0f, rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBorder(rowPanel,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		createTextCell(rowPanel, cells[DESCRIPTION_COLUMN], description, false,
				new RestrictionTooltip(restriction.getId(), restriction.getType(), description), TooltipMakerAPI.TooltipLocation.RIGHT, false);
		if (spec == null) {
			createTextCell(rowPanel, cells[VALUE_COLUMN], displayValue(restriction), false, null, TooltipMakerAPI.TooltipLocation.RIGHT, false);
		} else {
			TextFieldAPI valueField = createCellField(List.of("restriction", restriction.getId()), rowPanel,
					cells[VALUE_COLUMN], displayValue(restriction), rowsContent, restrictionsContent);
			valueFields.put(restriction.getId(), valueField);
			ButtonAPI enabledCheckbox = createCellCheckbox(List.of("enabled", restriction.getId()), rowPanel,
					cells[ENABLED_COLUMN], "rule:enabled:" + index, restriction.isEnabled(), true, null);
			enabledCheckboxes.put(restriction.getId(), enabledCheckbox);
		}
		createActions(rowPanel, cells[ACTIONS_COLUMN],
				new EditorAction("^", "rule:up:" + index, index > 0),
				new EditorAction("V", "rule:down:" + index, index + 1 < draft.getRestrictions().size()),
				new EditorAction("X", "rule:remove:" + index, true));
		rowsContent.addCustom(rowPanel, 0f);
	}

	private void addRestrictions(List<String> ids) {
		if (ids.isEmpty()) return;
		try {
			captureInputs(false);
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

	private void setStatus(String value) {
		statusText = value;
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (!(buttonId instanceof String action)) return;
		if (!action.startsWith("rule:up:") && !action.startsWith("rule:down:")
				&& !action.startsWith("rule:remove:")) return;
		try {
			captureInputs(false);
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

	@Override
	protected String draftState() {
		if (idField == null) return "";
		StringBuilder state = new StringBuilder();
		append(state, idField.getText());
		append(state, nameField.getText());
		for (BattleNext_RuleRestriction restriction : draft.getRestrictions()) {
			append(state, restriction.getId());
			if (enabledCheckboxes.containsKey(restriction.getId())) {
				append(state, Boolean.toString(enabledCheckboxes.get(restriction.getId()).isChecked()));
				append(state, valueFields.get(restriction.getId()).getText());
			} else {
				append(state, Boolean.toString(restriction.isEnabled()));
				append(state, displayValue(restriction));
			}
		}
		return state.toString();
	}

	public String getDraftId() {
		return idField.getText().trim();
	}

	@Override
	protected boolean submitDraft() {
		if (!validateName(nameField.getText(), strings.get("name_required"))) return false;
		try {
			captureInputs(true);
			if (!BattleNext_EntryId.isValidPathSegment(draft.getId())) {
				setStatus(ruleStrings.get("invalid_id"));
				return false;
			}
			store.saveEdited(draft, baseline == null ? "" : baseline.getId());
			baseline = copy(draft);
			setStatus(strings.get("saved"));
			return true;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.error(BattleNext_RuleEditor.class, "rule.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).build(), exception);
			setStatus(failureStatus(exception));
			return false;
		}
	}

	@Override
	protected void captureDraftInputs() {
		captureInputs(false);
	}

	private void captureInputs(boolean validate) {
		if (idField == null) return;
		draft.setId(idField.getText());
		draft.setName(nameField.getText());
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>();
		for (BattleNext_RuleRestriction current : draft.getRestrictions()) {
			if (!enabledCheckboxes.containsKey(current.getId())) {
				restrictions.add(current);
				continue;
			}
			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(current.getId(), current.getType());
			restriction.setEnabled(enabledCheckboxes.get(current.getId()).isChecked());
			String input = valueFields.get(current.getId()).getText().trim();
			try {
				if ("number".equals(current.getType())) {
					restriction.setValue(Double.parseDouble(input));
				} else if (!input.isEmpty()) {
					List<String> items = new ArrayList<>();
					for (String item : input.split(",", -1)) items.add(item.trim());
					restriction.setItems(items);
				}
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_RuleEditor.class, "rule.restriction.input.rejected",
				BattleNext_Logger.fields().put("rule_id", draft.getId()).put("restriction_id", current.getId()).put("input", input).put("type", current.getType()).put("validate", validate).put("fallback", current).build(), exception);
				if (validate) throw new IllegalArgumentException(current.getId() + ": " + exception.getMessage(), exception);
				if ("number".equals(current.getType())) {
					restriction.setValue(current.getValue());
				} else {
					restriction.setItems(current.getItems());
				}
			}
			restrictions.add(restriction);
		}
		draft.setRestrictions(restrictions);
	}

	@Override
	protected void restoreDraft() {
		try {
			draft = baseline == null ? new BattleNext_RuleModel() : copy(baseline);
			idField = null;
			nameField = null;
		} catch (JSONException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
