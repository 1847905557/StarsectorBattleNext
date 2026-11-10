package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;

import java.util.List;

public class BattleNext_EmptyPanelPlugin extends BattleNext_BasePanelPlugin {

	private static final float SHELL_OUTER_INSET = BattleNext_PageLayout.SECTION_GAP;
	private static final float CLOSE_MAX_WIDTH = 180f;
	private static final float CLOSE_WIDTH_SHARE = 0.3f;

	private final Runnable closeAction;

	public BattleNext_EmptyPanelPlugin(Runnable closeAction) {
		this.closeAction = closeAction;
	}

	public void init(CustomPanelAPI panel, float width, float height) {
		initFrame(panel, width, height);

		float footerWidth = width - SHELL_OUTER_INSET * 2f;
		float closeWidth = Math.min(CLOSE_MAX_WIDTH, footerWidth * CLOSE_WIDTH_SHARE);
		UIRect closeRect = new UIRect(SHELL_OUTER_INSET + footerWidth - closeWidth,
				height - SHELL_OUTER_INSET - BattleNext_PageLayout.FOOTER_HEIGHT,
				closeWidth, BattleNext_PageLayout.FOOTER_HEIGHT);
		BattleNext_PageLayout.fixedButton(panel, closeRect,
				BattleNext_I18nSection.getInstance("Common", "BattleNext_common_").get("close"),
				"close", CutStyle.BOTTOM, false);
	}

	@Override
	public void positionChanged(PositionAPI position) {

	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			event.consume();
		}
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if ("close".equals(buttonId)) closeAction.run();
	}
}
