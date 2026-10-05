package data.scripts.campaign;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.tools.BattleNext_Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class BattleNext_CampaignFleetSnapshot {

	private BattleNext_CampaignFleetSnapshot() {

	}

	static BattleNext_FleetModel capture(CampaignFleetAPI fleet, PersonAPI player,
			FleetMemberAPI selected, ShipVariantAPI current) throws JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("player", player).put("fleet_id", fleet.getId()).put("fleet_name", fleet.getName()).put("selected_member", selected).put("current_variant", current).build();
		BattleNext_Logger.operationStarted(BattleNext_CampaignFleetSnapshot.class, "fleet.player.snapshot", input);
		try {

			String id = "fleet_" + UUID.randomUUID().toString().replace("-", "");
			String playerName = player.getNameString();
			String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
			BattleNext_FleetModel snapshot = new BattleNext_FleetModel(id, playerName + "-" + time,
					BattleNext_PersonSkills.capture(player), List.of(), List.of());
			snapshot.setAuthor(playerName);
			List<BattleNext_MemberModel> members = new ArrayList<>();
			for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
				ShipVariantAPI variant = member == selected ? current : member.getVariant();
				JSONObject json = BattleNext_VariantBuilder.fromVariant(variant).toJson();
				String variantId = id + "_ship_" + (members.size() + 1);
				assignIds(json, variantId);
				BattleNext_VariantBuilder builder = BattleNext_VariantBuilder.fromJson(json, variantId);
				builder.build();
				snapshot.putVariant(builder);
				PersonAPI officer = member.getCaptain();
				boolean assigned = officer != null && !officer.isDefault();
				members.add(new BattleNext_MemberModel(variantId, member.getShipName(),
						assigned ? officer.getPersonalityAPI().getId() : "",
						assigned ? BattleNext_PersonSkills.capture(officer) : List.of(),
						assigned ? officer.getPortraitSprite() : "", "", List.of()));
			}
			snapshot.setMembers(members);
			BattleNext_Logger.operationSucceeded(BattleNext_CampaignFleetSnapshot.class, "fleet.player.snapshot",
					BattleNext_Logger.fields().put("fleet", snapshot).build());
			return snapshot;
		} catch (JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_CampaignFleetSnapshot.class, "fleet.player.snapshot", input, exception);
			throw exception;
		}
	}

	private static void assignIds(JSONObject json, String id) throws JSONException {
		json.put("variantId", id);
		JSONObject modules = json.optJSONObject("modules");
		if (modules == null || modules.length() == 0) return;
		String[] slots = JSONObject.getNames(modules);
		Arrays.sort(slots);
		for (int index = 0; index < slots.length; index++) {
			assignIds(modules.getJSONObject(slots[index]), id + "_module_" + (index + 1));
		}
	}
}
