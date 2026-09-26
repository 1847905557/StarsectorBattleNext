package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.BattleNext_LoadSource;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_TextField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_RulesPage implements BattleNext_PageView {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_RuleStore store = new BattleNext_RuleStore();
	private final Map<String, ButtonAPI> checks = new LinkedHashMap<>();
	private final Map<String, TextFieldAPI> values = new LinkedHashMap<>();
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private BattleNext_RuleModel draft;
	private BattleNext_RuleModel baseline;
	private TextFieldAPI idField;
	private TextFieldAPI nameField;
	private TextFieldAPI restrictionField;
	private BattleNext_OptionField restrictionPicker;
	private LabelAPI statusLabel;
	private String selectedId = "";
	private String savedSnapshot = "";
	private String status = "";

	public BattleNext_RulesPage(Consumer<Runnable> navigation, BiConsumer<String, Runnable> deletion) {
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		closePicker();
		this.panel = panel;
		selectedId = "";
		draft = null;
		baseline = null;
		try {
			store.reload();
		} catch (Exception exception) {
			status = text("load_failed") + ": " + exception.getMessage();
		}
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), 30f), panel, text("page_rules"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER, 2f);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER, 2f);
		UIRect footer = regions[2];
		String[] actions = {"add", "save", "copy", "delete"};
		float width = Math.min(145f, (footer.getWidth() - 30f) / actions.length);
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(panel,
					new UIRect(footer.getX() + index * (width + 10f), footer.getY(), width, 30f),
					text(actions[index]), actions[index], CutStyle.ALL, true);
		}
		TooltipMakerAPI footerStatus = panel.createUIElement(Math.max(1f, footer.getWidth() - actions.length * (width + 10f)), 30f, false);
		statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
		statusLabel.getPosition().inTL(0f, 5f);
		footerStatus.setHeightSoFar(30f);
		panel.addUIElement(footerStatus).inTL(footer.getX() + actions.length * (width + 10f), footer.getY());
		refreshContent();
	}

	@Override
	public void refreshContent() {
		closePicker();
		BattleNext_TextField.clearRegisteredFields();
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI list = listPanel.createUIElement(listRect.getWidth() - 18f, listRect.getHeight() - 16f, true);
		list.addTitle(text("rules_heading"));
		if (!status.isEmpty()) list.addPara(status, 5f, BattleNext_UiTheme.MUTED);
		for (BattleNext_RuleModel rule : store.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(list, rule.getName(),
					"rules:select:" + rule.getId(), Math.max(80f, listRect.getWidth() - 30f));
		}
		listPanel.addUIElement(list).inTL(8f, 8f);
		detailRect.removeLatestSubPanel();
		if (draft == null) return;
		CustomPanelAPI detail = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI content = detail.createUIElement(detailRect.getWidth() - 18f, detailRect.getHeight() - 16f, true);
		boolean editable = draft.getSourceType() != BattleNext_LoadSource.STANDARD;
		content.addTitle(editable ? text("rules_editable") : text("read_only"));
		checks.clear();
		values.clear();
		idField = field(content, text("rules_id"), draft.getId(), editable);
		nameField = field(content, text("rules_name"), draft.getName(), editable);
		if (editable) {
			content.addPara(text("rules_add_restriction"), 10f, BattleNext_UiTheme.ACCENT);
			restrictionPicker = new BattleNext_OptionField(panel, new RuleRestrictionOptionSource(), BattleNext_OptionField.OptionSelectionMode.SINGLE);
			restrictionField = restrictionPicker.addTo(content, detail,
					Math.max(100f, detailRect.getWidth() - 30f), "");
			BattleNext_PageLayout.button(content, text("add"), "rules:add_restriction", 100f);
		}
		for (int index = 0; index < draft.getRestrictions().size(); index++) {
			BattleNext_RuleRestriction restriction = draft.getRestrictions().get(index);
			BattleNext_RestrictionSpec entry = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
			String label = entry == null ? restriction.getId() + " [" + text("rules_missing_restriction") + "]"
					: entry.getDescription();
			String value = "number".equals(restriction.getType())
					? Double.toString(restriction.getValue()) : String.join(",", restriction.getItems());
			if (!editable || entry == null) {
				content.addPara(label + " [" + (restriction.isEnabled() ? text("enabled") : text("rules_disabled"))
						+ "]: " + value, 8f, BattleNext_UiTheme.TEXT);
				if (editable) {
					if (index > 0) BattleNext_PageLayout.button(content, text("move_up"), "rules:up:" + index, 90f);
					if (index + 1 < draft.getRestrictions().size()) {
						BattleNext_PageLayout.button(content, text("move_down"), "rules:down:" + index, 90f);
					}
					BattleNext_PageLayout.button(content, text("delete"), "rules:remove:" + index, 90f);
				}
				continue;
			}
			ButtonAPI check = content.addCheckbox(Math.max(100f, detailRect.getWidth() - 24f), 24f,
					label, "rules:toggle:" + restriction.getId(), ButtonAPI.UICheckboxSize.SMALL, 8f);
			check.setChecked(restriction.isEnabled());
			checks.put(restriction.getId(), check);
			values.put(restriction.getId(), BattleNext_TextField.addTo(content, detail,
					Math.max(100f, detailRect.getWidth() - 30f), 28f, value));
			if (index > 0) BattleNext_PageLayout.button(content, text("move_up"), "rules:up:" + index, 90f);
			if (index + 1 < draft.getRestrictions().size()) {
				BattleNext_PageLayout.button(content, text("move_down"), "rules:down:" + index, 90f);
			}
			BattleNext_PageLayout.button(content, text("delete"), "rules:remove:" + index, 90f);
		}
		detail.addUIElement(content).inTL(8f, 8f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (!(buttonId instanceof String action)) return;
		if (action.startsWith("rules:select:")) {
			String id = action.substring("rules:select:".length());
			navigation.accept(() -> open(id));
		} else if (action.startsWith("rules:up:") || action.startsWith("rules:down:")
				|| action.startsWith("rules:remove:") || "rules:add_restriction".equals(action)) {
			try {
				BattleNext_RuleModel next = capture();
				List<BattleNext_RuleRestriction> restrictions = new ArrayList<>(next.getRestrictions());
				if ("rules:add_restriction".equals(action)) {
					String id = restrictionField.getText().trim();
					BattleNext_RestrictionSpec entry = BattleNext_RestrictionRegistry.getInstance().get(id);
					if (entry == null) throw new IllegalArgumentException(text("rules_missing_restriction") + ": " + id);
					if (next.getRestriction(id) != null) throw new IllegalArgumentException(text("duplicate_id") + ": " + id);
					restrictions.add(new BattleNext_RuleRestriction(id, entry.getType()));
				} else {
					int index = Integer.parseInt(action.substring(action.lastIndexOf(':') + 1));
					if (action.startsWith("rules:remove:")) restrictions.remove(index);
					else {
						int target = action.startsWith("rules:up:") ? index - 1 : index + 1;
						if (target < 0 || target >= restrictions.size()) return;
						java.util.Collections.swap(restrictions, index, target);
					}
				}
				next.setRestrictions(restrictions);
				draft = next;
				refreshContent();
			} catch (Exception exception) {
				setStatus(exception.getMessage());
			}
		} else if ("add".equals(action)) {
			navigation.accept(() -> {
				draft = new BattleNext_RuleModel();
				baseline = null;
				selectedId = "";
				refreshContent();
				savedSnapshot = serializeState();
			});
		} else if ("copy".equals(action) && draft != null) {
			navigation.accept(() -> {
				try {
					BattleNext_RuleModel source = capture();
					String id = source.getId() + "_copy";
					for (int number = 2; store.get(id) != null; number++) id = source.getId() + "_copy" + number;
					source.setId(id);
					source.setSourceType(BattleNext_LoadSource.CREATED);
					draft = source;
					baseline = null;
					selectedId = "";
					refreshContent();
					savedSnapshot = serializeState();
				} catch (Exception exception) {
					setStatus(exception.getMessage());
				}
			});
		} else if ("save".equals(action)) {
			save();
		} else if ("delete".equals(action) && !selectedId.isEmpty() && baseline != null
				&& baseline.getSourceType() == BattleNext_LoadSource.CREATED) {
			deletion.accept(baseline.getName() + " [" + selectedId + "]", () -> {
				try {
					store.delete(selectedId);
					draft = null;
					baseline = null;
					selectedId = "";
					setStatus(text("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception.getMessage());
				}
			});
		}
	}

	@Override
	public boolean save() {
		if (draft == null || draft.getSourceType() == BattleNext_LoadSource.STANDARD) return true;
		try {
			BattleNext_RuleModel next = capture();
			if (!BattleNext_EntryId.isValidPathSegment(next.getId())) throw new IllegalArgumentException(text("invalid_rules_id"));
			if (!selectedId.isEmpty() && !next.getId().equals(selectedId)) {
				throw new IllegalArgumentException(text("rules_id_locked"));
			}
			if (store.get(next.getId()) != null && !next.getId().equals(selectedId)) {
				throw new IllegalArgumentException(text("duplicate_id"));
			}
			store.save(next);
			selectedId = next.getId();
			baseline = copy(next);
			draft = next;
			savedSnapshot = serializeState();
			setStatus(text("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(text("save_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override
	public void discard() {
		try {
			draft = baseline == null ? null : copy(baseline);
			refreshContent();
			savedSnapshot = serializeState();
		} catch (Exception exception) {
			setStatus(exception.getMessage());
		}
	}

	@Override
	public boolean hasUnsavedChanges() {
		return draft != null && draft.getSourceType() != BattleNext_LoadSource.STANDARD
				&& !serializeState().equals(savedSnapshot);
	}

	@Override
	public String getStatusText() {
		return status;
	}

	private void open(String id) {
		try {
			selectedId = id;
			baseline = copy(store.get(id));
			draft = copy(baseline);
			refreshContent();
			savedSnapshot = serializeState();
		} catch (Exception exception) {
			setStatus(text("load_failed") + ": " + exception.getMessage());
		}
	}

	private BattleNext_RuleModel copy(BattleNext_RuleModel source) throws Exception {
		BattleNext_RuleModel result = new BattleNext_RuleModel();
		result.fromJson(source.toJson());
		result.setSourceType(source.getSourceType());
		result.setSourcePath(source.getSourcePath());
		return result;
	}

	private BattleNext_RuleModel capture() throws Exception {
		if (draft.getSourceType() == BattleNext_LoadSource.STANDARD) return copy(draft);
		BattleNext_RuleModel next = new BattleNext_RuleModel(idField.getText(), nameField.getText());
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>();
		for (BattleNext_RuleRestriction current : draft.getRestrictions()) {
			String id = current.getId();
			BattleNext_RestrictionSpec entry = BattleNext_RestrictionRegistry.getInstance().get(id);
			if (entry == null) {
				restrictions.add(current);
				continue;
			}
			BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id, entry.getType());
			restriction.setEnabled(checks.get(id).isChecked());
			String value = values.get(id).getText().trim();
			if ("number".equals(entry.getType())) {
				restriction.setValue(Double.parseDouble(value));
			} else {
				List<String> items = new ArrayList<>();
				if (!value.isEmpty()) for (String item : value.split(",")) items.add(item.trim());
				restriction.setItems(items);
			}
			restrictions.add(restriction);
		}
		next.setRestrictions(restrictions);
		return next;
	}

	private TextFieldAPI field(TooltipMakerAPI content, String label, String value, boolean editable) {
		content.addPara(label, 6f, BattleNext_UiTheme.MUTED);
		if (!editable) {
			content.addPara(value, 2f, BattleNext_UiTheme.TEXT);
			return null;
		}
		return BattleNext_TextField.addTo(content, panel, Math.max(100f, detailRect.getWidth() - 30f), 28f, value);
	}

	private String serializeState() {
		if (idField == null) return "";
		StringBuilder value = new StringBuilder(idField.getText()).append('|').append(nameField.getText());
		for (BattleNext_RuleRestriction restriction : draft.getRestrictions()) {
			String id = restriction.getId();
			value.append('|').append(id);
			if (checks.containsKey(id)) {
				value.append(':').append(checks.get(id).isChecked()).append(':').append(values.get(id).getText());
			} else {
				value.append(':').append(restriction.isEnabled()).append(':').append(restriction.getType())
						.append(':').append(restriction.getValue()).append(':').append(restriction.getItems());
			}
		}
		return value.toString();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return restrictionPicker != null && restrictionPicker.handleGlobalInput(events);
	}

	public void closePicker() {
		if (restrictionPicker != null) restrictionPicker.close();
	}

	private class RuleRestrictionOptionSource implements BattleNext_OptionField.OptionFieldChoiceSource {

		@Override
		public List<BattleNext_OptionField.OptionFieldChoice> getOptions() {
			List<BattleNext_OptionField.OptionFieldChoice> options = new ArrayList<>();
			for (BattleNext_RestrictionSpec entry : BattleNext_RestrictionRegistry.getInstance().getAll()) {
				if (draft.getRestriction(entry.getId()) == null) {
					options.add(new BattleNext_OptionField.OptionFieldChoice(entry.getId(), entry.getDescription()));
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
		public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionField.OptionFieldChoice option) {
			tooltip.addPara(option.getLabel() + " [" + option.getId() + "]", 0f, BattleNext_UiTheme.TEXT);
		}
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}
}
