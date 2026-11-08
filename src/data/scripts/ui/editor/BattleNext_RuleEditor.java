package data.scripts.ui.editor;

import com.fs.starfarer.api.ui.*;
import data.model.rule.BattleNext_RuleModel;
import data.model.BattleNext_BaseStore.EditRejection;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionRegistry.BattleNext_RestrictionSpec;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.provider.BattleNext_RestrictionSourceProvider;
import org.json.JSONException;

import java.io.IOException;
import java.util.*;

public class BattleNext_RuleEditor extends BattleNext_EditorView {

	private static final float[] COLUMN_SHARES = {0.3f, 0.34f, 0.16f, 0.2f};
	private static final int NAME_COLUMN = 0;
	private static final int VALUE_COLUMN = 1;
	private static final int ENABLED_COLUMN = 2;
	private static final int ACTIONS_COLUMN = 3;

	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");

	private final BattleNext_RuleStore store;

	private BattleNext_RuleModel draft;
	private BattleNext_RuleModel baseline;
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private BattleNext_OptionPicker restrictionPicker;
	private BattleNext_TableWidget tableWidget;

	private static class RestrictionTooltip extends BaseTooltipCreator {

		private final BattleNext_EquipmentTooltip.Content content;

		private RestrictionTooltip(BattleNext_EquipmentTooltip.Content content) {
			this.content = content;
		}

		@Override
		public float getTooltipWidth(Object tooltipParam) {
			return BattleNext_EquipmentTooltip.WIDTH;
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			BattleNext_EquipmentTooltip.render(tooltip, content);
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
		createColumns("metadata", "restrictions", (metadataContent, columnWidth) -> {
			metadataContent.addTitle(ruleStrings.get("editor_title"));
			UIRect fieldArea = BattleNext_PageLayout.editorFieldArea(columnWidth);
			idField = createTextField("id", panel, metadataContent, fieldArea, ruleStrings.get("id"), draft.getId(), true, metadataContent);
			nameField = createTextField("name", panel, metadataContent, fieldArea, ruleStrings.get("name"), draft.getName(), true, metadataContent);
		}, (rowsContent, columnWidth) -> {
			String[] headers = {ruleStrings.get("restriction"), ruleStrings.get("value_or_items"), strings.get("enabled"), strings.get("actions")};
			tableWidget = createTable("rows", rowsContent, columnWidth, ruleStrings.get("restrictions"), headers, COLUMN_SHARES,
					draft.getRestrictionsReadonly().size(),
					(index, rowWidth, cells, content) -> addRestrictionRow(index, rowWidth, cells, content, rowsContent),
					(rowWidth, content) -> restrictionPicker = createPicker(panel, content, rowWidth,
							ruleStrings.get("add_restriction"), new BattleNext_RestrictionSourceProvider(() -> draft),
							BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, this::addRestrictions, content, rowsContent));
		});
	}

	private void addRestrictionRow(int index, float rowWidth, UIRect[] cells,
			TooltipMakerAPI rowsContent, TooltipMakerAPI restrictionsContent) {
		BattleNext_RuleRestriction restriction = draft.getRestrictionsReadonly().get(index);
		BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
		String name = spec == null ? ruleStrings.get("missing_restriction") : spec.name();
		BattleNext_EquipmentTooltip.Content tooltip = new BattleNext_EquipmentTooltip.Content(name,
				restriction.getId() + "  [" + restriction.getType() + "]", spec == null ? "" : spec.description(), List.of());
		CustomPanelAPI rowPanel = panel.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, panel.getPlugin());
		new UIRect(0f, 0f, rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT).addBorder(rowPanel,
				BattleNext_UiTheme.PANEL, BattleNext_PageLayout.TABLE_ROW_BORDER_THICKNESS);
		createTextCell(rowPanel, cells[NAME_COLUMN], name, false,
				new RestrictionTooltip(tooltip), TooltipMakerAPI.TooltipLocation.RIGHT, false);
		if (spec == null) {
			createTextCell(rowPanel, cells[VALUE_COLUMN], displayValue(restriction), false, null, TooltipMakerAPI.TooltipLocation.RIGHT, false);
		} else {
			createCellField(List.of("restriction", restriction.getId()), rowPanel,
					cells[VALUE_COLUMN], displayValue(restriction), rowsContent, restrictionsContent);
			createCellCheckbox(List.of("enabled", restriction.getId()), rowPanel,
					cells[ENABLED_COLUMN], "rule:enabled:" + index, restriction.isEnabled(), true, null);
		}
		createActions(rowPanel, cells[ACTIONS_COLUMN],
				new EditorAction("^", "rule:up:" + index, index > 0),
				new EditorAction("V", "rule:down:" + index, index + 1 < draft.getRestrictionsReadonly().size()),
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

		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>(draft.getRestrictionsReadonly());
		for (String id : ids) {
			BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(id);
			if (spec == null || draft.getRestriction(id) != null) continue;
			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id, spec.type());
			restriction.setEnabled(true);
			restrictions.add(restriction);
		}

