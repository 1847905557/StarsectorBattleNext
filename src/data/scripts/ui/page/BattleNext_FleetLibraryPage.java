package data.scripts.ui.page;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BattleNext_FleetLibraryPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_FleetEditor editor = new BattleNext_FleetEditor(store);
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");

	private CustomPanelAPI panel;
	private CustomPanelAPI overlayHost;
	private UIRect listRect;
	private UIRect detailRect;
	private String selectedId = "";
	private boolean draftOpen;
	private String status = "";
	private LabelAPI statusLabel;
	private ButtonAPI saveButton;

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	public void setOverlayHost(CustomPanelAPI overlayHost) {
		this.overlayHost = overlayHost;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		try {
			store.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			status = text("load_failed") + ": " + exception.getMessage();
		}
		selectedId = "";
		draftOpen = false;
		createContents();
	}

	@Override
	public void createContents() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), BattleNext_PageLayout.HEADER_HEIGHT),
				panel, text("page_fleets"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.PANEL,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		detailRect.addBorder(panel, BattleNext_UiTheme.PANEL,
				BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		UIRect footerRect = regions[2];
		String[] actions = {"add", "save", "back"};
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footerRect, actions.length);
		for (int index = 0; index < actions.length; index++) {
			ButtonAPI button = BattleNext_PageLayout.fixedButton(panel,
					BattleNext_PageLayout.managementFooterButtonRect(footerRect, buttonWidth, index),
					text(actions[index]), actions[index], CutStyle.ALL, true);
			if (index == 1) {
				saveButton = button;
				saveButton.setEnabled(store.isLoaded());
			}
		}
		UIRect statusRect = BattleNext_PageLayout.managementFooterStatusRect(footerRect, buttonWidth, actions.length);
		if (statusRect.getWidth() >= BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_MIN_WIDTH) {
			TooltipMakerAPI footerStatus = panel.createUIElement(statusRect.getWidth(), statusRect.getHeight(), false);
			statusLabel = footerStatus.addPara(status, 0f);
			statusLabel.getPosition().inTL(0f, BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_TOP_INSET);
			footerStatus.setHeightSoFar(statusRect.getHeight());
			panel.addUIElement(footerStatus).inTL(statusRect.getX(), statusRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		editor.closePicker();
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		UIRect listContentRect = BattleNext_PageLayout.managementContentRect(listRect);
		TooltipMakerAPI listContent = listPanel.createUIElement(listContentRect.getWidth(), listContentRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(listContentRect.getX() - listRect.getX(),
				listContentRect.getY() - listRect.getY());

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (!draftOpen) {
			UIRect promptRect = BattleNext_PageLayout.managementContentRect(detailRect);
			TooltipMakerAPI prompt = detailPanel.createUIElement(promptRect.getWidth(), promptRect.getHeight(), false);
			prompt.addTitle(text("select_fleet"));
			detailPanel.addUIElement(prompt).inTL(promptRect.getX() - detailRect.getX(),
					promptRect.getY() - detailRect.getY());
			return;
		}
		BattleNext_FleetModel selected = selectedId.isEmpty() ? null : store.get(selectedId);
		editor.open(selected, false);
		editor.setOverlayHost(overlayHost);
		UIRect editorRect = BattleNext_PageLayout.managementEditorRect(detailRect);
		CustomPanelAPI editorPanel = detailPanel.createCustomPanel(editorRect.getWidth(), editorRect.getHeight(),
				detailPanel.getPlugin());
		detailPanel.addComponent(editorPanel).inTL(editorRect.getX() - detailRect.getX(),
				editorRect.getY() - detailRect.getY());
		editor.attach(editorPanel);
		if (selected != null) {
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect),
					text("delete"), "delete", CutStyle.ALL, true);
			boolean managed = store.isManaged(selected.getId());
			delete.setEnabled(managed && store.isLoaded());
			if (!managed) {
				UIRect noteRect = BattleNext_PageLayout.managementSourceNoteLocalRect(detailRect);
				TooltipMakerAPI note = detailPanel.createUIElement(noteRect.getWidth(), noteRect.getHeight(), false);
				note.addPara(note.shortenString(text("source_unavailable_short"),
						detailRect.getWidth() - BattleNext_PageLayout.MANAGEMENT_SOURCE_NOTE_TEXT_RESERVE),
						0f);
				detailPanel.addUIElement(note).inTL(noteRect.getX(), noteRect.getY());
			}
		}
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		return draftOpen && editor.handleGlobalInput(events);
	}

	public void closePicker() {
		editor.closePicker();
	}

	@Override
	public void buttonPressed(Object buttonId) {
		editor.closePicker();
		if (buttonId instanceof String && ((String) buttonId).startsWith("fleet:")) {
			String id = ((String) buttonId).substring("fleet:".length());
			navigation.accept(() -> { selectedId = id; draftOpen = true; refreshContent(); });
		} else if ("add".equals(buttonId)) {
			navigation.accept(() -> { selectedId = ""; draftOpen = true; refreshContent(); });
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty() && store.isLoaded()) {
			BattleNext_FleetModel selected = store.get(selectedId);
			if (selected != null && store.isManaged(selected.getId())) {
				deletion.accept(selected.getName() + " [" + selected.getId() + "]", () -> {
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

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_fleets"));
		if (!store.isLoaded()) listContent.addPara(text("load_failed") + ": " + store.getLoadError(),
				BattleNext_PageLayout.MANAGEMENT_LIST_ERROR_TOP_GAP);
		if (store.getAll().isEmpty()) listContent.addPara(text("no_fleets"),
				BattleNext_PageLayout.MANAGEMENT_LIST_EMPTY_TOP_GAP);
		for (BattleNext_FleetModel fleet : store.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, fleet.getName(),
					"fleet:" + fleet.getId(), BattleNext_PageLayout.managementListButtonWidth(listRect));
		}
	}

	@Override
	public boolean hasUnsavedChanges() {
		return draftOpen && editor.hasUnsavedChanges();
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
	public void discard() {
		if (selectedId.isEmpty()) {
			draftOpen = false;
			refreshContent();
		} else {
			editor.deserialize();
		}
	}

	@Override
	public String getStatusText() {
		return status;
	}

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}

}
