package data.scripts.member;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import data.scripts.tools.BattleNext_Logger;

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

	public FleetMemberAPI build(BattleNext_MemberModel definition, ShipVariantAPI variant) {
		if (definition == null || variant == null) {
			throw new IllegalArgumentException("Member definition and variant are required");
		}
		FleetMemberAPI member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, variant);
		applyMemberData(member, definition);
		return member;
	}

	private void applyMemberData(FleetMemberAPI member, BattleNext_MemberModel definition) {
		String name = definition.getName();
		if (name.isBlank()) {
			name = member.getVariant().getHullSpec().getHullName();
			BattleNext_Logger.warn(BattleNext_MemberBuilder.class, "member.name.defaulted", BattleNext_Logger.fields()
					.put("member", definition).put("hull_id", member.getVariant().getHullSpec().getHullId())
					.put("name", name).build());
		}
		member.setShipName(name);
		if (!definition.getPersonality().isEmpty()) {
			member.setPersonalityOverride(definition.getPersonality());
		}

		PersonAPI officer = Global.getSettings().createPerson();
		if (!definition.getPersonality().isEmpty()) {
			officer.setPersonality(definition.getPersonality());
		}
		if (!definition.getPortrait().isEmpty()) {
			officer.setPortraitSprite(definition.getPortrait());
		}

		BattleNext_PersonSkills.apply(officer, definition.getSkills());
		member.setCaptain(officer);

		member.getRepairTracker().setMothballed(false);
		member.getRepairTracker().setCrashMothballed(false);
		member.getCrewComposition().setCrew(member.getMaxCrew());
		member.updateStats();
		member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
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
