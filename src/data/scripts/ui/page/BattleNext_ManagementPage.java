package data.scripts.ui.page;

import com.fs.starfarer.api.ui.*;
import data.model.BattleNext_BaseLoader.LoadError;
import data.model.BattleNext_BaseStore;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_SaveErrors;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class BattleNext_ManagementPage extends BattleNext_PageView {

	protected final Runnable backAction;
	protected final Consumer<Runnable> navigation;
	protected final BiConsumer<String, Runnable> deletion;
	protected String selectedId = "";
	protected boolean draftOpen;
	private final String selectionPrefix;

	protected BattleNext_ManagementPage(Runnable backAction, Consumer<Runnable> navigation,
			BiConsumer<String, Runnable> deletion, String selectionPrefix) {
		this.backAction = backAction;
		this.navigation = navigation;
		this.deletion = deletion;
		this.selectionPrefix = selectionPrefix;
	}

	protected UIRect listRect;
	protected UIRect detailRect;
	protected UIRect footerRect;
	protected CustomPanelAPI listPanel;
	protected CustomPanelAPI detailPanel;
	protected TooltipMakerAPI listContent;
	protected ButtonAPI saveButton;

	@Override
	protected final void buildPage() {

		BattleNext_PageLayout.heading(new UIRect(0f, 0f, panel.getPosition().getWidth(),
				BattleNext_PageLayout.HEADER_HEIGHT), panel, pageTitle());
		UIRect[] regions = BattleNext_PageLayout.managementRegions(panel);
		listRect = regions[0];
		detailRect = regions[1];
		footerRect = regions[2];
		listRect.addBorder(panel, BattleNext_UiTheme.PANEL, BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);
		detailRect.addBorder(panel, BattleNext_UiTheme.PANEL, BattleNext_PageLayout.MANAGEMENT_REGION_BORDER_THICKNESS);

		String[] actions = footerActions();
		float buttonWidth = BattleNext_PageLayout.managementFooterButtonWidth(footerRect, actions.length);
		for (int index = 0; index < actions.length; index++) {
			ButtonAPI button = BattleNext_PageLayout.fixedButton(panel,
					BattleNext_PageLayout.managementFooterButtonRect(footerRect, buttonWidth, index),
					strings.get(actions[index]), actions[index], CutStyle.ALL, true);
			if ("save".equals(actions[index])) {
				saveButton = button;
			}
		}

		UIRect statusRect = BattleNext_PageLayout.managementFooterStatusRect(footerRect, buttonWidth, actions.length);
		if (statusRect.getWidth() >= BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_MIN_WIDTH) {
			TooltipMakerAPI footerStatus = panel.createUIElement(statusRect.getWidth(), statusRect.getHeight(), false);
			statusLabel = footerStatus.addPara(status, 0f);
			footerStatus.addTooltipTo(BattleNext_SaveErrors.tooltip(this::getSaveErrors), footerStatus.getPrev(), TooltipMakerAPI.TooltipLocation.ABOVE, true);
			statusLabel.getPosition().inTL(0f, BattleNext_PageLayout.MANAGEMENT_FOOTER_STATUS_TOP_INSET);
			footerStatus.setHeightSoFar(statusRect.getHeight());

			panel.addUIElement(footerStatus).inTL(statusRect.getX(), statusRect.getY());
		}
	}

	@Override
	protected final void refreshPage() {

		float scrollOffset = listContent == null ? 0f : listContent.getExternalScroller().getYOffset();
		listRect.removeLatestSubPanel();
		listPanel = listRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		UIRect listContentRect = BattleNext_PageLayout.managementContentRect(listRect);
		listContent = listPanel.createUIElement(listContentRect.getWidth() + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH,
				listContentRect.getHeight(), true);
		listContent.setTextWidthOverride(listContentRect.getWidth());
		buildList(listContent);
		listPanel.addUIElement(listContent).inTL(listContentRect.getX() - listRect.getX(),
				listContentRect.getY() - listRect.getY());
		listContent.getExternalScroller().setYOffset(scrollOffset);

		detailRect.removeLatestSubPanel();
		detailPanel = detailRect.createSubPanel(panel, panel.getPlugin()).getPanel();
		if (!isDetailOpen()) {
			if (!saveErrors.isEmpty() || status.equals(editorView().getStatusText())) {
				status = "";
			}
			saveErrors.clear();
			editorView().close();
			UIRect promptRect = BattleNext_PageLayout.managementContentRect(detailRect);
			TooltipMakerAPI prompt = detailPanel.createUIElement(promptRect.getWidth(), promptRect.getHeight(), false);
			BattleNext_PageLayout.contentTitle(prompt, promptText(), promptRect.getWidth(), 0f);
			detailPanel.addUIElement(prompt).inTL(promptRect.getX() - detailRect.getX(),
					promptRect.getY() - detailRect.getY());
			return;
		}

		if (editorView().getStatusText().isEmpty()) {
			saveErrors.clear();
			status = "";
		}

		UIRect editorRect = BattleNext_PageLayout.managementEditorRect(detailRect);
		CustomPanelAPI contentPanel = detailPanel.createCustomPanel(editorRect.getWidth(), editorRect.getHeight(),
				detailPanel.getPlugin());
		detailPanel.addComponent(contentPanel).inTL(editorRect.getX() - detailRect.getX(),
				editorRect.getY() - detailRect.getY());
		buildDetail(contentPanel);

		if (hasSelection()) {
			ButtonAPI delete = BattleNext_PageLayout.fixedButton(detailPanel,
					BattleNext_PageLayout.managementDeleteButtonLocalRect(detailRect),
					strings.get("delete"), "delete", CutStyle.ALL, true);
			delete.setEnabled(canDelete());
		}

		String noteText = sourceNote();
		if (!noteText.isEmpty()) {
			UIRect noteRect = BattleNext_PageLayout.managementSourceNoteLocalRect(detailRect);
			TooltipMakerAPI note = detailPanel.createUIElement(noteRect.getWidth(), noteRect.getHeight(), false);
			note.addPara(note.shortenString(noteText,
					detailRect.getWidth() - BattleNext_PageLayout.MANAGEMENT_SOURCE_NOTE_TEXT_RESERVE), 0f);
			detailPanel.addUIElement(note).inTL(noteRect.getX(), noteRect.getY());
		}
	}

	@Override
	protected final void updatePageControls() {
		saveButton.setEnabled(canSave());
	}

	@Override
	protected void establishPageState() {
		listContent.getExternalScroller().setYOffset(0f);
	}

	@Override
	public boolean handleEscape() {
		buttonPressed("back");
		return true;
	}

	@Override
	protected final boolean savePage() {
		if (!isDetailOpen() || !isDetailEditable()) return true;
		if (!editorView().save()) {
			setStatus(editorView().getStatusText());
			return false;
		}

		setStatus(strings.get("saved"));
		String inputState = editorView().serializeState();
		try {
			afterEditorCommit();
			reloadSavedData();
			refreshContent();
		} catch (Exception exception) {
			saveErrors.failure(strings.get("load_failed"), pageTitle(), exception);
			setStatus(strings.get("saved") + " / " + saveErrors.summary());
			BattleNext_Logger.warn(getClass(), "management.display.after_commit.failed",
					BattleNext_Logger.fields().put("input_state", inputState).build(), exception);
		}

		return true;
	}

	protected void afterEditorCommit() {

	}

	@Override
	protected final void afterSave() {
		refreshContent();
	}

	@Override
	protected final void savePublished() {
		afterEditorCommit();
	}

	protected void reloadSavedData() throws Exception {
		catalog().reload();
	}

	@Override
	public final String selection() {
		return selectedId;
	}

	@Override
	public final void restoreSelection(String id) {
		if (selectionAvailable(id)) buttonPressed(selectionPrefix + id);
	}

	protected boolean selectionAvailable(String id) {
		return catalog().get(id) != null;
	}

	@Override
	protected void loadPageState() {
		selectedId = "";
		draftOpen = false;
		try {
			catalog().ensureLoaded();
		} catch (Exception exception) {
			setStatus(strings.get("load_failed") + ": " + exception.getMessage());
		}
	}

	@Override
	protected final void handlePageAction(Object buttonId) {
		if (!(buttonId instanceof String action)) return;

		if (action.startsWith(selectionPrefix) || action.equals("add")) {
			String id = action.equals("add") ? "" : action.substring(selectionPrefix.length());
			navigation.accept(() -> {
				try {
					if (openEditor(id)) {
						selectedId = id;
						draftOpen = true;
						refreshContent();
					} else if (!isSaving()) setStatus(editorView().getStatusText());
				} catch (Exception exception) {
					setStatus(strings.get("load_failed") + ": " + exception.getMessage());
				}
			});
		} else if (action.equals("save")) {
			save();
		} else if (action.equals("delete") && hasSelection() && canDelete()) {
			try {
				String id = selectedId;
				deletion.accept(deleteDescription(), () -> persistMutation(() -> {
					deleteSelection(id);
					return true;
				}, () -> {
					selectedId = "";
					draftOpen = false;
					catalogChanged();
					setStatus(strings.get("deleted"));
				}));
			} catch (Exception exception) {
				setStatus(strings.get("delete_failed") + ": " + exception.getMessage());
			}
		} else if (action.equals("back")) {
			if (draftOpen) navigation.accept(() -> showSelection(""));
			else backAction.run();
		} else if (draftOpen) {
			saveErrors.clear();
			status = "";
			editorView().buttonPressed(action);
		}
	}

	protected final void showSelection(String id) {
		selectedId = id;
		draftOpen = !id.isEmpty();
		refreshContent();
	}

	protected abstract BattleNext_BaseStore<?> catalog();
	protected abstract boolean openEditor(String id) throws Exception;
	protected abstract String deleteDescription() throws Exception;

	protected void deleteSelection(String id) throws Exception {
		catalog().delete(id);
	}

	@Override
	protected final boolean isPageDirty() {
		return draftOpen && editorView().hasUnsavedChanges();
	}

	@Override
	protected void discardPage() {
		if (selectedId.isEmpty()) showSelection("");
		else editorView().discard();
	}

	@Override
	public final boolean handleGlobalInput(List<InputEventAPI> events) {
		return draftOpen && editorView().handleGlobalInput(events);
	}

	protected abstract String pageTitle();

	protected String[] footerActions() {
		return new String[]{"add", "save", "back"};
	}

	protected abstract String promptText();

	protected abstract void buildListItems(TooltipMakerAPI listContent);

	protected final void addListItem(TooltipMakerAPI listContent, String name, Object action) {
		addListItem(listContent, name, action, null);
	}

	protected final void addListItem(TooltipMakerAPI listContent, String name, Object action,
			TooltipMakerAPI.TooltipCreator tooltip) {
		float areaWidth = BattleNext_PageLayout.managementContentRect(listRect).getWidth();
		BattleNext_PageLayout.listButton(listContent, name, action, areaWidth, tooltip);
	}

	protected final void addCatalogDiagnostics(TooltipMakerAPI content, List<LoadError> errors) {
		for (LoadError error : errors) {
			String title = strings.get("load_failed") + ": " + error.path() + " / " + error.id();
			content.addPara(content.shortenString(title, BattleNext_PageLayout.managementContentRect(listRect).getWidth()), listErrorGap());
			content.addTooltipToPrevious(new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object tooltipParam) {
					return 480f;
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					tooltip.addPara(title + " / " + error.position(), 0f);
					tooltip.addPara(error.diagnostic(), 5f);
					tooltip.addPara(error.raw(), 5f);
				}
			}, TooltipMakerAPI.TooltipLocation.RIGHT);
		}
	}

	protected boolean isListLoaded() {
		return catalog().isLoaded();
	}

	protected String listError() {
		return catalog().getLoadError();
	}

	protected boolean isListEmpty() {
		return catalog().getAllCopy().isEmpty();
	}

	protected abstract String emptyListText();

	protected final void buildDetail(CustomPanelAPI contentPanel) {
		attachEditor(contentPanel);
	}

	protected boolean isDetailOpen() {
		return draftOpen;
	}

	protected boolean hasSelection() {
		return !selectedId.isEmpty();
	}

	protected final boolean canSave() {
		return isDetailOpen() && isDetailEditable() && isSaveDataLoaded();
	}

	protected boolean isDetailEditable() {
		return true;
	}

	protected boolean isSaveDataLoaded() {
		return catalog().isLoaded();
	}

	protected boolean canDelete() {
		return catalog().isLoaded();
	}

	protected String sourceNote() {
		return "";
	}

	protected String listTitle() {
		return pageTitle();
	}

	protected float listErrorGap() {
		return BattleNext_PageLayout.MANAGEMENT_LIST_ERROR_TOP_GAP;
	}

	protected float listEmptyGap() {
		return BattleNext_PageLayout.MANAGEMENT_LIST_EMPTY_TOP_GAP;
	}

	private void buildList(TooltipMakerAPI listContent) {
		float areaWidth = BattleNext_PageLayout.managementContentRect(listRect).getWidth();
		BattleNext_PageLayout.contentTitle(listContent, listTitle(), areaWidth, 0f);
		if (!isListLoaded()) {
			listContent.addPara(strings.get("load_failed") + ": " + listError(), listErrorGap());
		}
		if (isListEmpty()) {
			listContent.addPara(emptyListText(), listEmptyGap());
		}
		buildListItems(listContent);
	}
}
