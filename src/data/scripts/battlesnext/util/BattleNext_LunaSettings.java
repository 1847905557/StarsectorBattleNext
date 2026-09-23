package data.scripts.kratogen.util;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;

public class Kratogen_LunaSettings {

	public static final String MOD_ID = "Kratogen_TA";
	public static final String FULL_GRAPHIC = "Kratogen_fullGraphicEffect";

	public static boolean isFullGraphic() {
		if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
			return LunaSettings.getBoolean(MOD_ID, FULL_GRAPHIC);
		}
		return true;
	}
}
