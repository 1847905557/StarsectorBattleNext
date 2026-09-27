package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_RuleEditor;
import data.scripts.ui.widget.BattleNext_TextField;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_RulesPage implements BattleNext_PageView {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_RuleStore store = new BattleNext_RuleStore();
	private final BattleNext_RuleEditor editor = new BattleNext_RuleEditor(store);
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private LabelAPI statusLabel;
	private String selectedId = "";
	private String status = "";
	private boolean draftOpen;

	public BattleNext_RulesPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		selectedId = "";
		draftOpen = false;
		try {
			store.reload();
		} catch (Exception exception) {
			status = text("load_failed") + ": " + exception.getMessage();
		}
		createContents();
	}

	@Override
	public void createContents() {
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(), 30f),
				panel, text("page_rules"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.HEADER, 2f);
		detailRect.addBorder(panel, BattleNext_UiTheme.HEADER, 2f);
		UIRect footer = regions[2];
		String[] actions = {"add", "save", "back"};
		float width = Math.min(160f, (footer.getWidth() - 20f) / 3f);
		for (int index = 0; index < actions.length; index++) {
			BattleNext_PageLayout.fixedButton(panel,
					new UIRect(footer.getX() + index * (width + 10f), footer.getY(), width, 30f),
					text(actions[index]), actions[index], CutStyle.ALL, true);
		}
		float statusX = 3f * width + 30f;
		if (footer.getWidth() - statusX >= 60f) {
			TooltipMakerAPI footerStatus = panel.createUIElement(footer.getWidth() - statusX, 30f, false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, 5f);
			footerStatus.setHeightSoFar(30f);
			panel.addUIElement(footerStatus).inTL(footer.getX() + statusX, footer.getY());
		}
		refreshContent();
	}

	@Override
	public void refreshContent() {
		closePicker();
		BattleNext_TextField.clearRegisteredFields();
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		TooltipMakerAPI list = listPanel.createUIElement(listRect.getWidth() - 18f,
				listRect.getHeight() - 16f, true);
		list.addTitle(text("rules_heading"));
		if (!store.isLoaded()) list.addPara(text("load_failed") + ": " + store.getLoadError(),
				5f, BattleNext_UiTheme.MUTED);
		for (BattleNext_RuleModel rule : store.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(list, rule.getName(),
					"rules:select:" + rule.getId(), Math.max(80f, listRect.getWidth() - 30f));
		}
		if (store.getAll().isEmpty()) list.addPara(text("rules_empty"), 8f, BattleNext_UiTheme.MUTED);
		listPanel.addUIElement(list).inTL(8f, 8f);
		detailRect.removeLatestSubPanel();
		CustomPanelAPI detail = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (draftOpen) {
			CustomPanelAPI editorPanel = detail.createCustomPanel(detailRect.getWidth() - 16f,
					detailRect.getHeight() - 50f, detail.getPlugin());
			detail.addComponent(editorPanel).inTL(8f, 8f);
			editor.attach(editorPanel);
			if (!selectedId.isEmpty()) {
				ButtonAPI delete = BattleNext_PageLayout.fixedButton(detail,
						new UIRect(8f, detailRect.getHeight() - 36f, 145f, 28f),
						text("delete"), "delete", CutStyle.ALL, true);
				delete.setEnabled(store.isLoaded() && store.get(selectedId) != null);
			}
			return;
		}
		TooltipMakerAPI prompt = detail.createUIElement(detailRect.getWidth() - 18f,
				detailRect.getHeight() - 16f, false);
		prompt.addTitle(text("rules_select"));
		detail.addUIElement(prompt).inTL(8f, 8f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (!(buttonId instanceof String action)) return;
		editor.closePicker();
		if (action.startsWith("rules:select:")) {
			navigation.accept(() -> open(action.substring("rules:select:".length())));
			return;
		}
		if ("add".equals(action)) {
			navigation.accept(() -> open(""));
			return;
		}
		if ("save".equals(action)) {
			save();
			return;
		}
		if ("delete".equals(action) && draftOpen && !selectedId.isEmpty() && store.isLoaded()) {
			BattleNext_RuleModel selected = store.get(selectedId);
			if (selected == null) return;
			deletion.accept(selected.getName() + " [" + selectedId + "]", () -> {
				try {
					store.delete(selectedId);
					selectedId = "";
					draftOpen = false;
					setStatus(text("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(text("save_failed") + ": " + exception.getMessage());
				}
			});
			return;
		}
		if ("back".equals(action)) {
			if (draftOpen) navigation.accept(() -> {
				selectedId = "";
				draftOpen = false;
				refreshContent();
			});
			else backAction.run();
			return;
		}
		if (draftOpen) editor.buttonPressed(action);
	}

	private void open(String id) {
		try {
			BattleNext_RuleModel rule = id.isEmpty() ? null : store.get(id);
			if (!id.isEmpty() && rule == null) throw new IllegalArgumentException(id);
			editor.open(rule);
			selectedId = id;
			draftOpen = true;
			refreshContent();
		} catch (Exception exception) {
			setStatus(text("load_failed") + ": " + exception.getMessage());
		}
	}

	@Override
	public boolean save() {
		if (!draftOpen) return true;
		if (!store.isLoaded()) {
			setStatus(text("load_failed") + ": " + store.getLoadError());
			return false;
		}
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValidPathSegment(id)) {
			setStatus(text("invalid_rules_id"));
			return false;
		}
		for (BattleNext_RuleModel rule : store.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, rule.getId()) && !rule.getId().equals(selectedId)) {
				setStatus(text("duplicate_id"));
				return false;
			}
		}
		if (!editor.serialize()) {
			setStatus(editor.getStatusText());
			return false;
		}
		try {
			store.reload();
			selectedId = id;
			setStatus(text("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(text("load_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override
	public void discard() {
		if (draftOpen) editor.deserialize();
	}

	@Override
	public boolean hasUnsavedChanges() {
		return draftOpen && editor.hasUnsavedChanges();
	}

	@Override
	public String getStatusText() {
		return status;
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return draftOpen && editor.handleGlobalInput(events);
	}

	public void closePicker() {
		editor.closePicker();
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}
}
