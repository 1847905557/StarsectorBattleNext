package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_FleetLibraryPage extends BattleNext_ManagementPage {

	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_FleetEditor editor = new BattleNext_FleetEditor(store);

	private String selectedId = "";
	private boolean draftOpen;

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Runnable> navigation,
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
		try {
			store.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			status = strings.get("load_failed") + ": " + exception.getMessage();
		}
		selectedId = "";
		draftOpen = false;
	}

	@Override
	protected String pageTitle() {
		return managementStrings.get("page_fleets");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"add", "save", "back"};
	}

	@Override
	protected String promptText() {
		return fleetStrings.get("select_prompt");
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
		return fleetStrings.get("empty");
	}

	@Override
	protected void buildListItems(TooltipMakerAPI listContent) {
		for (BattleNext_FleetModel fleet : store.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, fleet.getName(),
					"fleet:" + fleet.getId(), BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	@Override
	protected void buildDetail(CustomPanelAPI contentPanel) {
		attachEditor(contentPanel);
	}

	@Override
	protected BattleNext_EditorView editorView() {
		return editor;
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
		return store.isLoaded() && store.isManaged(selectedId);
	}

	@Override
	protected String sourceNote() {
		return !selectedId.isEmpty() && !store.isManaged(selectedId) ? strings.get("source_unavailable_short") : "";
	}

	@Override
	protected void clearPageOverlays() {
		editor.closeOverlays();
	}

	@Override
	protected void handlePageAction(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("fleet:")) {
			String id = ((String) buttonId).substring("fleet:".length());
			navigation.accept(() -> {
				editor.open(store.get(id), false);
				selectedId = id;
				draftOpen = true;
				refreshContent();
			});
		} else if ("add".equals(buttonId)) {
			navigation.accept(() -> {
				editor.open(null, false);
				selectedId = "";
				draftOpen = true;
				refreshContent();
			});
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty() && store.isLoaded()) {
			BattleNext_FleetModel selected = store.get(selectedId);
			if (selected != null && store.isManaged(selected.getId())) {
				deletion.accept(selected.getName(), () -> {
					try {
						store.delete(selected.getId());
						selectedId = "";
						draftOpen = false;
						status = strings.get("deleted");
						refreshContent();
					} catch (IOException | JSONException | RuntimeException exception) {
						setStatus(strings.get("save_failed") + ": " + exception.getMessage());
					}
				});
			}
		} else if ("back".equals(buttonId)) {
			if (draftOpen) navigation.accept(() -> { selectedId = ""; draftOpen = false; refreshContent(); });
			else backAction.run();
		} else if (draftOpen) {
			editor.buttonPressed(buttonId);
		}
	}

	@Override
	protected boolean isPageDirty() {
		return draftOpen && editor.hasUnsavedChanges();
	}

	@Override
	protected boolean savePage() {
		if (!draftOpen) return true;
		if (!editor.save()) {
			setStatus(editor.getStatusText());
			return false;
		}
		selectedId = editor.getDraftId();
		setStatus(strings.get("saved"));
		try {
			store.reload();
			refreshContent();
		} catch (Exception exception) {
			setStatus(strings.get("saved") + " / " + strings.get("load_failed") + ": " + exception);
		}
		return true;
	}

	@Override
	protected void discardPage() {
		if (selectedId.isEmpty()) {
			draftOpen = false;
			refreshContent();
		} else {
			editor.deserialize();
		}
	}

	@Override
	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return draftOpen && editor.handleGlobalInput(events);
	}
}
