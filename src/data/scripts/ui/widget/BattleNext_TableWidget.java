package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.function.BiConsumer;

public class BattleNext_TableWidget extends BattleNext_PanelBasedWidget {

	@FunctionalInterface
	public interface RowBuilder {
		void build(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI content);
	}

	private final CustomUIPanelPlugin inputOwner;
	private final String[] headers;
	private final float[] columnShares;
	private final RowBuilder rowBuilder;
	private final BiConsumer<Float, TooltipMakerAPI> contentBuilder;

	private UIRect rowsBounds;
	private CustomPanelAPI rowsPanel;
	private float scrollOffset;
	private TooltipMakerAPI content;
	private int rowCount;
	private boolean headerBuilt;

	public BattleNext_TableWidget(CustomUIPanelPlugin inputOwner,
			String[] headers, float[] columnShares, int rowCount, RowBuilder rowBuilder) {
		this(inputOwner, headers, columnShares, rowCount, rowBuilder, (width, content) -> {

		});
	}

	public BattleNext_TableWidget(CustomUIPanelPlugin inputOwner,
			String[] headers, float[] columnShares, int rowCount, RowBuilder rowBuilder,
			BiConsumer<Float, TooltipMakerAPI> contentBuilder) {
		this.inputOwner = inputOwner;
		this.headers = headers.clone();
		this.columnShares = columnShares.clone();
		this.rowCount = rowCount;
		this.rowBuilder = rowBuilder;
		this.contentBuilder = contentBuilder;
	}

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		float tableWidth = panel.getPosition().getWidth();
		float availableHeight = Math.max(0f, panel.getPosition().getHeight() - BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		if (!headerBuilt) {
			createHeader();
			headerBuilt = true;
		}
		rowsBounds = new UIRect(-BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET,
				BattleNext_PageLayout.TABLE_HEADER_HEIGHT,
				tableWidth + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, availableHeight);
		rowsPanel = ownPanel(panel, rowsBounds.createSubPanel(panel, inputOwner).getPanel());
		content = rowsPanel.createUIElement(rowsBounds.getWidth(), availableHeight, true);
		float rowWidth = rowsBounds.getWidth() - BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH;
		UIRect[] cells = columns(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		for (int index = 0; index < rowCount; index++) {
			rowBuilder.build(index, rowWidth, cells, content);
		}
		contentBuilder.accept(rowWidth, content);
		float visibleHeight = Math.min(availableHeight,
				content.getHeightSoFar() + BattleNext_PageLayout.TABLE_ROW_BOTTOM_PADDING);
		rowsPanel.getPosition().setSize(rowsBounds.getWidth(), visibleHeight);
		content.getPosition().setSize(rowsBounds.getWidth(), visibleHeight);
		rowsPanel.addUIElement(content).inTL(0f, 0f);
		if (content.getExternalScroller() != null) {
			float limit = Math.max(0f, content.getHeightSoFar() - availableHeight);
			content.getExternalScroller().setYOffset(Math.min(limit, scrollOffset));
		}
	}

	@Override
	protected void clearPageState() {
		headerBuilt = false;
	}

	@Override
	protected void clearContent() {
		if (content != null && content.getExternalScroller() != null) {
			scrollOffset = content.getExternalScroller().getYOffset();
		}
		if (rowsPanel != null) releasePanel(rowsPanel);
		rowsPanel = null;
		content = null;
	}

	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
	}

	public void refresh() {
		refreshContent();
	}

	public TooltipMakerAPI getContent() {
		return content;
	}

	private void createHeader() {
		float tableWidth = panel.getPosition().getWidth();
		TooltipMakerAPI headerBackground = panel.createUIElement(tableWidth,
				BattleNext_PageLayout.TABLE_HEADER_HEIGHT, false);
		LabelAPI headerStrip = headerBackground.addSectionHeading("", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID,
				tableWidth - BattleNext_PageLayout.SECTION_HEADING_EXTRA_WIDTH, 0f);
		headerStrip.getPosition().inTL(0f, 0f);
		headerBackground.setHeightSoFar(BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		panel.addUIElement(headerBackground).inTL(0f, 0f);
		float stripHeight = Math.min(BattleNext_PageLayout.TABLE_HEADER_HEIGHT,
				headerStrip.getPosition().getHeight());
		UIRect[] headingCells = columns(tableWidth, stripHeight);
		for (int index = 0; index < headingCells.length; index++) {
			UIRect cell = headingCells[index];
			addLabel(cell, headers[index]);
		}
	}

	private UIRect[] columns(float width, float height) {
		float available = width - BattleNext_PageLayout.TABLE_COLUMN_GAP * (columnShares.length - 1);
		UIRect[] result = new UIRect[columnShares.length];
		float x = 0f;
		for (int index = 0; index < columnShares.length; index++) {
			float nextX = index == columnShares.length - 1 ? width : x + available * columnShares[index];
			result[index] = new UIRect(x, 0f, nextX - x, height);
			x = nextX + BattleNext_PageLayout.TABLE_COLUMN_GAP;
		}
		return result;
	}

	private void addLabel(UIRect rect, String value) {
		float labelWidth = Math.max(1f,
				rect.getWidth() - BattleNext_PageLayout.CELL_LABEL_SIDE_INSET * 2f);
		TooltipMakerAPI maker = panel.createUIElement(labelWidth, rect.getHeight(), false);
		maker.setParaSmallInsignia();
		LabelAPI label = maker.addPara(maker.shortenString(value,
				Math.max(1f, rect.getWidth() - BattleNext_PageLayout.CELL_LABEL_TRUNCATION_PADDING)), 0f);
		label.setAlignment(Alignment.MID);
		label.getPosition().setSize(labelWidth, label.getPosition().getHeight());
		label.getPosition().inTL(0f,
				Math.max(0f, (rect.getHeight() - label.getPosition().getHeight()) * 0.5f));
		maker.setHeightSoFar(rect.getHeight());
		panel.addUIElement(maker).inTL(rect.getX() + BattleNext_PageLayout.CELL_LABEL_SIDE_INSET,
				rect.getY());
	}
}
