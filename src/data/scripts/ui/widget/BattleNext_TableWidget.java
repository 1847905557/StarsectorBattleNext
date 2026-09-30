package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_TableWidget {

	@FunctionalInterface
	public interface RowBuilder {
		void build(int index, float rowWidth, UIRect[] cells, TooltipMakerAPI content);
	}

	private final CustomPanelAPI host;
	private final CustomUIPanelPlugin inputOwner;
	private final String[] headers;
	private final float[] columnShares;
	private final RowBuilder rowBuilder;
	private UIRect bounds;
	private UIRect rowsBounds;
	private TooltipMakerAPI content;
	private int rowCount;

	public BattleNext_TableWidget(CustomPanelAPI host, CustomUIPanelPlugin inputOwner,
			String[] headers, float[] columnShares, int rowCount, RowBuilder rowBuilder) {
		this.host = host;
		this.inputOwner = inputOwner;
		this.headers = headers.clone();
		this.columnShares = columnShares.clone();
		this.rowCount = rowCount;
		this.rowBuilder = rowBuilder;
	}

	public void attach(UIRect bounds) {
		this.bounds = bounds;
		createHeader();
		refresh();
	}

	public void setRowCount(int rowCount) {
		this.rowCount = rowCount;
	}

	public void refresh() {
		if (bounds == null) return;
		if (rowsBounds != null) rowsBounds.removeLatestSubPanel();

		float tableWidth = bounds.getWidth() - BattleNext_PageLayout.TABLE_SIDE_MARGIN * 2f;
		float availableHeight = Math.max(0f, bounds.getHeight() - BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		float visibleHeight = Math.min(availableHeight,
				rowCount * BattleNext_PageLayout.TABLE_ROW_HEIGHT
						+ BattleNext_PageLayout.TABLE_ROW_BOTTOM_PADDING);
		rowsBounds = new UIRect(BattleNext_PageLayout.TABLE_SIDE_MARGIN
				- BattleNext_PageLayout.CUSTOM_COMPONENT_LEFT_INSET,
				bounds.getY() + BattleNext_PageLayout.TABLE_HEADER_HEIGHT,
				tableWidth + BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH, visibleHeight);
		CustomPanelAPI rowsPanel = rowsBounds.createSubPanel(host, inputOwner).getPanel();
		content = rowsPanel.createUIElement(rowsBounds.getWidth(), visibleHeight, true);
		float rowWidth = rowsBounds.getWidth() - BattleNext_PageLayout.SCROLLBAR_RESERVED_WIDTH;
		UIRect[] cells = columns(rowWidth, BattleNext_PageLayout.TABLE_ROW_HEIGHT);
		for (int index = 0; index < rowCount; index++) {
			rowBuilder.build(index, rowWidth, cells, content);
		}
		rowsPanel.addUIElement(content).inTL(0f, 0f);
	}

	public TooltipMakerAPI getContent() {
		return content;
	}

	private void createHeader() {
		float tableWidth = bounds.getWidth() - BattleNext_PageLayout.TABLE_SIDE_MARGIN * 2f;
		TooltipMakerAPI headerBackground = host.createUIElement(tableWidth,
				BattleNext_PageLayout.TABLE_HEADER_HEIGHT, false);
		LabelAPI headerStrip = headerBackground.addSectionHeading("", BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID,
				tableWidth - BattleNext_PageLayout.SECTION_HEADING_EXTRA_WIDTH, 0f);
		headerStrip.getPosition().inTL(0f, 0f);
		headerBackground.setHeightSoFar(BattleNext_PageLayout.TABLE_HEADER_HEIGHT);
		host.addUIElement(headerBackground).inTL(BattleNext_PageLayout.TABLE_SIDE_MARGIN,
				bounds.getY());
		float stripHeight = Math.min(BattleNext_PageLayout.TABLE_HEADER_HEIGHT,
				headerStrip.getPosition().getHeight());
		UIRect[] headingCells = columns(tableWidth, stripHeight);
		for (int index = 0; index < headingCells.length; index++) {
			UIRect cell = headingCells[index];
			addLabel(new UIRect(BattleNext_PageLayout.TABLE_SIDE_MARGIN + cell.getX(),
					bounds.getY() + cell.getY(), cell.getWidth(), cell.getHeight()), headers[index]);
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
		TooltipMakerAPI maker = host.createUIElement(labelWidth, rect.getHeight(), false);
		maker.setParaSmallInsignia();
		LabelAPI label = maker.addPara(maker.shortenString(value,
				Math.max(1f, rect.getWidth() - BattleNext_PageLayout.CELL_LABEL_TRUNCATION_PADDING)), 0f);
		label.setAlignment(Alignment.MID);
		label.getPosition().setSize(labelWidth, label.getPosition().getHeight());
		label.getPosition().inTL(0f,
				Math.max(0f, (rect.getHeight() - label.getPosition().getHeight()) * 0.5f));
		maker.setHeightSoFar(rect.getHeight());
		host.addUIElement(maker).inTL(rect.getX() + BattleNext_PageLayout.CELL_LABEL_SIDE_INSET,
				rect.getY());
	}
}
