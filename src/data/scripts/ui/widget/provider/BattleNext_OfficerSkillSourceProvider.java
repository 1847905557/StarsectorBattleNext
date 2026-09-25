package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionField.Option;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_CommanderSkillSourceProvider implements BattleNext_OptionField.Source {

	private final int maxCount;

	public BattleNext_CommanderSkillSourceProvider() {
		this(1);
	}

	public BattleNext_CommanderSkillSourceProvider(int maxCount) {
		this.maxCount = maxCount;
	}

	@Override
	public int getMaxCount(String id) {
		return maxCount;
	}

	@Override
	public List<Option> getOptions() {
		List<BattleNext_OptionField.Option> options = new ArrayList<>();
		for (String id : Global.getSettings().getSortedSkillIds()) {
			SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
			if (spec == null) continue;
			options.add(new BattleNext_OptionField.Option(id, spec.getName()));
		}
		return options;
	}

	@Override
	public String getIcon(String id) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
		return spec == null ? "" : spec.getSpriteName();
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionField.Option option) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(option.getId());
		tooltip.addTitle(option.getLabel());
		tooltip.addPara(option.getId(), 5f, BattleNext_UiTheme.MUTED);
		if (spec != null && spec.getDescription() != null && !spec.getDescription().isBlank()) {
			tooltip.addPara(spec.getDescription(), 8f, BattleNext_UiTheme.TEXT);
		}
	}
}
