package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.member.BattleNext_MemberBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_FleetAssembly {

	private final BattleNext_FleetModel definition;
	private final PersonAPI commander;
	private final List<FleetMemberAPI> members;
	private final List<String> memberErrors;

	public BattleNext_FleetAssembly(BattleNext_FleetModel definition, PersonAPI commander,
			List<FleetMemberAPI> members, List<String> memberErrors) {
		this.definition = definition;
		this.commander = commander;
		this.members = List.copyOf(members);
		this.memberErrors = List.copyOf(memberErrors);
	}

	public BattleNext_FleetModel getDefinition() { return definition; }
	public PersonAPI getCommander() { return commander; }
	public List<FleetMemberAPI> getMembers() { return Collections.unmodifiableList(members); }
	public List<String> getMemberErrors() { return Collections.unmodifiableList(memberErrors); }
	public boolean isComplete() { return memberErrors.isEmpty() && members.size() == definition.getMembers().size(); }
}
