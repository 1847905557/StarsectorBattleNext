package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_StatusBar {

	private static final float STATUS_TEXT_RIGHT_RESERVE = 10f;
	private static final float STATUS_TEXT_TOP_INSET = 5f;

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private TooltipMakerAPI content;
	private LabelAPI statusLabel;
	private float textWidth;

	public void attach(CustomPanelAPI panel, UIRect rect) {
		content = rect.beginTooltip(panel, false);
		textWidth = rect.getWidth() - STATUS_TEXT_RIGHT_RESERVE;
		statusLabel = content.addPara(strings.get("ready"), 0f);
		statusLabel.getPosition().inTL(0f, STATUS_TEXT_TOP_INSET);
		content.setHeightSoFar(rect.getHeight());
		rect.addTooltip();
	}

	public void update(String error, String activeTournamentId, boolean pendingResult) {
		if (statusLabel == null) return;
		StringBuilder text = new StringBuilder();
		if (!error.isEmpty()) {
			text.append(error);
		} else if (!activeTournamentId.isEmpty()) {
			text.append(strings.get("tournament_active")).append(": ").append(activeTournamentId);
		} else if (pendingResult) {
			text.append(strings.get("result_updated"));
		} else {
			text.append(strings.get("ready"));
		}
		statusLabel.setText(content.shortenString(text.toString(), textWidth));
	}

	public void showError(String message) {
		if (statusLabel != null) statusLabel.setText(content.shortenString(message, textWidth));
	}
}
