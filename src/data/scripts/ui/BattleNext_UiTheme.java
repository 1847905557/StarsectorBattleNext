package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public final class BattleNext_UiTheme {

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
		content.setBgAlpha(0.92f);
	}
}
