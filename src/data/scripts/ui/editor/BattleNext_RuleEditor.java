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
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import org.json.JSONException;

import java.awt.Color;
import java.io.IOException;
import java.util.*;

public class BattleNext_RuleEditor implements BattleNext_EditorView {

	private static final float TITLE_HEIGHT = 24f;

	private static final float HEADER_HEIGHT = 18f;
	private static final float ROW_HEIGHT = 38f;
	private static final float FIELD_HEIGHT = 28f;
	private static final float COLUMN_GAP = 8f;
	private static final float SIDE_MARGIN = 12f;
	private static final float[] COLUMN_SHARES = {0.3f, 0.34f, 0.16f, 0.2f};

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final BattleNext_RuleStore store;
	private final Map<String, ButtonAPI> enabledFields = new LinkedHashMap<>();
	private final Map<String, TextFieldAPI> valueFields = new LinkedHashMap<>();
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
	private UIRect rowsArea;
	private UIRect rowsRect;

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
		if (restrictionPicker != null) restrictionPicker.close();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return restrictionPicker != null && restrictionPicker.handleGlobalInput(events);
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		createContents();
		savedSnapshot = serializeState();
	}

	@Override
	public void createContents() {
		contentRect = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		refreshContent();
	}

	@Override
	public void refreshContent() {
		closePicker();
		BattleNext_TextField.clearRegisteredFields();
		contentRect.removeLatestSubPanel();
		CustomPanelAPI contentPanel = contentRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		float gap = 10f;
		float usableWidth = Math.max(2f, contentRect.getWidth() - gap);
		float leftWidth = (float) Math.floor(usableWidth * 0.45f);
		float rightWidth = usableWidth - leftWidth;
		TooltipMakerAPI details = contentPanel.createUIElement(leftWidth, contentRect.getHeight(), true);
		details.addTitle(text("rules_editable"));
		statusLabel = details.addPara(status, 4f, BattleNext_UiTheme.MUTED);
		idField = field(details, panel, text("rules_id"), draft.getId(), leftWidth - 12f, details);
		nameField = field(details, panel, text("rules_name"), draft.getName(), leftWidth - 12f, details);
		contentPanel.addUIElement(details).inTL(0f, 0f);

		TooltipMakerAPI restrictions = contentPanel.createUIElement(rightWidth, contentRect.getHeight(), true);
		restrictions.addTitle(text("rules_restrictions"));
		contentPanel.addUIElement(restrictions).inTL(leftWidth + gap, 0f);
		float tableWidth = Math.max(2f, rightWidth - SIDE_MARGIN);
		float tableY = TITLE_HEIGHT;
		float tableHeight = Math.max(1f, contentRect.getHeight() - tableY);
		CustomPanelAPI tablePanel = panel.createCustomPanel(rightWidth, tableHeight, panel.getPlugin());
		restrictions.addComponent(tablePanel).inTL(0f, tableY);
		TooltipMakerAPI headerBackground = tablePanel.createUIElement(tableWidth, HEADER_HEIGHT, false);
		LabelAPI headerStrip = headerBackground.addSectionHeading("", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID, tableWidth, 0f);
		headerStrip.getPosition().inTL(0f, 0f);
		headerBackground.setHeightSoFar(HEADER_HEIGHT);
		tablePanel.addUIElement(headerBackground).inTL(SIDE_MARGIN, 0f);
		UIRect[] headings = columns(tableWidth, HEADER_HEIGHT);
		String[] labels = {text("rules_restriction"), text("rules_value_items"), text("enabled"), text("actions")};
		for (int index = 0; index < headings.length; index++) {
			UIRect cell = headings[index];
			addLabel(tablePanel, new UIRect(SIDE_MARGIN + cell.getX(), 0f,
					cell.getWidth(), cell.getHeight()), labels[index], BattleNext_UiTheme.TEXT, true);
		}

		enabledFields.clear();
		valueFields.clear();
		float contentHeight = (draft.getRestrictions().size() + 1) * ROW_HEIGHT + 10f;
		float availableHeight = Math.max(1f, tableHeight - HEADER_HEIGHT);
		float visibleHeight = Math.min(availableHeight, contentHeight);
		rowsArea = new UIRect(SIDE_MARGIN - 5f, HEADER_HEIGHT, tableWidth, availableHeight);
		rowsRect = new UIRect(rowsArea.getX(), rowsArea.getY(), rowsArea.getWidth(), visibleHeight);
		CustomPanelAPI rowsPanel = rowsRect.createSubPanel(tablePanel, panel.getPlugin()).getPanel();
		TooltipMakerAPI rows = rowsPanel.createUIElement(tableWidth, visibleHeight, true);
		for (int index = 0; index < draft.getRestrictions().size(); index++) {
			addRestrictionRow(index, draft.getRestrictions().get(index), rows, tableWidth, restrictions);
		}
		restrictionPicker = new BattleNext_OptionPicker(panel, new RestrictionChoiceSource(),
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, this::addRestrictions);
		restrictionPicker.addTo(rows, panel, Math.min(190f, tableWidth), text("rules_add_restriction"), rows, restrictions);
		rowsPanel.addUIElement(rows).inTL(0f, 0f);
	}

	private void addRestrictionRow(int index, BattleNext_RuleRestriction restriction,
			TooltipMakerAPI content, float tableWidth, TooltipMakerAPI restrictions) {
		BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
		String description = spec == null ? text("rules_missing_restriction") : spec.getDescription();
		CustomPanelAPI row = Global.getSettings().createCustom(tableWidth, ROW_HEIGHT, panel.getPlugin());
		new UIRect(0f, 0f, tableWidth, ROW_HEIGHT).addBoarder(row, BattleNext_UiTheme.HEADER, 2f);
		UIRect[] cells = columns(tableWidth, ROW_HEIGHT);
		TooltipMakerAPI descriptionHost = row.createUIElement(Math.max(1f, cells[0].getWidth() - 4f),
				ROW_HEIGHT, false);
		LabelAPI descriptionLabel = descriptionHost.addPara(
				descriptionHost.shortenString(description, Math.max(1f, cells[0].getWidth() - 10f)), 0f,
				spec == null ? BattleNext_UiTheme.MUTED : BattleNext_UiTheme.TEXT);
		descriptionLabel.getPosition().inTL(3f,
				Math.max(0f, (ROW_HEIGHT - descriptionLabel.getPosition().getHeight()) * 0.5f));
		descriptionHost.addTooltipToPrevious(new RestrictionTooltip(restriction.getId(), restriction.getType(), description),
				TooltipMakerAPI.TooltipLocation.RIGHT);
		descriptionHost.setHeightSoFar(ROW_HEIGHT);
		row.addUIElement(descriptionHost).inTL(cells[0].getX(), cells[0].getY());
		if (spec == null) {
			TooltipMakerAPI missing = row.createUIElement(cells[1].getWidth() - 8f, ROW_HEIGHT, false);
			missing.addPara(missing.shortenString(displayValue(restriction), cells[1].getWidth() - 12f),
					0f, BattleNext_UiTheme.MUTED);
			row.addUIElement(missing).inTL(cells[1].getX() + 3f, 0f);
		} else {
			addField(cells[1], row, displayValue(restriction), restriction.getId(), content, restrictions);
			TooltipMakerAPI enabledHost = row.createUIElement(cells[2].getWidth(), ROW_HEIGHT, false);
			ButtonAPI enabled = enabledHost.addCheckbox(cells[2].getWidth(), ROW_HEIGHT, "",
					"rule:enabled:" + index, ButtonAPI.UICheckboxSize.SMALL, 0f);
			enabled.setChecked(restriction.isEnabled());
			enabledFields.put(restriction.getId(), enabled);
			enabledHost.setHeightSoFar(ROW_HEIGHT);
			row.addUIElement(enabledHost).inTL(cells[2].getX(), 0f);
		}
		float buttonWidth = Math.max(22f, (cells[3].getWidth() - 8f) / 3f);
		BattleNext_PageLayout.fixedButton(row, new UIRect(cells[3].getX(), 5f, buttonWidth, 28f),
				"^", "rule:up:" + index, CutStyle.ALL, false).setEnabled(index > 0);
		BattleNext_PageLayout.fixedButton(row, new UIRect(cells[3].getX() + buttonWidth, 5f,
				buttonWidth, 28f), "V", "rule:down:" + index, CutStyle.ALL, false)
				.setEnabled(index + 1 < draft.getRestrictions().size());
		BattleNext_PageLayout.fixedButton(row, new UIRect(cells[3].getX() + buttonWidth * 2f, 5f,
				buttonWidth, 28f), "X", "rule:remove:" + index, CutStyle.ALL, false);
		content.addCustom(row, 0f);
	}

	private TextFieldAPI addField(UIRect rect, CustomPanelAPI owner, String value, String key,
			TooltipMakerAPI... viewports) {
		float width = Math.max(40f, rect.getWidth() - 10f);
		TooltipMakerAPI maker = owner.createUIElement(width, FIELD_HEIGHT, false);
		TextFieldAPI field = BattleNext_TextField.addTo(maker, owner, width, FIELD_HEIGHT, value, viewports);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		maker.setHeightSoFar(FIELD_HEIGHT);
		owner.addUIElement(maker).inTL(rect.getX() + 3f, rect.getY() + (rect.getHeight() - FIELD_HEIGHT) * 0.5f);
		valueFields.put(key, field);
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

	private void addLabel(CustomPanelAPI host, UIRect rect, String value, Color color, boolean compactFont) {
		TooltipMakerAPI maker = host.createUIElement(Math.max(1f, rect.getWidth() - 4f), rect.getHeight(), false);
		if (compactFont) maker.setParaSmallInsignia();
		LabelAPI label = maker.addPara(maker.shortenString(value, Math.max(1f, rect.getWidth() - 10f)), 0f, color);
		label.getPosition().inTL(3f, Math.max(0f, (rect.getHeight() - label.getPosition().getHeight()) * 0.5f));
		maker.setHeightSoFar(rect.getHeight());
		host.addUIElement(maker).inTL(rect.getX(), rect.getY());
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
			setStatus(text("saved"));
			return true;
		} catch (IOException | JSONException | RuntimeException exception) {
			setStatus(text("save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override
	public void deserialize() {
		try {
			draft = baseline == null ? new BattleNext_RuleModel() : copy(baseline);
			refreshContent();
			savedSnapshot = serializeState();
		} catch (JSONException exception) {
			setStatus(text("load_failed") + ": " + exception.getMessage());
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
		host.addPara(label, 6f, BattleNext_UiTheme.MUTED);
		return BattleNext_TextField.addTo(host, owner, Math.max(90f, width), 28f, value, viewports);
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

	private String text(String key) {
		return strings.get(key);
	}

	private class RestrictionTooltip extends BaseTooltipCreator {

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
			return 320f;
		}

		@Override
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
			tooltip.addPara(description, 0f, BattleNext_UiTheme.TEXT);
			tooltip.addPara(id + "  [" + type + "]", 5f, BattleNext_UiTheme.MUTED);
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
			tooltip.addPara(option.getLabel() + " [" + option.getId() + "]", 0f, BattleNext_UiTheme.TEXT);
		}
	}
}
