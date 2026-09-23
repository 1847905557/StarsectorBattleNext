package data.scripts.battlesnext.util;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

public class BattleNext_LunaSettings {

	public static final String MOD_ID = "ai_battles_next";
	public static final String FULL_GRAPHIC = "BattleNext_fullGraphicEffect";

	public static boolean isFullGraphic() {
		if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
			return LunaSettings.getBoolean(MOD_ID, FULL_GRAPHIC);
		}
		return true;
	}
}
