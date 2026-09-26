package data.scripts.order;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.*;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_OrderThread {

	private final String starterId;
	private final OrderStarter starter;
	private final List<OrderScriptBinding<OrderFilter>> filters = new ArrayList<>();
	private final String pickerId;
	private final OrderPicker picker;
	private final List<OrderScriptBinding<OrderSuffix>> suffixes = new ArrayList<>();

	public BattleNext_OrderThread(BattleNext_OrderParser.ParsedOrderCommand command, BattleNext_OrderRegistry registry) {

		starterId = command.getStarter();
		starter = (OrderStarter)registry.create(starterId);
		for (BattleNext_OrderParser.OrderScriptInvocation part : command.getFilters()) {
			filters.add(new OrderScriptBinding<>(part.getId(), (OrderFilter)registry.create(part.getId()), part.getParams()));
		}
		pickerId = command.getPicker();
		picker = (OrderPicker)registry.create(pickerId);
		for (BattleNext_OrderParser.OrderScriptInvocation part : command.getSuffixes()) {
			suffixes.add(new OrderScriptBinding<>(part.getId(), (OrderSuffix)registry.create(part.getId()), part.getParams()));
		}
	}

	public boolean advance(ShipAPI ship, CombatEngineAPI engine, BattleNext_OrderField field, boolean continuing) {

		List<CombatEntityAPI> targets;
		CombatAssignmentType type;
		try {
			targets = new ArrayList<>(starter.getInitialTargets(ship, engine));
			type = starter.getCombatAssignmentType();
		} catch (RuntimeException exception) {
			throw new OrderScriptExecutionException(starterId, exception);
		}
		for (OrderScriptBinding<OrderFilter> step : filters) {
			if (targets.isEmpty()) return false;
			try {
				step.script.filterTargets(ship, targets, step.params, engine);
			} catch (RuntimeException exception) {
				throw new OrderScriptExecutionException(step.id, exception);
			}
		}
		if (targets.isEmpty()) return false;
		AssignmentTargetAPI target;
		try {
			target = picker.pick(ship, targets, type, engine);
		} catch (RuntimeException exception) {
			throw new OrderScriptExecutionException(pickerId, exception);
		}
		if (target == null) return false;
		float priority = 0f;
		for (OrderScriptBinding<OrderSuffix> step : suffixes) {
			try {
				target = step.script.selectTarget(target, targets, continuing, step.params, engine);
				if (target == null) return false;
				priority = step.script.resolvePriority(priority, target, step.params, engine);
			} catch (RuntimeException exception) {
				throw new OrderScriptExecutionException(step.id, exception);
			}
		}
		field.apply(ship, type, target, priority);
		return true;
	}

	private static class OrderScriptBinding<T> {

		private final String id;
		private final T script;
		private final String[] params;

		private OrderScriptBinding(String id, T script, List<String> params) {
			this.id = id;
			this.script = script;
			this.params = params.toArray(new String[0]);
		}
	}

	public static class OrderScriptExecutionException extends RuntimeException {

		private final String scriptId;

		public OrderScriptExecutionException(String scriptId, RuntimeException cause) {
			super(cause);
			this.scriptId = scriptId;
		}

		public String getScriptId() {
			return scriptId;
		}
	}
}
