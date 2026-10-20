package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_EntryId;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_ImportFleet implements BaseCommand {

	@Override
	public CommandResult runCommand(@NotNull String args, @NotNull CommandContext context) {

		if (context != CommandContext.CAMPAIGN_MAP) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}

		String id = BattleNext_EntryId.trimWhitespace(args);
		if (id.isEmpty()) return CommandResult.BAD_SYNTAX;

		BattleNext_FleetStore store = new BattleNext_FleetStore();
		try {
			store.reload();
		} catch (IOException | JSONException | RuntimeException exception) {
			Console.showMessage("Unable to load fleet catalog: " + exception.getMessage());
			return CommandResult.ERROR;
		}

		BattleNext_FleetModel definition = store.get(id);
		if (definition == null) {
			Console.showMessage("Fleet ID not found: " + id);
			return CommandResult.ERROR;
		}

		BattleNext_AssembledFleet assembled;
		try {
			assembled = new BattleNext_FleetBuilder().build(definition);
		} catch (RuntimeException exception) {
			Console.showMessage("Unable to build fleet " + id + ": " + exception.getMessage());
			return CommandResult.ERROR;
		}
		if (!assembled.isComplete()) {
			Console.showMessage("Unable to build fleet " + id + ": " + String.join("; ", assembled.getMemberErrors()));
			return CommandResult.ERROR;
		}

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		FleetDataAPI fleetData = playerFleet.getFleetData();
		List<PersonAPI> officers = new ArrayList<>();
		for (int index = 0; index < assembled.getMembers().size(); index++) {
			BattleNext_MemberModel member = definition.getMembers().get(index);
			if (!hasOfficerData(member)) continue;

			FleetMemberAPI ship = assembled.getMembers().get(index);
			PersonAPI officer = ship.getCaptain();
			if (!member.getPortrait().isEmpty()) {
				officer.setPortraitSprite(member.getPortrait());
			}
			FullName name = officer.getName();
			if (name == null || name.getFullName().isBlank()) {
				officer.setName(new FullName("Imported", "Officer", Gender.MALE));
			}
			officers.add(officer);
		}

		for (FleetMemberAPI member : assembled.getMembers()) {
			fleetData.addFleetMember(member);
		}
		for (PersonAPI officer : officers) {
			fleetData.addOfficer(officer);
		}
		playerFleet.forceSync();
		for (FleetMemberAPI member : assembled.getMembers()) {
			member.updateStats();
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		}

		Console.showMessage("Imported " + definition.getName() + " to player fleet: "
				+ assembled.getMembers().size() + " ships, " + officers.size() + " officers.");
		return CommandResult.SUCCESS;
	}

	private boolean hasOfficerData(BattleNext_MemberModel member) {
		return !member.getPersonality().isEmpty() || !member.getPortrait().isEmpty() || !member.getSkills().isEmpty();
	}
}
