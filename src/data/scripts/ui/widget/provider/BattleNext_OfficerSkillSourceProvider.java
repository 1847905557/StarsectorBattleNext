package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.characters.SkillSpecAPI;

public class BattleNext_OfficerSkillSourceProvider extends BattleNext_SkillSourceProvider {

	@Override
	public boolean isSkillBanned(SkillSpecAPI spec) {
		return !spec.isCombatOfficerSkill();
	}
}
