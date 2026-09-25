package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_StatusBar {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private LabelAPI statusLabel;

	public void attach(TooltipMakerAPI footer) {
		statusLabel = footer.addPara(strings.get("ready"), 0f, BattleNext_UiTheme.MUTED);
		statusLabel.getPosition().inBL(0f, 6f);
	}

	public void update(String error, int activeTournamentSlot, boolean pendingResult) {
		if (statusLabel == null) return;
		StringBuilder text = new StringBuilder();
		if (!error.isEmpty()) {
			text.append(error);
		} else if (activeTournamentSlot >= 0) {
			text.append(strings.get("tournament_active")).append(": ").append(activeTournamentSlot);
		} else if (pendingResult) {
			text.append(strings.get("result_updated"));
		} else {
			text.append(strings.get("ready"));
		}
		statusLabel.setText(text.toString());
	}

	public void showError(String message) {
		if (statusLabel != null) statusLabel.setText(message);
	}
}
