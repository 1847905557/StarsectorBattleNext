package data.scripts.member;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_MemberBuilder {

	public FleetMemberAPI build(BattleNext_MemberModel definition) {
		validate(definition);
		ShipVariantAPI variant = Global.getSettings().getVariant(definition.getVariantId());
		if (variant == null) {
			throw new IllegalArgumentException("Unknown variant: " + definition.getVariantId());
		}

		FleetMemberAPI member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant);
		applyMemberData(member, definition);
		return member;
	}

	private void applyMemberData(FleetMemberAPI member, BattleNext_MemberModel definition) {
		if (!definition.getName().isEmpty()) {
			member.setShipName(definition.getName());
		}
		if (!definition.getPersonality().isEmpty()) {
			member.setPersonalityOverride(definition.getPersonality());
		}

		PersonAPI captain = Global.getSettings().createPerson();
		if (!definition.getPersonality().isEmpty()) {
			captain.setPersonality(definition.getPersonality());
		}
		if (!definition.getPortrait().isEmpty()) {
			captain.setPortraitSprite(definition.getPortrait());
		}
		applySkills(captain, definition);
		member.setCaptain(captain);
		member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
	}

	private void applySkills(PersonAPI captain, BattleNext_MemberModel definition) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String skill : definition.getSkills()) {
			counts.put(skill, counts.getOrDefault(skill, 0) + 1);
		}
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			float level = entry.getValue() > 1 ? 2f : 1f;
			captain.getStats().setSkillLevel(entry.getKey(), level);
		}
	}

	private void validate(BattleNext_MemberModel definition) {
		if (definition == null) {
			throw new IllegalArgumentException("Member definition is required");
		}
		if (definition.getVariantId().isEmpty()) {
			throw new IllegalArgumentException("Member variant_id is required");
		}
		if (!Global.getSettings().doesVariantExist(definition.getVariantId())) {
			throw new IllegalArgumentException("Unknown variant: " + definition.getVariantId());
		}
	}
}
