package data.scripts.order;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.*;
import data.order.BattleNext_OrderStarter.TargetKind;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_OrderThread {

	private final String starterId;
	private final BattleNext_OrderStarter starter;
	private final List<OrderScriptBinding<BattleNext_OrderFilter>> filters = new ArrayList<>();
	private final String pickerId;
	private final BattleNext_OrderPicker picker;
	private final List<OrderScriptBinding<BattleNext_OrderSuffix>> suffixes = new ArrayList<>();

	public BattleNext_OrderThread(BattleNext_OrderParser.ParsedOrderCommand command, BattleNext_OrderRegistry registry) {

		starterId = command.getStarter();
		try {
			starter = (BattleNext_OrderStarter) registry.create(starterId);
		} catch (RuntimeException exception) {
			throw new OrderScriptExecutionException(starterId, exception);
		}
		for (BattleNext_OrderParser.OrderScriptInvocation part : command.getFilters()) {
			try {
				BattleNext_OrderFilter filter = (BattleNext_OrderFilter) registry.create(part.getId());
				if (filter.getTargetKind() != TargetKind.ANY && filter.getTargetKind() != starter.getTargetKind()) {
					throw new IllegalArgumentException("Filter requires " + filter.getTargetKind()
							+ " targets, starter supplies " + starter.getTargetKind());
				}
				filter.configure(part.getParams());
				filters.add(new OrderScriptBinding<>(part.getId(), filter));
			} catch (RuntimeException exception) {
				throw new OrderScriptExecutionException(part.getId(), exception);
			}
		}
		pickerId = command.getPicker();
		try {
			picker = (BattleNext_OrderPicker) registry.create(pickerId);
		} catch (RuntimeException exception) {
			throw new OrderScriptExecutionException(pickerId, exception);
		}
		for (BattleNext_OrderParser.OrderScriptInvocation part : command.getSuffixes()) {
			try {
				BattleNext_OrderSuffix suffix = (BattleNext_OrderSuffix) registry.create(part.getId());
				suffix.configure(part.getParams());
				suffixes.add(new OrderScriptBinding<>(part.getId(), suffix));
			} catch (RuntimeException exception) {
				throw new OrderScriptExecutionException(part.getId(), exception);
			}
		}
	}

	public boolean advance(ShipAPI ship, CombatEngineAPI engine, BattleNext_OrderField field) {

		List<CombatEntityAPI> targets;
		CombatAssignmentType type;
		try {
			targets = new ArrayList<>(starter.getInitialTargets(ship, engine));
			type = starter.getCombatAssignmentType();
		} catch (RuntimeException exception) {
			throw new OrderScriptExecutionException(starterId, exception);
		}
		for (OrderScriptBinding<BattleNext_OrderFilter> step : filters) {
			if (targets.isEmpty()) return false;
			try {
				step.script.filterTargets(ship, targets, engine);
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
		AssignmentTargetAPI previous = field.getCommandTarget(ship, this);
		float priority = 0f;
		for (OrderScriptBinding<BattleNext_OrderSuffix> step : suffixes) {
			try {
				target = step.script.selectTarget(target, targets, previous, engine);
				if (target == null) return false;
				priority = step.script.resolvePriority(priority, target, engine);
			} catch (RuntimeException exception) {
				throw new OrderScriptExecutionException(step.id, exception);
			}
		}
		try {
			return field.apply(ship, starter.getAction(), type, target, priority, this);
		} catch (RuntimeException exception) {
			throw new OrderScriptExecutionException(starterId, exception);
		}
	}

	private record OrderScriptBinding<T>(String id, T script) { }

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
