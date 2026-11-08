package data.scripts.ui.page;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.editor.BattleNext_RuleEditor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_RulesPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");

	private static final float RULE_LIST_ERROR_TOP_GAP = 5f;
	private static final float RULE_LIST_EMPTY_TOP_GAP = 8f;
	private final BattleNext_RuleStore store;
	private final BattleNext_RuleEditor editor;

	public BattleNext_RulesPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this(backAction, navigation, deletion, new BattleNext_RuleStore());
	}

	public BattleNext_RulesPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, BattleNext_RuleStore store) {
		super(backAction, navigation, deletion, "rules:select:");
		this.store = store;
		editor = new BattleNext_RuleEditor(store);
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_rules");
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
		addCatalogDiagnostics(listContent, store.getErrors());
		for (BattleNext_RuleModel rule : store.getAllCopy()) {
			addListItem(listContent, rule.getName(), "rules:select:" + rule.getId());
		}
	}

	@Override
	protected BattleNext_RuleEditor editorView() {
		return editor;
	}

	@Override
	protected boolean canDelete() {
		return store.isLoaded() && store.get(selectedId) != null;
	}

	@Override
	protected void afterEditorCommit() {
		selectedId = editor.getDraftId();
	}

	@Override
	protected boolean openEditor(String id) throws Exception {
		editor.open(store.get(id));
		return true;
	}

	@Override
	protected String deleteDescription() {
		return store.get(selectedId).getName();
	}

	@Override
	protected BattleNext_RuleStore catalog() {
		return store;
	}
}
