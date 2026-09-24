package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.member.BattleNext_MemberBuilder;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetAssembler {

	private final BattleNext_MemberBuilder memberBuilder;

	public BattleNext_FleetAssembler() {
		memberBuilder = new BattleNext_MemberBuilder();
	}

	public BattleNext_FleetAssembly assemble(BattleNext_FleetModel definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Fleet definition is required");
		}
		PersonAPI commander = createCommander(definition);
		List<FleetMemberAPI> members = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (int index = 0; index < definition.getMembers().size(); index++) {
			try {
				members.add(memberBuilder.build(definition.getMembers().get(index)));
			} catch (RuntimeException exception) {
				errors.add("member[" + index + "]: " + exception.getMessage());
			}
		}
		return new BattleNext_FleetAssembly(definition, commander, members, errors);
	}

	public BattleNext_FleetAssembly forceSync(BattleNext_FleetAssembly current, BattleNext_FleetModel definition) {
		return assemble(definition);
	}

	private PersonAPI createCommander(BattleNext_FleetModel definition) {
		PersonAPI commander = Global.getSettings().createPerson();
		for (String skill : definition.getSkills()) {
			commander.getStats().setSkillLevel(skill, 1f);
		}
		return commander;
	}
}
