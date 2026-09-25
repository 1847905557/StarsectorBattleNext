package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionField.Option;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_OfficerSkillSourceProvider implements BattleNext_OptionField.Source {

	@Override
	public int getMaxCount(String id) {
		return 2;
	}

	@Override
	public List<Option> getOptions() {
		List<Option> options = new ArrayList<>();
		for (String id : Global.getSettings().getSortedSkillIds()) {
			SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
			if (spec == null) continue;

			if (spec.hasTag("BattleNext_forceAllowed")) {
				options.add(new Option(id, spec.getName()));
				continue;
			}

			if (spec.isAdminSkill()) continue;
			if (spec.hasTag(Skills.TAG_DEPRECATED)) continue;
			options.add(new Option(id, spec.getName()));
		}
		return options;
	}

	@Override
	public String getIcon(String id) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
		return spec == null ? "" : spec.getSpriteName();
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, Option option) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(option.getId());
		tooltip.addTitle(option.getLabel());
		tooltip.addPara(option.getId(), 5f, BattleNext_UiTheme.MUTED);
		if (spec != null && spec.getDescription() != null && !spec.getDescription().isBlank()) {
			tooltip.addPara(spec.getDescription(), 8f, BattleNext_UiTheme.TEXT);
		}
	}
}
