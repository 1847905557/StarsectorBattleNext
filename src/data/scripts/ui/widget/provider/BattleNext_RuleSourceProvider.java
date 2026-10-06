package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_RuleSourceProvider extends BattleNext_BaseSourceProvider {

	private static final float BODY_GAP = 8f;
	private static final float ROW_GAP = 5f;
	private static final float BOTTOM_INSET = 8f;

	private final BattleNext_RuleStore ruleStore;
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");
	private final BattleNext_I18nSection commonStrings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");

	public BattleNext_RuleSourceProvider(BattleNext_RuleStore ruleStore) {
		this.ruleStore = ruleStore;
	}

	@Override
	public List<OptionChoice> getOptions() {
		List<OptionChoice> options = new ArrayList<>();
		for (BattleNext_RuleModel rule : ruleStore.getAll()) {
			options.add(new OptionChoice(rule.getId(), rule.getName()));
		}
		return options;
	}

	@Override
	public int getMaxStack(String id) {
		return 1;
	}

	@Override
	public String getIconPath(String id) {
		return "";
	}

	@Override
	public boolean expectsIcon() {
		return false;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {

		BattleNext_RuleModel rule = ruleStore.get(option.getId());
		if (rule == null) {
			BattleNext_Logger.warn(BattleNext_RuleSourceProvider.class, "rule.tooltip.unavailable",
					BattleNext_Logger.fields().put("rule_id", option.getId()).build(),
					new IllegalArgumentException("Unknown rule: " + option.getId()));
			BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(
					option.getLabel(), option.getId(), "", List.of(commonStrings.get("unavailable"))));
			return;
		}
		tooltip.setTitleSmallOrbitron();
		tooltip.setTitleFontColor(BattleNext_UiTheme.ACCENT);
		addHeader(tooltip, rule.getName(), rule.getId());
		tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
		tooltip.addPara(ruleStrings.get("restrictions") + ": " + rule.getRestrictions().size(),
				BattleNext_UiTheme.ACCENT, BODY_GAP);
		for (BattleNext_RuleRestriction restriction : rule.getRestrictions()) {
			BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(restriction.getId());
			String description = spec == null ? ruleStrings.get("missing_restriction") : spec.getDescription();
			tooltip.addPara(description, BattleNext_UiTheme.TEXT, BODY_GAP);
			BattleNext_PageLayout.addMutedIdLine(tooltip, restriction.getId() + "  [" + restriction.getType() + "]", ROW_GAP);
			ButtonAPI enabledCheckbox = tooltip.addCheckbox(getTooltipWidth(), BattleNext_PageLayout.FORM_FIELD_HEIGHT,
					commonStrings.get("enabled"), restriction.getId(), ButtonAPI.UICheckboxSize.SMALL, ROW_GAP);
			enabledCheckbox.setChecked(restriction.isEnabled());
			enabledCheckbox.setEnabled(false);
			String value = "number".equals(restriction.getType()) ? Double.toString(restriction.getValue())
					: String.join(",", restriction.getItems());
			tooltip.addPara(ruleStrings.get("value_or_items") + ": " + value, BattleNext_UiTheme.TEXT, ROW_GAP);
			if (spec == null) {
				BattleNext_Logger.warn(BattleNext_RuleSourceProvider.class, "rule.tooltip.restriction.unavailable",
						BattleNext_Logger.fields().put("rule_id", rule.getId()).put("restriction_id", restriction.getId())
								.put("type", restriction.getType()).put("enabled", restriction.isEnabled())
								.put("value", restriction.getValue()).put("items", restriction.getItems()).build());
			}
		}
		tooltip.addSpacer(BOTTOM_INSET);
	}
}
