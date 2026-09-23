package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames;
import data.scripts.campaign.fad.Kratogen_MineralExplorationData;
import data.scripts.campaign.fad.Kratogen_TerraformData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryPlugin;
import data.scripts.campaign.bar.Kratogen_EventChainManager;
import data.scripts.campaign.bar.Kratogen_EventChainStageFirstContactBarEventCreator;
import data.scripts.campaign.bar.Kratogen_EventChainStageLeakBarEventCreator;
import data.scripts.campaign.intel.hostile.Kratogen_HostileActivityManager;
import data.scripts.kratogen.util.Kratogen_Database;
import data.scripts.kratogen.util.Kratogen_FontData;
import data.scripts.plugins.Kratogen_CampaignListener;
import data.scripts.plugins.Kratogen_CampaignPlugin;
import data.scripts.kratogen.util.Kratogen_Utils;
import data.scripts.kratogen.util.Kratogen_Utils.I18nSection;
import data.scripts.weapons.ai.*;
import data.scripts.world.systems.Kratogen_ArcadiaAddStation;
import data.scripts.world.Kratogen_SDFKratogen;
import data.scripts.world.Kratogen_ExplorationFleetManager;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;

public class KratogenModPlugin extends BaseModPlugin {

	public static final boolean EXP = false;

	public static final String CLASS_MARK = "KratogenModPlugin";

	public static final I18nSection strings = I18nSection.getInstance("Misc", "Kratogen_");

