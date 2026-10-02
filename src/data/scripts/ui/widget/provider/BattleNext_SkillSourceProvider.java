package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.SkillEffectType;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI.SkillEffectSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BattleNext_SkillSourceProvider extends BattleNext_BaseSourceProvider {
	private static final float ADVANCED_SKILL_LEVEL_THRESHOLD = 1f;
	private static final int ADVANCED_SKILL_MAX_COUNT = 2;
	private static final int BASIC_SKILL_MAX_COUNT = 1;
	private static final float SCOPE_GAP = 5f;
	private static final float SCOPE_RESERVE = 26f;

	@Override
	public int getMaxCount(String id) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
		if (spec == null) return 0;

		List<SkillEffectSpecAPI> effects = spec.getEffectsAPI();
		for (SkillEffectSpecAPI effect : effects) {
			if (effect.getType() == SkillEffectType.ABILITY_UNLOCK) continue;
			if (effect.getType() == SkillEffectType.HULLMOD_UNLOCK) continue;
			if (effect.getType() == SkillEffectType.DESCRIPTION) continue;

			if (effect.getRequiredSkillLevel() > ADVANCED_SKILL_LEVEL_THRESHOLD) {
				return ADVANCED_SKILL_MAX_COUNT;
			}
		}

		return BASIC_SKILL_MAX_COUNT;
	}

	@Override
	public List<OptionChoice> getOptions() {

		List<OptionChoice> options = new ArrayList<>();
		for (String id : Global.getSettings().getSortedSkillIds()) {
			SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
			if (spec == null) continue;
			if (spec.isAptitudeEffect()) continue;

			if (spec.hasTag("BattleNext_forceAllowed")) {
				options.add(new OptionChoice(id, spec.getName()));
				continue;
			}

			if (isSkillBanned(spec)) continue;
			if (spec.hasTag(Skills.TAG_DEPRECATED)) continue;
			if (spec.hasTag(Skills.TAG_NPC_ONLY) && spec.hasTag(Skills.TAG_PLAYER_ONLY)) continue;
			options.add(new OptionChoice(id, spec.getName()));
		}

		return options;
	}

	public boolean isSkillBanned(SkillSpecAPI spec) {
		return true;
	}

	@Override
	public String getIconPath(String id) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
		return spec == null ? "" : spec.getSpriteName();
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(option.getId());
		addHeader(tooltip, option.getLabel(), option.getId());
		if (spec == null) return;
		String scope = String.join(" - ", Stream.of(spec.getScopeStr(), spec.getScopeStr2())
				.filter(value -> value != null && !value.isBlank()).toList());
		addExcerpt(tooltip, spec.getDescription(), scope.isEmpty() ? 0f : SCOPE_RESERVE);
		if (!scope.isEmpty() && tooltip.getHeightSoFar() + SCOPE_RESERVE <= tooltip.getPosition().getHeight()) {
			tooltip.addPara(scope, SCOPE_GAP);
		}
	}
}
