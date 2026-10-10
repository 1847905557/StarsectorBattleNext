package data.scripts.member;

import com.fs.starfarer.api.characters.PersonAPI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_PersonSkills {

	private BattleNext_PersonSkills() {

	}

	public static List<String> capture(PersonAPI person) {
		List<String> skills = new ArrayList<>();
		for (var skill : person.getStats().getSkillsCopy()) {
			if (skill.getLevel() <= 0f) continue;
			String id = skill.getSkill().getId();
			skills.add(id);
			if (skill.getLevel() > 1f) {
				skills.add(id);
			}
		}
		return skills;
	}

	public static void apply(PersonAPI person, List<String> skills) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String skill : skills) {
			counts.put(skill, counts.getOrDefault(skill, 0) + 1);
		}

		person.getStats().setLevel(0);
		for (var entry : counts.entrySet()) {
			person.getStats().setSkillLevel(entry.getKey(), entry.getValue() > 1 ? 2f : 1f);
			person.getStats().setLevel(person.getStats().getLevel() + 1);
		}

		if (person.getStats().getLevel() > 0 && person.isDefault()) {
			person.setPortraitSprite("graphics/portraits/portrait_generic.png");
		}
	}
}
