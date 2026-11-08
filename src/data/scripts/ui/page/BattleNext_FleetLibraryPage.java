package data.scripts.ui.page;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.widget.provider.BattleNext_VariantCatalog;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_FleetLibraryPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");

	private final BattleNext_FleetStore store;
	private final BattleNext_FleetEditor editor;

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this(backAction, navigation, deletion, new BattleNext_FleetStore(), new BattleNext_VariantCatalog(new BattleNext_PinnedVariantStore()));
	}

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, BattleNext_FleetStore store, BattleNext_VariantCatalog catalog) {
		super(backAction, navigation, deletion, "fleet:");
		this.store = store;
		editor = new BattleNext_FleetEditor(store, catalog);
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_fleets");
	}

	@Override
	protected String promptText() {
		return fleetStrings.get("select_prompt");
	}

	@Override
	protected String emptyListText() {
		return fleetStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		addCatalogDiagnostics(listContent, store.getErrors());
		for (BattleNext_FleetModel fleet : store.getAllCopy()) {
			addListItem(listContent, fleet.getName(), "fleet:" + fleet.getId());
		}
	}

	@Override
	protected BattleNext_FleetEditor editorView() {
		return editor;
	}

	@Override
	protected boolean canDelete() {
		return store.canDelete(selectedId);
	}

	@Override
	protected String sourceNote() {
		return !selectedId.isEmpty() && !store.isManaged(selectedId) ? strings.get("source_unavailable_short") : "";
	}

	@Override
	protected void afterEditorCommit() {
		selectedId = editor.getDraftId();
	}

	@Override
	protected boolean openEditor(String id) {
		editor.open(store.get(id), false);
		return true;
	}

	@Override
	protected String deleteDescription() throws IOException {
		List<String> paths = store.deletionPaths(selectedId);
		return store.get(selectedId).getName() + "\n" + fleetStrings.format(
				paths.size() == 1 ? "delete_directory" : "delete_files", String.join("\n", paths));
	}

	@Override
	protected BattleNext_FleetStore catalog() {
		return store;
	}
}
