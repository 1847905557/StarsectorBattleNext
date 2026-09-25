package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.BattleNext_EntryId;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_FleetDraftEditor;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

public class BattleNext_FleetLibraryPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final Consumer<Runnable> navigation;
	private final BiConsumer<String, Runnable> deletion;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_FleetDraftEditor editor = new BattleNext_FleetDraftEditor();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private String selectedId = "";
	private boolean draftOpen;
	private String status = "";
	private LabelAPI statusLabel;

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		store.reload();
		selectedId = "";
		draftOpen = false;
		createContents();
	}

	@Override
	public void createContents() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		BattleNext_PageLayout.heading(new UIRect(0f, 0f, root.getWidth(), 30f), panel, text("page_fleets"));
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		listRect.addBorder(panel, BattleNext_UiTheme.ACCENT, 1f);
		detailRect.addBorder(panel, BattleNext_UiTheme.ACCENT, 1f);
		UIRect footerRect = regions[2];
		float buttonWidth = Math.min(160f, (footerRect.getWidth() - 20f) / 3f);
		for (int index = 0; index < 3; index++) {
			String[] actions = {"add", "save", "back"};
			BattleNext_PageLayout.fixedButton(panel,
					new UIRect(footerRect.getX() + index * (buttonWidth + 10f), footerRect.getY(), buttonWidth, 30f),
					text(actions[index]), actions[index], CutStyle.ALL, true);
		}
		float statusX = 3f * buttonWidth + 30f;
		if (footerRect.getWidth() - statusX >= 60f) {
			TooltipMakerAPI footerStatus = panel.createUIElement(footerRect.getWidth() - statusX, 30f, false);
			statusLabel = footerStatus.addPara(status, 0f, BattleNext_UiTheme.MUTED);
			statusLabel.getPosition().inTL(0f, 5f);
			footerStatus.setHeightSoFar(30f);
			panel.addUIElement(footerStatus).inTL(footerRect.getX() + statusX, footerRect.getY());
		}

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI listContent = listPanel.createUIElement(listRect.getWidth() - 18f, listRect.getHeight() - 16f, true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(8f, 8f);

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, null).getPanel();
		if (!draftOpen) {
			TooltipMakerAPI prompt = detailPanel.createUIElement(detailRect.getWidth() - 18f, detailRect.getHeight() - 16f, false);
			prompt.addTitle(text("select_fleet"));
			detailPanel.addUIElement(prompt).inTL(8f, 8f);
			return;
		}
		BattleNext_FleetModel selected = selectedId.isEmpty() ? null : store.get(selectedId);
		editor.open(selected, false);
		CustomPanelAPI editorPanel = detailPanel.createCustomPanel(detailRect.getWidth() - 16f, detailRect.getHeight() - 50f, null);
		detailPanel.addComponent(editorPanel).inTL(8f, 8f);
		editor.attach(editorPanel);
		if (selected != null) {
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					new UIRect(8f, detailRect.getHeight() - 36f, 145f, 28f),
					text("delete"), "delete", CutStyle.ALL, true);
			boolean managed = "user".equals(selected.getSource());
			delete.setEnabled(managed);
			if (!managed) {
				TooltipMakerAPI note = detailPanel.createUIElement(detailRect.getWidth() - 170f, 28f, false);
				note.addPara(note.shortenString(text("source_unavailable_short"), detailRect.getWidth() - 180f),
						0f, BattleNext_UiTheme.MUTED);
				detailPanel.addUIElement(note).inTL(165f, detailRect.getHeight() - 34f);
			}
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("fleet:")) {
			String id = ((String) buttonId).substring("fleet:".length());
			navigation.accept(() -> { selectedId = id; draftOpen = true; refreshContent(); });
		} else if ("add".equals(buttonId)) {
			navigation.accept(() -> { selectedId = ""; draftOpen = true; refreshContent(); });
		} else if ("save".equals(buttonId)) {
			save();
		} else if ("delete".equals(buttonId) && !selectedId.isEmpty()) {
			BattleNext_FleetModel selected = store.get(selectedId);
			if (selected != null && "user".equals(selected.getSource())) {
				deletion.accept(selected.getName() + " [" + selected.getId() + "]", () -> {
					try {
						store.delete(selected);
						selectedId = "";
						draftOpen = false;
						status = text("deleted");
						refreshContent();
					} catch (IOException exception) {
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
		if (store.getAll().isEmpty()) listContent.addPara(text("no_fleets"), 10f, BattleNext_UiTheme.MUTED);
		for (BattleNext_FleetModel fleet : store.getAll()) {
			BattleNext_PageLayout.buttonWithTooltip(listContent, fleet.getName() + " [" + fleet.getId() + "]",
					"fleet:" + fleet.getId(), Math.max(80f, listRect.getWidth() - 30f));
		}
	}

	@Override public boolean hasUnsavedChanges() { return draftOpen && editor.hasUnsavedChanges(); }

	@Override public boolean save() {
		if (!draftOpen) return true;
		String id = editor.getDraftId();
		if (!BattleNext_EntryId.isValid(id)) { setStatus(text("invalid_fleet_id")); return false; }
		for (BattleNext_FleetModel fleet : store.getAll()) {
			if (BattleNext_EntryId.sameFileName(id, fleet.getId()) && !fleet.getId().equals(selectedId)) {
				setStatus(text("duplicate_id")); return false;
			}
		}
		BattleNext_FleetModel original = selectedId.isEmpty() ? null : store.get(selectedId);
		if (original != null && BattleNext_EntryId.sameFileName(id, selectedId)
				&& !"user".equals(original.getSource())) {
			setStatus(text("source_unavailable")); return false;
		}
		if (!editor.serialize()) { setStatus(editor.getStatusText()); return false; }
		store.reload();
		selectedId = id;
		status = text("saved");
		refreshContent();
		return true;
	}

	@Override public void discard() {
		if (selectedId.isEmpty()) { draftOpen = false; refreshContent(); }
		else { editor.deserialize(); }
	}

	@Override public String getStatusText() { return status; }

	private void setStatus(String value) {
		status = value;
		if (statusLabel != null) statusLabel.setText(value);
	}

	private String text(String key) {
		return strings.get(key);
	}

}