	@Override
	public void onApplicationLoad() {

		String version = Global.getSettings().getGameVersion();
		if (!version.startsWith("0.98a")) throw new IllegalArgumentException("改版本号？三界六道不就毁在这四个字上！");

		ShaderLib.init();

		if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
			LightData.readLightDataCSV("data/lights/Kratogen_light_data.csv");
			TextureData.readTextureDataCSV("data/lights/Kratogen_texture_data.csv");
		}
	}

	@Override
	public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
		return switch (missile.getProjectileSpecId()) {
			case "Kratogen_bise_lrm" ->
					new PluginPick<>(new Kratogen_BiseAI(missile, launchingShip), PickPriority.MOD_SET);
			case "Kratogen_borean_mrm" ->
					new PluginPick<>(new Kratogen_BoreanAI(missile, launchingShip), PickPriority.MOD_SET);
			case "Kratogen_thundershower_mrm" ->
					new PluginPick<>(new Kratogen_ThundershowerAI(missile, launchingShip), PickPriority.MOD_SET);
			case "Kratogen_flotation_mrm" ->
					new PluginPick<>(new Kratogen_FlotationAI(missile, launchingShip), PickPriority.MOD_SET);
			case "Kratogen_todo_future_mrm" ->
					new PluginPick<>(new Kratogen_NotUsedAI(missile, launchingShip), PickPriority.MOD_SET);
			case "Kratogen_emp_mine" ->
					new PluginPick<>(new Kratogen_EmpMineAI(missile, launchingShip), PickPriority.MOD_SET);
			default -> null;
		};
	}

	@Override
	public PluginPick<AutofireAIPlugin> pickWeaponAutofireAI(WeaponAPI weapon) {

		if (weapon == null) return null;
		if (weapon.getSpec() == null) return null;

		if (weapon.getSpec().hasTag("Kratogen_advanced_autofire")) return new PluginPick<>(new Kratogen_BaseAutofireController(weapon), PickPriority.MOD_SET);
		return null;
	}

	@Override
	public void onNewGame() {
		ProcgenUsedNames.notifyUsed("Bifrost");
		ProcgenUsedNames.notifyUsed("Idavoll");
		ProcgenUsedNames.notifyUsed("Uroarbrunnr");
		ProcgenUsedNames.notifyUsed("Lerad");
		ProcgenUsedNames.notifyUsed("Svadilfari");
		ProcgenUsedNames.notifyUsed("Lyngvi");
		ProcgenUsedNames.notifyUsed("Thrudvang");
		ProcgenUsedNames.notifyUsed("Heimdall");

		afterNewGameLoad = false;
		onGameLoad(true);
		afterNewGameLoad = true;
	}

	private boolean afterNewGameLoad = true;

	@Override
	public void onGameLoad(boolean newGame) {

		boolean classLoad = false;
		if (Global.getSector().getPersistentData().containsKey(CLASS_MARK)) {
			classLoad = (Boolean)Global.getSector().getPersistentData().get(CLASS_MARK);
		}

		if (!classLoad) {
			Global.getSector().getPersistentData().put(CLASS_MARK, true);

			if (Kratogen_Utils.NEX()) {
				new Kratogen_NEXGenerate().generate(Global.getSector());
			} else {
				new Kratogen_NormalGenerate().generate(Global.getSector());
			}

			SharedData.getData().getPersonBountyEventData().addParticipatingFaction("kratogen");
			Global.getSector().addScript(new Kratogen_CampaignListener());
			Global.getSector().addScript(new Kratogen_HostileActivityManager());
			Global.getSector().registerPlugin(new Kratogen_CampaignPlugin());
		}
		if (!Global.getSector().hasScript(Kratogen_ExplorationFleetManager.class)) {
			Global.getSector().addScript(new Kratogen_ExplorationFleetManager());
		}

		onDevModeF8Reload();

		Kratogen_EventChainManager.getAegle();
		Kratogen_EventChainManager.getRemis();
		Kratogen_EventChainManager.getHubbard();
		Kratogen_EventChainManager.getGammas();
		Kratogen_EventChainManager.getGlitchedPortrait();
		Kratogen_EventChainManager.getFakePerson();

		if (afterNewGameLoad) {
			BarEventManager manager = BarEventManager.getInstance();
			if (!manager.hasEventCreator(Kratogen_EventChainStageFirstContactBarEventCreator.class)) {
				manager.addEventCreator(new Kratogen_EventChainStageFirstContactBarEventCreator());
			}
			if (!manager.hasEventCreator(Kratogen_EventChainStageLeakBarEventCreator.class)) {
				manager.addEventCreator(new Kratogen_EventChainStageLeakBarEventCreator());
			}

			// market required, so not load in classLoad section
			if (!Global.getSector().hasScript(Kratogen_SDFKratogen.class)) {
				Global.getSector().addScript(new Kratogen_SDFKratogen());
			}
		}

		if (newGame) {
			Kratogen_EventChainManager.loadMeaningfulValue();
		}
	}

	@Override
	public void onDevModeF8Reload() {
		Kratogen_MineralExplorationData.init();
		Kratogen_TerraformData.init();
		Kratogen_FontData.init();
		//Kratogen_EventChainManager.load42();
	}

	@Override
	public void onNewGameAfterEconomyLoad() {
		Kratogen_ArcadiaAddStation.createInitialPeople();
	}

	@Override
	public void onCodexDataGenerated() {

		String id1 = CodexDataV2.getShipEntryId("Kratogen_station_3");
		CodexEntryPlugin entry1 = CodexDataV2.getEntry(id1);
		String id2 = CodexDataV2.getShipEntryId("Kratogen_station_3_ultra");
		CodexEntryPlugin entry2 = CodexDataV2.getEntry(id2);
		if (entry1 != null && entry2 != null) {
			entry1.addRelatedEntry(entry2);
			entry2.addRelatedEntry(entry1);
		}

		id1 = CodexDataV2.getShipSystemEntryId("Kratogen_blade_impact");
		entry1 = CodexDataV2.getEntry(id1);
		id2 = CodexDataV2.getShipSystemEntryId("Kratogen_stinger_impact");
		entry2 = CodexDataV2.getEntry(id2);
		if (entry1 != null && entry2 != null) {
			entry1.addRelatedEntry(entry2);
			entry2.addRelatedEntry(entry1);
		}

		id1 = CodexDataV2.getShipEntryId("Kratogen_frostcrystal");
		entry1 = CodexDataV2.getEntry(id1);
		id2 = CodexDataV2.getWeaponEntryId("Kratogen_tpa");
		entry2 = CodexDataV2.getEntry(id2);
		if (entry1 != null && entry2 != null) {
			entry1.addRelatedEntry(entry2);
			entry2.addRelatedEntry(entry1);
		}

		for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
			String id = Kratogen_Database.getNonDHullId(spec);
			if (id.startsWith("Kratogen_station_") && id.endsWith("_l")) {
				String idr = id.substring(0, id.length() - 2) + "_r";
				if (Global.getSettings().getHullSpec(idr) != null) {

					id1 = CodexDataV2.getShipEntryId(id);
					entry1 = CodexDataV2.getEntry(id1);
					id2 = CodexDataV2.getShipEntryId(idr);
					entry2 = CodexDataV2.getEntry(id2);
					if (entry1 != null && entry2 != null) {
						entry1.addRelatedEntry(entry2);
						entry2.addRelatedEntry(entry1);
					}
				}
			}
		}
	}
}
