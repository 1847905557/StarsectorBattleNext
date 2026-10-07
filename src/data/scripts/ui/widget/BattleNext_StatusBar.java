package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

public class BattleNext_StatusBar extends BattleNext_TooltipBasedWidget {

	private static final float STATUS_TEXT_RIGHT_RESERVE = 10f;
	private static final float STATUS_TEXT_TOP_INSET = 5f;

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");

	private TooltipMakerAPI content;
	private LabelAPI statusLabel;
	private float textWidth;
	private String error = "";
	private String activeTournamentId = "";
	private String reportId = "";
	private final float width;
	private final float height;

	public BattleNext_StatusBar(float width, float height) {
		this.width = width;
		this.height = height;
	}

	@Override
	protected void buildContent(TooltipMakerAPI host, CustomPanelAPI panelOwner) {
		content = host;
		textWidth = width - STATUS_TEXT_RIGHT_RESERVE;
		statusLabel = content.addPara(strings.get("ready"), 0f);
		statusLabel.getPosition().inTL(0f, STATUS_TEXT_TOP_INSET);
		content.setHeightSoFar(height);
	}

	public void update(String error, String activeTournamentId, String reportId) {
		this.error = error;
		this.activeTournamentId = activeTournamentId;
		this.reportId = reportId;
		refreshText();
	}

	public void reportSaved(String reportId) {
		this.reportId = reportId;
		refreshText();
	}

	public void refreshText() {
		if (statusLabel == null) return;
		StringBuilder text = new StringBuilder();
		if (!error.isEmpty()) {
			text.append(error);
		} else if (!reportId.isEmpty()) {
			text.append(managementStrings.format("report_updated", reportId));
		} else if (!activeTournamentId.isEmpty()) {
			text.append(managementStrings.format("active_tournament", activeTournamentId));
		} else {
			text.append(strings.get("ready"));
		}
		statusLabel.setText(content.shortenString(text.toString(), textWidth));
	}

	public void showError(String message) {
		error = message;
		refreshText();
	}
}