		draft.setRestrictions(restrictions);
		refreshContent();
	}

	private String displayValue(BattleNext_RuleRestriction restriction) {
		return "number".equals(restriction.getType()) ? Double.toString(restriction.getValue())
				: String.join(",", restriction.getItemsReadonly());
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
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>(draft.getRestrictionsReadonly());
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
		for (BattleNext_RuleRestriction restriction : draft.getRestrictionsReadonly()) {
			append(state, restriction.getId());
			append(state, Boolean.toString(checkValue(List.of("enabled", restriction.getId()), restriction.isEnabled())));
			append(state, fieldText(List.of("restriction", restriction.getId()), displayValue(restriction)));
		}

		return state.toString();
	}

	public String getDraftId() {
		return idField.getText().trim();
	}

	@Override
	protected boolean submitDraft() {
		validateName(ruleStrings.get("editor_title") + " / " + idField.getText().trim(), strings.get("name"), nameField.getText(), strings.get("name_required"));
		saveErrors.id(ruleStrings.get("editor_title"), ruleStrings.get("id"), idField.getText());

		try {
			captureInputs(true);
			for (EditRejection rejection : store.editRejections(draft, baseline == null ? "" : baseline.getId())) {
				saveErrors.rejection(ruleStrings.get("editor_title"), ruleStrings.get("id"), rejection);
			}
			if (!saveErrors.isEmpty()) return false;

			String sourceId = baseline == null ? "" : baseline.getId();
			BattleNext_RuleModel nextBaseline = copy(draft);
			return persist(() -> {
				store.saveEdited(nextBaseline, sourceId);
				return true;
			}, () -> {
				baseline = nextBaseline;
				setStatus(strings.get("saved"));
			});
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_RuleEditor.class, "rule.editor.commit.failed",
				BattleNext_Logger.fields().put("draft", draft).put("input_state", serializeState()).build(), exception);
			setStatus(failureStatus(draft.getId(), exception));
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
		for (BattleNext_RuleRestriction current : draft.getRestrictionsReadonly()) {
			if (BattleNext_RestrictionRegistry.getInstance().get(current.getId()) == null) {
				restrictions.add(current);
				continue;
			}

			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(current.getId(), current.getType());
			restriction.setEnabled(checkValue(List.of("enabled", current.getId()), current.isEnabled()));
			String text = fieldText(List.of("restriction", current.getId()), displayValue(current));
			String input = text.trim();
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
				if (validate) {
					String reason = "number".equals(current.getType()) ? strings.get("error_nonnegative")
							: strings.get(List.of(input.split(",", -1)).stream().anyMatch(String::isBlank) ? "error_list_empty" : "error_list_duplicate");
					saveErrors.field(ruleStrings.get("editor_title") + " / " + draft.getId(), current.getId(), text, reason);
				}
				if ("number".equals(current.getType())) {
					restriction.setValue(current.getValue());
				} else {
					restriction.setItems(current.getItemsReadonly());
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
