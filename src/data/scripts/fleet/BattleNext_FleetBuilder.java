package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberBuilder;
import data.scripts.member.BattleNext_MemberModel;
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

		PersonAPI commander = createCommander(definition);
		List<FleetMemberAPI> members = new ArrayList<>();
		List<String> errors = new ArrayList<>(definition.getLoadErrorsCopy());
		List<BattleNext_MemberModel> definitions = definition.getMembersReadonly();
		for (int index = 0; index < definitions.size(); index++) {
			try {
				String variantId = definitions.get(index).getVariantId();
				BattleNext_VariantBuilder localVariant = definition.getVariant(variantId);
				if (localVariant != null) {
					ShipVariantAPI variant = localVariant.build();
					members.add(memberBuilder.build(definitions.get(index), variant));
				} else {
					members.add(memberBuilder.build(definitions.get(index)));
				}
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_FleetBuilder.class, "fleet.member.assembly.failed",
				BattleNext_Logger.fields().put("fleet_id", definition.getId()).put("member_index", index).put("member", definitions.get(index)).build(), exception);
				errors.add("member[" + index + "]: " + exception.getMessage());
			}
		}
		BattleNext_Logger.info(BattleNext_FleetBuilder.class, "fleet.assembly.completed",
				() -> BattleNext_Logger.fields().put("definition", definition).put("commander", commander).put("members", members).put("errors", errors).build());
		return new BattleNext_AssembledFleet(definition, commander, members, errors);
	}

	public void addToMission(MissionDefinitionAPI api, FleetSide side, BattleNext_AssembledFleet fleet, String tagline) {
		api.initFleet(side, "", FleetGoal.ATTACK, true, 1);
		api.setFleetTagline(side, tagline);
		for (FleetMemberAPI member : fleet.getMembersReadonly()) api.addFleetMember(side, member);

		PersonAPI commander = api.getDefaultCommander(side);
		BattleNext_PersonSkills.apply(commander, fleet.getDefinition().getSkillsReadonly());
		for (FleetMemberAPI member : fleet.getMembersReadonly()) {
			member.setFleetCommanderForStats(commander, null);
			member.getCrewComposition().addCrew(member.getMinCrew());
			member.updateStats();
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		}
	}

	private PersonAPI createCommander(BattleNext_FleetModel definition) {
		PersonAPI commander = Global.getSettings().createPerson();
		BattleNext_PersonSkills.apply(commander, definition.getSkillsReadonly());
		return commander;
	}
}
