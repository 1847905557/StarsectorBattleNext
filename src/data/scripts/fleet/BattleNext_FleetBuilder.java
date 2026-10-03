package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberBuilder;
import data.scripts.member.BattleNext_PersonSkills;

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
				errors.add("member[" + index + "]: " + exception.getMessage());
			}
		}
		return new BattleNext_AssembledFleet(definition, commander, members, errors);
	}

	public BattleNext_AssembledFleet rebuild(BattleNext_AssembledFleet currentFleet, BattleNext_FleetModel definition) {
		return build(definition);
	}

	private PersonAPI createCommander(BattleNext_FleetModel definition) {
		PersonAPI commander = Global.getSettings().createPerson();
		BattleNext_PersonSkills.apply(commander, definition.getSkills());
		return commander;
	}
}
