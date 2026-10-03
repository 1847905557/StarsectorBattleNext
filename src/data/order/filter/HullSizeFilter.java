package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderFilter;
import data.order.BattleNext_OrderStarter.TargetKind;
import data.order.BattleNext_OrderUtils;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class HullSizeFilter implements BattleNext_OrderFilter {

	private final Set<ShipAPI.HullSize> sizes = EnumSet.noneOf(ShipAPI.HullSize.class);
	private boolean largest;
	private boolean smallest;

	@Override
	public TargetKind getTargetKind() {
		return TargetKind.SHIP;
	}

	@Override
	public void configure(List<String> params) {
		BattleNext_OrderUtils.requireCount(params, 1, Integer.MAX_VALUE);
		sizes.clear();
		largest = params.contains(BattleNext_OrderUtils.HULL_SIZE_LARGEST);
		smallest = params.contains(BattleNext_OrderUtils.HULL_SIZE_SMALLEST);
		for (String param : params) {
			if (param.equals(BattleNext_OrderUtils.HULL_SIZE_LARGEST)) continue;
			if (param.equals(BattleNext_OrderUtils.HULL_SIZE_SMALLEST)) continue;
			ShipAPI.HullSize size = BattleNext_OrderUtils.hullSize(param);
			if (size == null) throw new IllegalArgumentException("Unknown hull size: " + param);
			sizes.add(size);
		}
	}

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatEngineAPI engine) {

		if (!sizes.isEmpty()) targets.removeIf(target -> !sizes.contains(((ShipAPI) target).getHullSize()));
		if (!largest && !smallest) return;
		if (targets.isEmpty()) return;
		Set<ShipAPI.HullSize> eligible = EnumSet.noneOf(ShipAPI.HullSize.class);
		for (CombatEntityAPI target : targets) {
			eligible.add(((ShipAPI) target).getHullSize());
		}
		Set<ShipAPI.HullSize> selected = EnumSet.noneOf(ShipAPI.HullSize.class);
		if (largest) selected.add(eligible.stream().max(Comparator.comparingInt(Enum::ordinal)).orElseThrow());
		if (smallest) selected.add(eligible.stream().min(Comparator.comparingInt(Enum::ordinal)).orElseThrow());
		targets.removeIf(target -> !selected.contains(((ShipAPI) target).getHullSize()));
	}
}
