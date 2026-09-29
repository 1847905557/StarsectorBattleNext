package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public class BattleNext_UiTheme {

	private static final float CONTENT_BACKGROUND_ALPHA = 0.92f;
	public static final String BUTTON_HOVER_SOUND_ID = "ui_button_mouseover";
	public static final String BUTTON_PRESSED_SOUND_ID = "ui_button_pressed";

	public static final Color ACCENT = Misc.getButtonTextColor();
	public static final Color PANEL = Global.getSettings().getColor("buttonBgDark");
	public static final Color HEADER = Misc.getDarkPlayerColor();
	public static final Color TEXT = Misc.getTextColor();
	public static final Color MUTED = Global.getSettings().getColor("standardUIIconColor");

	private BattleNext_UiTheme() {
	}

	public static void configure(TooltipMakerAPI content) {
		content.setTitleFontColor(ACCENT);
		content.setParaFontColor(TEXT);
		content.setTitleOrbitronLarge();
		content.setParaSmallInsignia();
		content.setBgAlpha(CONTENT_BACKGROUND_ALPHA);
	}
}
