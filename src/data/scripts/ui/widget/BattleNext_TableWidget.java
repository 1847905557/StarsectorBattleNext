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
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.HashMap;
import java.util.Map;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;

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
	private VisibleRows visibleRows;
	private final IntConsumer releaseRow;
	private final IntPredicate keepRow;
	private final Map<Integer, CustomPanelAPI> mountedRows = new HashMap<>();

	public BattleNext_TableWidget(CustomUIPanelPlugin inputOwner,
			String[] headers, float[] columnShares, int rowCount, RowBuilder rowBuilder,
			IntConsumer releaseRow, IntPredicate keepRow) {
		this(inputOwner, headers, columnShares, rowCount, rowBuilder, releaseRow, keepRow, (width, content) -> {

		});
	}

	public BattleNext_TableWidget(CustomUIPanelPlugin inputOwner,
			String[] headers, float[] columnShares, int rowCount, RowBuilder rowBuilder,
			IntConsumer releaseRow, IntPredicate keepRow,
			BiConsumer<Float, TooltipMakerAPI> contentBuilder) {
		this.inputOwner = inputOwner;
		this.headers = headers.clone();
		this.columnShares = columnShares.clone();
		this.rowCount = rowCount;
		this.rowBuilder = rowBuilder;
		this.releaseRow = releaseRow;
		this.keepRow = keepRow;
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
		rowsPanel = ownPanel(panel, rowsBounds.createSubPanel(panel, new BaseCustomUIPanelPlugin() {
			@Override
			public void advance(float amount) {
				if (visibleRows != null) visibleRows.update();
			}
		}).getPanel());

		content = rowsPanel.createUIElement(rowsBounds.getWidth(), availableHeight, true);
		float rowWidth = rowsBounds.getWidth() - BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH;
		UIRect[] cells = columns(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		visibleRows = new VisibleRows(rowCount, index -> BattleNext_PageLayout.TABLE_ROW_HEIGHT,
				index -> {
					CustomPanelAPI canvas = visibleRows.getPanel();
					CustomPanelAPI row = canvas.createCustomPanel(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, new BaseCustomUIPanelPlugin());
					TooltipMakerAPI fields = row.createUIElement(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT, false);
					rowBuilder.build(index, rowWidth, cells, fields);
					row.addUIElement(fields).inTL(0f, 0f);
					mountedRows.put(index, row);
					canvas.addComponent(row).inTL(0f, visibleRows.top(index));
				}, index -> {
					releaseRow.accept(index);
					visibleRows.getPanel().removeComponent(mountedRows.remove(index));
				}, keepRow);
		visibleRows.attach(rowsPanel, content, rowWidth, 0f);
		visibleRows.update(scrollOffset, availableHeight);

		contentBuilder.accept(rowWidth, content);
		float visibleHeight = Math.min(availableHeight,
				content.getHeightSoFar() + BattleNext_PageLayout.TABLE_ROW_BOTTOM_PADDING);
		rowsPanel.getPosition().setSize(rowsBounds.getWidth(), visibleHeight);
		content.getPosition().setSize(rowsBounds.getWidth(), visibleHeight);

		rowsPanel.addUIElement(content).inTL(0f, 0f);

		if (content.getExternalScroller() != null) {
			BattleNext_PageLayout.restoreScrollOffset(content, availableHeight, scrollOffset);
		}
	}

	@Override
	protected void clearPageState() {
		headerBuilt = false;
	}

	@Override
	protected void clearContent() {
		if (visibleRows != null) visibleRows.close();
		visibleRows = null;
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
		BattleNext_PageLayout.cellLabel(panel, rect, value, true, null, TooltipMakerAPI.TooltipLocation.RIGHT, false);
	}
}
