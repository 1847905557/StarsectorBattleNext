package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_RuleEditor;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_RulesPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");

	private static final float RULE_LIST_ERROR_TOP_GAP = 5f;
	private static final float RULE_LIST_EMPTY_TOP_GAP = 8f;

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_RuleStore store = new BattleNext_RuleStore();
	private final BattleNext_RuleEditor editor = new BattleNext_RuleEditor(store);

	private String selectedId = "";
	private boolean draftOpen;

	public BattleNext_RulesPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	protected void bindPageInput() {
		editor.setInputRouter(inputRouter);
	}

	@Override
	protected void loadPageState() {
		selectedId = "";
		draftOpen = false;
		try {
			store.reload();
		} catch (Exception exception) {
			status = strings.get("load_failed") + ": " + exception.getMessage();
		}
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_rules");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"add", "save", "back"};
	}

	@Override
	protected String promptText() {
		return ruleStrings.get("select_prompt");
	}

	@Override
	protected String listTitle() {
		return ruleStrings.get("management_heading");
	}

	@Override
	protected boolean isListLoaded() {
		return store.isLoaded();
	}

	@Override
	protected String listError() {
		return store.getLoadError();
	}

	@Override
	protected boolean isListEmpty() {
		return store.getAll().isEmpty();
	}

	@Override
	protected String emptyListText() {
		return ruleStrings.get("empty");
	}

	@Override
	protected float listErrorGap() {
		return RULE_LIST_ERROR_TOP_GAP;
	}

	@Override
	protected float listEmptyGap() {
		return RULE_LIST_EMPTY_TOP_GAP;
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		for (BattleNext_RuleModel rule : store.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, rule.getName(),
					"rules:select:" + rule.getId(), BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {
		editor.attach(contentPanel);
	}

	@Override
	protected boolean isDetailOpen() {
		return draftOpen;
	}

	@Override
	protected boolean hasSelection() {
		return !selectedId.isEmpty();
	}

	@Override
	protected boolean isSaveDataLoaded() {
		return store.isLoaded();
	}

	@Override
	protected boolean canDelete() {
		return store.isLoaded() && store.get(selectedId) != null;
	}

	@Override
	protected void clearPageOverlays() {
		editor.closePicker();
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if (!(buttonId instanceof String action)) return;
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
			deletion.accept(selected.getName(), () -> {
				try {
					store.delete(selectedId);
					selectedId = "";
					draftOpen = false;
					setStatus(strings.get("deleted"));
					refreshContent();
				} catch (Exception exception) {
					setStatus(strings.get("save_failed") + ": " + exception.getMessage());
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

	@Override
	protected boolean isPageDirty() {
		return draftOpen && editor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		if (!draftOpen) return true;
		if (!store.isLoaded()) {
			setStatus(strings.get("load_failed") + ": " + store.getLoadError());
			return false;
		}
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValidPathSegment(id)) {
			setStatus(ruleStrings.get("invalid_id"));
			return false;
		}
		for (BattleNext_RuleModel rule : store.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, rule.getId()) && !rule.getId().equals(selectedId)) {
				setStatus(strings.get("duplicate_id"));
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
			setStatus(strings.get("saved"));
			refreshContent();
			return true;
		} catch (Exception exception) {
			setStatus(strings.get("load_failed") + ": " + exception.getMessage());
			return false;
		}
	}

	@Override
	protected void discardPage() {
		if (draftOpen) editor.deserialize();
	}

	@Override
	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return draftOpen && editor.handleGlobalInput(events);
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
			setStatus(strings.get("load_failed") + ": " + exception.getMessage());
		}
	}
}
