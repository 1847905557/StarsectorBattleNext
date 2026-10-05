package data.scripts.ui.page;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

public abstract class BattleNext_ManagementPage extends BattleNext_PageView {

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
			UIRect promptRect = BattleNext_PageLayout.managementContentRect(detailRect);
			TooltipMakerAPI prompt = detailPanel.createUIElement(promptRect.getWidth(), promptRect.getHeight(), false);
			BattleNext_PageLayout.contentTitle(prompt, promptText(), promptRect.getWidth(), 0f);
			detailPanel.addUIElement(prompt).inTL(promptRect.getX() - detailRect.getX(),
					promptRect.getY() - detailRect.getY());
			return;
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

	protected abstract String pageTitle();

	protected abstract String[] footerActions();

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

	protected abstract boolean isListLoaded();

	protected abstract String listError();

	protected abstract boolean isListEmpty();

	protected abstract String emptyListText();

	protected abstract void buildDetail(CustomPanelAPI contentPanel);

	protected abstract boolean isDetailOpen();

	protected abstract boolean hasSelection();

	protected final boolean canSave() {
		return isDetailOpen() && isDetailEditable() && isSaveDataLoaded();
	}

	protected boolean isDetailEditable() {
		return true;
	}

	protected abstract boolean isSaveDataLoaded();

	protected abstract boolean canDelete();

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
