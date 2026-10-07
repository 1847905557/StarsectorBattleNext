package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.io.BattleNext_FleetSaver;
import data.scripts.ui.BattleNext_UiInputRouter;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BattleNext_TestCampaignFleetFixture extends BattleNext_TestMemberFixture {

	protected Variant activeVariant;
	protected Variant otherVariant;
	protected FleetMemberAPI selected;
	protected CampaignFleetAPI fleet;
	protected PersonAPI player;
	protected PersonAPI officer;
	protected PersonAPI core;
	protected CoreUITabId tab = CoreUITabId.REFIT;
	protected final List<FleetMemberAPI> fleetMembers = new ArrayList<>();
	protected final List<Object> scripts = new ArrayList<>();

	@BeforeEach
	protected void configurePlayerFleet() throws Exception {

		activeVariant = new Variant("parent_variant", hulls.get("parent"));
		activeVariant.getJson().put("displayName", "Active draft");
		activeVariant.getJson().put("wings", selectedVariant.getJson().getJSONArray("wings"));
		activeVariant.getJson().put("weaponGroups", selectedVariant.getJson().getJSONArray("weaponGroups"));
		activeVariant.getApi().setNumFluxVents(42);
		activeVariant.getApi().addMod("ordinary");
		activeVariant.getApi().addPermaMod("sm", true);
		activeVariant.getApi().addPermaMod("active_dmod", false);
		activeVariant.getApi().getSModdedBuiltIns().add("built");
		activeVariant.getApi().getSuppressedMods().add("d");
		activeVariant.getApi().setModuleVariant("module", selectedVariant.getModules().get("module"));
		otherVariant = new Variant("parent_variant", hulls.get("parent"));
		otherVariant.getApi().setNumFluxVents(13);
		player = person("Player", "player.png", "aggressive", false, true, false,
				Map.of("combat", 2f, "fleet", 1f, "industry", 1f, "unlearned", 0f));
		officer = person("Officer", "officer.png", "reckless", false, false, false,
				Map.of("gunnery", 2f, "helmsmanship", 1f));
		core = person("Core", "core.png", "reckless", false, false, true, Map.of("core_skill", 2f));
		selected = member("Officer ship", selectedVariant.getApi(), officer, false);
		fleetMembers.add(selected);
		fleetMembers.add(member("Player ship", otherVariant.getApi(), player, false));
		fleetMembers.add(member("Core ship", otherVariant.getApi(), core, false));
		fleetMembers.add(member("Civilian mothballed", otherVariant.getApi(),
				person("Default", "default.png", "steady", true, false, false, Map.of()), true));
		fleetMembers.add(member("Unassigned", otherVariant.getApi(), null, false));
		FleetDataAPI data = proxy(FleetDataAPI.class, (method, args) -> switch (method) {
			case "getMembersListCopy" -> new ArrayList<>(fleetMembers);
			case "getCombatReadyMembersListCopy" -> List.of(selected);
			default -> null;
		});
		fleet = proxy(CampaignFleetAPI.class, (method, args) -> switch (method) {
			case "getName" -> "Player fleet";
			case "getFleetData" -> data;
			default -> null;
		});
		Global.setSector(proxy(SectorAPI.class, (method, args) -> switch (method) {
			case "getPlayerFleet" -> fleet;
			case "getPlayerPerson" -> player;
			case "getCampaignUI" -> proxy(CampaignUIAPI.class, (name, values) ->
					"getCurrentCoreTab".equals(name) ? tab : null);
			case "addTransientScript" -> scripts.add(args[0]);
			case "removeTransientScript" -> scripts.remove(args[0]);
			default -> null;
		}));
	}

	@AfterEach
	protected void releaseCampaignRouter() {
		BattleNext_UiInputRouter.activate(null);
	}

	public static class FailingFleetStore extends BattleNext_FleetStore {
		private boolean failLoad;
		private boolean failWrite = true;
		private final BattleNext_FleetSaver writer = new BattleNext_FleetSaver() {
			@Override
			public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {
				if (failWrite) throw new IOException("Injected fleet write failure");
				return super.save(fleet);
			}
		};

		public void setFailLoad(boolean value) {
			failLoad = value;
		}

		public void setFailWrite(boolean value) {
			failWrite = value;
		}

		@Override
		public void reload() throws IOException, JSONException {
			if (failLoad) throw new IOException("Injected fleet load failure");
			super.reload();
		}

		@Override
		protected BattleNext_FleetSaver saver() {
			return writer;
		}
	}
}
