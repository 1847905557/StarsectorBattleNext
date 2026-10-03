package data.scripts.campaign;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.state.AppDriver;
import org.boxutil.backends.reflect.BUtil_RefMethod;

import java.util.List;

public record BattleNext_CampaignRefitContext(UIPanelAPI host, Object refitPanel,
		FleetMemberAPI member, ShipVariantAPI variant) {

	static BattleNext_CampaignRefitContext current() throws Throwable {
		Object state = AppDriver.getInstance().getCurrentState();
		if (!(state instanceof CampaignState)) throw new IllegalStateException("Campaign refit is not active");
		Object dialog = invoke(state, "getEncounterDialog");
		Object core = dialog == null ? invoke(state, "getCore") : invoke(dialog, "getCoreUI");
		return from(core, dialog == null ? (UIPanelAPI) core : (UIPanelAPI) dialog);
	}

	static BattleNext_CampaignRefitContext from(Object core, UIPanelAPI host) throws Throwable {
		Object tab = invoke(core, "getCurrentTab");
		for (Object child : childrenOf(tab)) {
			if (!hasMethod(child, "syncWithCurrentVariant")) continue;
			FleetMemberAPI member = (FleetMemberAPI) invoke(child, "getMember");
			Object shipDisplay = invoke(child, "getShipDisplay");
			ShipVariantAPI variant = (ShipVariantAPI) invoke(shipDisplay, "getCurrentVariant");
			return new BattleNext_CampaignRefitContext(host, child, member, variant);
		}
		throw new IllegalStateException("Campaign refit panel is unavailable");
	}

	static UIPanelAPI parentOf(UIPanelAPI panel) throws Throwable {
		return (UIPanelAPI) invoke(panel, "getParent");
	}

	static List<?> childrenOf(Object panel) throws Throwable {
		return (List<?>) invoke(panel, "getChildrenCopy");
	}

	private static boolean hasMethod(Object target, String name) throws Throwable {
		for (Object method : target.getClass().getMethods()) {
			if (BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) return true;
		}
		return false;
	}

	private static Object invoke(Object target, String name) throws Throwable {
		for (Object method : target.getClass().getMethods()) {
			if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) continue;
			return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, target);
		}
		throw new NoSuchMethodException(name);
	}
}
