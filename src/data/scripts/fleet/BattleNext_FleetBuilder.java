package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberBuilder;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.tools.BattleNext_Logger;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetBuilder {

	private final BattleNext_MemberBuilder memberBuilder;

	public BattleNext_FleetBuilder() {
		memberBuilder = new BattleNext_MemberBuilder();
	}

	public BattleNext_AssembledFleet build(BattleNext_FleetModel definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Fleet definition is required");
		}
		PersonAPI commander = createCommander(definition);
		List<FleetMemberAPI> members = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (int index = 0; index < definition.getMembers().size(); index++) {
			try {
				String variantId = definition.getMembers().get(index).getVariantId();
				BattleNext_VariantBuilder localVariant = definition.getVariant(variantId);
				if (localVariant != null) {
					ShipVariantAPI variant = localVariant.build();
					members.add(memberBuilder.build(definition.getMembers().get(index), variant));
				} else {
					members.add(memberBuilder.build(definition.getMembers().get(index)));
				}
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_FleetBuilder.class, "fleet.member.assembly.failed",
				BattleNext_Logger.fields().put("fleet_id", definition.getId()).put("member_index", index).put("member", definition.getMembers().get(index)).build(), exception);
				errors.add("member[" + index + "]: " + exception.getMessage());
			}
		}
		BattleNext_Logger.info(BattleNext_FleetBuilder.class, "fleet.assembly.completed",
				BattleNext_Logger.fields().put("definition", definition).put("commander", commander).put("members", members).put("errors", errors).build());
		return new BattleNext_AssembledFleet(definition, commander, members, errors);
	}

	public BattleNext_AssembledFleet rebuild(BattleNext_AssembledFleet currentFleet, BattleNext_FleetModel definition) {
		return build(definition);
	}

	public void addToMission(MissionDefinitionAPI api, FleetSide side, BattleNext_AssembledFleet fleet, String tagline) {
		api.initFleet(side, "", FleetGoal.ATTACK, fleet.getDefinition().getTags().contains("default_ai"), 1);
		api.setFleetTagline(side, tagline);
		for (FleetMemberAPI member : fleet.getMembers()) {
			api.addFleetMember(side, member);
		}
		BattleNext_PersonSkills.apply(api.getDefaultCommander(side), fleet.getDefinition().getSkills());
	}

	private PersonAPI createCommander(BattleNext_FleetModel definition) {
		PersonAPI commander = Global.getSettings().createPerson();
		BattleNext_PersonSkills.apply(commander, definition.getSkills());
		return commander;
	}
}
