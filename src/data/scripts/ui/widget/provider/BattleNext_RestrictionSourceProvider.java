package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.rule.BattleNext_RuleModel;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BattleNext_RestrictionSourceProvider extends BattleNext_BaseSourceProvider {

	private final Supplier<BattleNext_RuleModel> ruleSupplier;

	public BattleNext_RestrictionSourceProvider(Supplier<BattleNext_RuleModel> ruleSupplier) {
		this.ruleSupplier = ruleSupplier;
	}

	@Override
	public List<OptionChoice> getOptions() {
		BattleNext_RuleModel rule = ruleSupplier.get();
		List<OptionChoice> options = new ArrayList<>();
		for (BattleNext_RestrictionSpec spec : BattleNext_RestrictionRegistry.getInstance().getAll()) {
			if (rule.getRestriction(spec.getId()) != null) continue;
			options.add(new OptionChoice(spec.getId(), spec.getName()));
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
		BattleNext_RestrictionSpec spec = BattleNext_RestrictionRegistry.getInstance().get(option.getId());
		BattleNext_EquipmentTooltip.render(tooltip, new BattleNext_EquipmentTooltip.Content(
				spec.getName(), spec.getId() + "  [" + spec.getType() + "]", spec.getDescription(), List.of()));
	}
}
