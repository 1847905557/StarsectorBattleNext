package data.scripts.ui;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;

public final class BattleNext_UiTheme {

	public static final Color ACCENT = new Color(70, 220, 235, 255);
	public static final Color PANEL = new Color(8, 18, 28, 235);
	public static final Color TEXT = new Color(220, 240, 245, 255);
	public static final Color MUTED = new Color(130, 165, 175, 255);

	private BattleNext_UiTheme() {
	}

	public static void configure(TooltipMakerAPI content) {
		content.setTitleFontColor(ACCENT);
		content.setParaFontColor(TEXT);
		content.setTitleOrbitronLarge();
		content.setParaSmallInsignia();
		content.setBgAlpha(0.92f);
	}
}
