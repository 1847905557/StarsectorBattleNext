package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.characters.SkillSpecAPI.SkillEffectSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class BattleNext_SkillSourceProvider extends BattleNext_BaseSourceProvider {

	private static final float ADVANCED_SKILL_LEVEL_THRESHOLD = 1f;
	private static final int ADVANCED_SKILL_MAX_STACK = 2;
	private static final int BASIC_SKILL_MAX_STACK = 1;
	private static final float SCOPE_GAP = 5f;
	private static final float TOOLTIP_WIDTH = 520f;
	private static final float AUTHOR_GAP = 5f;
	private static final float EFFECT_GROUP_GAP = 8f;
	private static final float EFFECT_GAP = 5f;
	private final BattleNext_I18nSection commonStrings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	@Override
	public int getMaxStack(String id) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
		if (spec == null) return 0;

		List<SkillEffectSpecAPI> effects = spec.getEffectsAPI();
		for (SkillEffectSpecAPI effect : effects) {
			if (!hasDisplayEffect(effect)) continue;

			if (effect.getRequiredSkillLevel() > ADVANCED_SKILL_LEVEL_THRESHOLD) {
				return ADVANCED_SKILL_MAX_STACK;
			}
		}

		return BASIC_SKILL_MAX_STACK;
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
	public float getTooltipWidth() {
		return TOOLTIP_WIDTH;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		SkillSpecAPI spec = Global.getSettings().getSkillSpec(option.getId());
		addHeader(tooltip, option.getLabel(), option.getId());
		if (spec == null) return;
		String scope = String.join(" - ", Stream.of(spec.getScopeStr(), spec.getScopeStr2())
				.filter(value -> value != null && !value.isBlank()).toList());
		addDescription(tooltip, spec.getDescription());
		tooltip.setParaFontDefault();
		tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
		String author = spec.getAuthor();
		if (author != null && !author.isBlank()) {
			tooltip.addPara(author, BattleNext_UiTheme.TEXT, AUTHOR_GAP).setAlignment(Alignment.RMID);
		}
		if (!scope.isEmpty()) {
			tooltip.addPara(scope, BattleNext_UiTheme.TEXT, SCOPE_GAP);
		}
		addEffects(tooltip, spec);
	}

	private boolean hasDisplayEffect(SkillEffectSpecAPI effect) {
		return effect.getType() != SkillEffectType.ABILITY_UNLOCK
				&& effect.getType() != SkillEffectType.HULLMOD_UNLOCK
				&& effect.getType() != SkillEffectType.DESCRIPTION;
	}

	private void addEffects(TooltipMakerAPI tooltip, SkillSpecAPI spec) {

		List<SkillEffectSpecAPI> effects = spec.getEffectsAPI();
		if (effects.stream().noneMatch(this::hasDisplayEffect)) return;
		MutableCharacterStatsAPI preview = Global.getSettings().createPerson().getStats();
		preview.setSkipRefresh(true);
		float previousLevel = Float.NaN;
		String previousGroup = null;
		for (SkillEffectSpecAPI effect : effects) {
			if (!hasDisplayEffect(effect)) continue;
			float requiredLevel = effect.getRequiredSkillLevel();
			float previewLevel = Math.max(BASIC_SKILL_MAX_STACK, requiredLevel);
			String group = Objects.requireNonNullElse(effect.getName(), "");
			try {
				if (previousLevel != requiredLevel || !Objects.equals(previousGroup, group)) {
					String heading = group == null || group.isBlank()
							? commonStrings.format("skill_effect_level", BattleNext_I18nSection.buildFactor(previewLevel)) : group;
					Color headingColor = "Elite".equals(group) ? Misc.getStoryOptionColor() : BattleNext_UiTheme.ACCENT;
					tooltip.addPara(heading, headingColor, EFFECT_GROUP_GAP);
					previousLevel = requiredLevel;
					previousGroup = group;
				}
				preview.setSkillLevel(spec.getId(), previewLevel);
				LevelBasedEffect script = effect.getAsLevelBasedEffect();
				if (script instanceof CustomSkillDescription custom && custom.hasCustomDescription()) {
					tooltip.addSpacer(EFFECT_GAP);
					custom.createCustomDescription(preview, spec, tooltip, tooltip.getWidthSoFar());
				} else {
					String description = script.getEffectDescription(effect.isLevelBased() ? previewLevel : requiredLevel);
					if (description != null && !description.isBlank()) {
						boolean shipEffect = switch (effect.getType()) {
							case SHIP, ALL_SHIPS_IN_FLEET, ALL_FIGHTERS_IN_FLEET, SHIP_FIGHTERS -> true;
							default -> false;
						};
						Color color = shipEffect && script.getScopeDescription() == null
								? Misc.getTextColor() : Misc.getHighlightColor();
						tooltip.addPara(description, color, EFFECT_GAP);
					}
				}
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_SkillSourceProvider.class, "skill.effect.description.degraded",
						BattleNext_Logger.fields().put("skill_id", spec.getId()).put("effect_class", effect.getEffectClass())
								.put("effect_type", effect.getType()).put("required_level", requiredLevel)
								.put("preview_level", previewLevel).put("group", group).put("level_based", effect.isLevelBased()).build(), exception);
				tooltip.setParaFontDefault();
				tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
				tooltip.addPara(commonStrings.get("load_failed") + ": " + exception, BattleNext_UiTheme.TEXT, EFFECT_GAP);
			} finally {
				tooltip.setParaFontDefault();
				tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
			}
		}
	}
}
