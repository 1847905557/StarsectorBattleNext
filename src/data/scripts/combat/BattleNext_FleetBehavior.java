package data.scripts.combat;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import data.model.fleet.BattleNext_FleetModel;
import data.automation.BattleNext_OrderField;
import data.automation.BattleNext_OrderRegistry;
import data.automation.BattleNext_OrderSpec;
import data.automation.OrderFilter;
import data.automation.OrderPicker;
import data.automation.OrderStarter;
import data.automation.OrderSuffix;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetBehavior extends BaseEveryFrameCombatPlugin {

	public enum Mode {
		ORDERS,
		AUTO_COMMAND,
		FULL_ASSAULT
	}

	public static final float ORDERS_INTERVAL = 0.15f;
	public static final float FULL_ASSAULT_INTERVAL = 1f;
	private final Mode playerMode;
	private final Mode enemyMode;
	private final Map<String, String> ordersByVariant = new HashMap<>();
	private final IntervalUtil ordersInterval = new IntervalUtil(ORDERS_INTERVAL, ORDERS_INTERVAL);
	private final IntervalUtil assaultInterval = new IntervalUtil(FULL_ASSAULT_INTERVAL, FULL_ASSAULT_INTERVAL);
	private CombatEngineAPI engine;

	public BattleNext_FleetBehavior(Mode playerMode, Mode enemyMode) {
		this.playerMode = playerMode;
		this.enemyMode = enemyMode;
	}

	public BattleNext_FleetBehavior(Mode playerMode, Mode enemyMode, BattleNext_FleetModel player, BattleNext_FleetModel enemy) {
		this(playerMode, enemyMode);
		addOrders(player);
		addOrders(enemy);
	}

	private void addOrders(BattleNext_FleetModel fleet) {
		if (fleet == null) return;
		fleet.getMembers().forEach(member -> ordersByVariant.put(member.getVariantId(), member.getOrders()));
	}

	public static Mode fromTags(List<String> tags) {
		if (tags == null) return Mode.AUTO_COMMAND;
		if (tags.contains("orders")) return Mode.ORDERS;
		if (tags.contains("full_assault")) return Mode.FULL_ASSAULT;
		return Mode.AUTO_COMMAND;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		configureAutoCommand(FleetSide.PLAYER, playerMode);
		configureAutoCommand(FleetSide.ENEMY, enemyMode);
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || engine.isCombatOver()) return;
		ordersInterval.advance(amount);
		assaultInterval.advance(amount);
		boolean ordersReady = ordersInterval.intervalElapsed();
		boolean assaultReady = assaultInterval.intervalElapsed();
		if (ordersReady) {
			processOrders(FleetSide.PLAYER, playerMode);
			processOrders(FleetSide.ENEMY, enemyMode);
		}
		if (assaultReady) {
			processFullAssault(FleetSide.PLAYER, playerMode);
			processFullAssault(FleetSide.ENEMY, enemyMode);
		}
	}

	private void configureAutoCommand(FleetSide side, Mode mode) {
		if (mode != Mode.AUTO_COMMAND) return;
		CombatTaskManagerAPI taskManager = engine.getFleetManager(side).getTaskManager(false);
		taskManager.setFullAssault(false);
	}

	private void processOrders(FleetSide side, Mode mode) {
		if (mode != Mode.ORDERS) return;
		BattleNext_OrderField field = BattleNext_OrderField.getInstance(engine, side.ordinal(), false);
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive() || ship.isFighter() || ship.isStation() || ship.getOwner() != side.ordinal()) continue;
			field.registerShip(ship);
			if (field.getInfoFor(ship) == null) {
				applyScript(field, ship);
			}
		}
		field.pruneAssignments();
	}

	private void applyScript(BattleNext_OrderField field, ShipAPI ship) {
		String script = ship.getVariant() == null ? "" : ordersByVariant.get(ship.getVariant().getHullVariantId());
		if (script == null || script.trim().isEmpty()) return;
		BattleNext_OrderSpec.ParseResult parsed = BattleNext_OrderSpec.parse(script);
		if (!parsed.isValid()) return;
		BattleNext_OrderSpec.Command command = parsed.getCommand();
		try {
			OrderStarter starter = (OrderStarter)BattleNext_OrderRegistry.getInstance().create("starter", command.getStarter());
			List<? extends CombatEntityAPI> targets = starter.getInitialTargets(ship, engine);
			for (BattleNext_OrderSpec.Part part : command.getFilters()) {
				OrderFilter filter = (OrderFilter)BattleNext_OrderRegistry.getInstance().create("filter", part.getId());
				filter.filterTargets(ship, targets, part.getParams().toArray(new String[0]), engine);
				if (targets.isEmpty()) return;
			}
			OrderPicker picker = (OrderPicker)BattleNext_OrderRegistry.getInstance().create("picker", command.getPicker());
			Object target = picker.pick(ship, targets, starter.getCombatAssignmentType(), engine);
			float priority = 0f;
			Object custom = null;
			for (BattleNext_OrderSpec.Part part : command.getSuffixes()) {
				OrderSuffix suffix = (OrderSuffix)BattleNext_OrderRegistry.getInstance().create("suffix", part.getId());
				priority = suffix.getFloatResultWithTarget((com.fs.starfarer.api.combat.AssignmentTargetAPI)target,
						part.getParams().toArray(new String[0]), custom, engine);
				custom = suffix.getCustomResultWithTarget((com.fs.starfarer.api.combat.AssignmentTargetAPI)target,
						part.getParams().toArray(new String[0]), custom, engine);
			}
			field.apply(ship, starter.getCombatAssignmentType(), (com.fs.starfarer.api.combat.AssignmentTargetAPI)target, priority);
		} catch (RuntimeException ignored) {
		}
	}

	private void processFullAssault(FleetSide side, Mode mode) {
		if (mode != Mode.FULL_ASSAULT) return;
		CombatTaskManagerAPI taskManager = engine.getFleetManager(side).getTaskManager(false);
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive() || ship.isFighter() || ship.isStation() || ship.getOwner() != side.ordinal()) continue;
			taskManager.orderSearchAndDestroy(engine.getFleetManager(side).getDeployedFleetMember(ship), false);
		}
	}
}
