package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.characters.SkillSpecAPI;

public class BattleNext_CommanderSkillSourceProvider extends BattleNext_SkillSourceProvider {

	@Override
	public boolean isSkillBanned(SkillSpecAPI spec) {
		return !spec.isCombatOfficerSkill();
	}
}
