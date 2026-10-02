package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_FleetLibraryPage extends BattleNext_ManagementPage {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_FleetEditor editor = new BattleNext_FleetEditor(store);

	private CustomPanelAPI overlayHost;
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
			status = text("load_failed") + ": " + exception.getMessage();
		}
		selectedId = "";
		draftOpen = false;
	}

	@Override
	protected String pageTitle() {
		return text("page_fleets");
	}

	@Override
	protected String[] footerActions() {
		return new String[]{"add", "save", "back"};
	}

	@Override
	protected String promptText() {
		return text("select_fleet");
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
		return text("no_fleets");
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
		editor.setOverlayHost(overlayHost);
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
		return store.isLoaded() && store.isManaged(selectedId);
	}

	@Override
	protected String sourceNote() {
		return !selectedId.isEmpty() && !store.isManaged(selectedId) ? text("source_unavailable_short") : "";
	}

	@Override
	protected void clearPageOverlays() {
		editor.closePicker();
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
						status = text("deleted");
						refreshContent();
					} catch (IOException | JSONException | RuntimeException exception) {
						setStatus(text("save_failed") + ": " + exception.getMessage());
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
		if (!store.isLoaded()) {
			setStatus(text("load_failed") + ": " + store.getLoadError());
			return false;
		}
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValidPathSegment(id)) {
			setStatus(text("invalid_fleet_id"));
			return false;
		}
		for (BattleNext_FleetModel fleet : store.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, fleet.getId()) && !fleet.getId().equals(selectedId)) {
				setStatus(text("duplicate_id")); return false;
			}
		}
		String folderName = editor.getDraftFolderName();
		if (folderName.isEmpty()) folderName = id;
		if (!BattleNext_EntryId.isValidPathSegment(folderName)) {
			setStatus(text("invalid_fleet_folder"));
			return false;
		}
		for (BattleNext_FleetModel fleet : store.getAll()) {
			if (BattleNext_EntryId.sameFileName(folderName, fleet.getFolderName())
					&& !fleet.getId().equals(selectedId)) {
				setStatus(text("duplicate_fleet_folder"));
				return false;
			}
		}
		BattleNext_FleetModel original = selectedId.isEmpty() ? null : store.get(selectedId);
			if (original != null && BattleNext_EntryId.sameFileName(id, selectedId)
					&& original.getSourceType() == BattleNext_LoadSource.LEGACY) {
			setStatus(text("source_unavailable")); return false;
		}
		if (!editor.serialize()) {
			setStatus(editor.getStatusText());
			return false;
		}
		try {
			store.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			setStatus(text("load_failed") + ": " + exception.getMessage());
			return false;
		}
		selectedId = id;
		status = text("saved");
		refreshContent();
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

	public void setOverlayHost(CustomPanelAPI overlayHost) {
		this.overlayHost = overlayHost;
	}
}
