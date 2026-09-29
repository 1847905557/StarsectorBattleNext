package data.scripts.order;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import data.order.BattleNext_OrderRegistry;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.ai.rtt.BattleNext_RttShipAI;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BattleNext_FleetOrderBehavior extends BaseEveryFrameCombatPlugin {

	public enum FleetOrderMode {
		ORDERS,
		AUTO_COMMAND,
		FULL_ASSAULT
	}

	public static final float ORDERS_INTERVAL = 0.15f;
	public static final float FULL_ASSAULT_INTERVAL = 1f;
	private final FleetOrderMode playerMode;
	private final FleetOrderMode enemyMode;
	private final Map<FleetMemberAPI, BattleNext_OrderProcessor> ordersByMember = new IdentityHashMap<>();
	private final Set<BattleNext_OrderField> activeFields = new HashSet<>();
	private final IntervalUtil ordersInterval = new IntervalUtil(ORDERS_INTERVAL, ORDERS_INTERVAL);
	private final IntervalUtil assaultInterval = new IntervalUtil(FULL_ASSAULT_INTERVAL, FULL_ASSAULT_INTERVAL);
	private CombatEngineAPI engine;

	public BattleNext_FleetOrderBehavior(FleetOrderMode playerMode, FleetOrderMode enemyMode) {
		this.playerMode = playerMode;
		this.enemyMode = enemyMode;
	}

	public BattleNext_FleetOrderBehavior(FleetOrderMode playerMode, FleetOrderMode enemyMode, BattleNext_AssembledFleet assembledPlayerFleet, BattleNext_AssembledFleet assembledEnemyFleet) {
		this(playerMode, enemyMode);
		if (playerMode == FleetOrderMode.ORDERS) addOrders(assembledPlayerFleet);
		if (enemyMode == FleetOrderMode.ORDERS) addOrders(assembledEnemyFleet);
	}

	private void addOrders(BattleNext_AssembledFleet assembledFleet) {
		if (assembledFleet == null) return;
		for (int index = 0; index < assembledFleet.getMembers().size(); index++) {
			FleetMemberAPI member = assembledFleet.getMembers().get(index);
			String script = assembledFleet.getDefinition().getMembers().get(index).getOrders();
			String label = assembledFleet.getDefinition().getName() + " member[" + index + "] "
					+ assembledFleet.getDefinition().getMembers().get(index).getVariantId();
			BattleNext_OrderProcessor processor = new BattleNext_OrderProcessor(script, label,
					BattleNext_OrderRegistry.getInstance(), message -> Global.getLogger(BattleNext_FleetOrderBehavior.class).error(message));
			if (!processor.isEmpty()) ordersByMember.put(member, processor);
		}
	}

	public static FleetOrderMode fromTags(List<String> tags) {
		if (tags == null) return FleetOrderMode.AUTO_COMMAND;
		if (tags.contains("orders")) return FleetOrderMode.ORDERS;
		if (tags.contains("full_assault")) return FleetOrderMode.FULL_ASSAULT;
		return FleetOrderMode.AUTO_COMMAND;
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

	private void configureAutoCommand(FleetSide side, FleetOrderMode mode) {
		if (mode != FleetOrderMode.AUTO_COMMAND) return;
		CombatTaskManagerAPI taskManager = engine.getFleetManager(side).getTaskManager(false);
		taskManager.setFullAssault(false);
	}

	private void processOrders(FleetSide side, FleetOrderMode mode) {
		if (mode != FleetOrderMode.ORDERS) return;
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive() || ship.isFighter() || ship.isStation() || ship.isStationModule()
					|| ship.getOriginalOwner() != side.ordinal()) continue;
			if (BattleNext_RttShipAI.isControlled(ship)) continue;
			BattleNext_OrderProcessor processor = ordersByMember.get(ship.getFleetMember());
			if (processor == null) continue;
			CombatTaskManagerAPI tasks = engine.getFleetManager(ship.getOwner()).getTaskManager(ship.isAlly());
			if (tasks.isFullAssault() || tasks.isInFullRetreat()) continue;
			BattleNext_OrderField field = BattleNext_OrderField.getInstance(engine, ship.getOwner(), ship.isAlly());
			field.registerShip(ship);
			field.reconcileShip(ship);
			activeFields.add(field);
			processor.advance(ship, engine, field);
		}
		for (BattleNext_OrderField field : activeFields) {
			field.pruneAssignments();
		}
	}

	private void processFullAssault(FleetSide side, FleetOrderMode mode) {
		if (mode != FleetOrderMode.FULL_ASSAULT) return;
		CombatTaskManagerAPI taskManager = engine.getFleetManager(side).getTaskManager(false);
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive() || ship.isFighter() || ship.isStation() || ship.getOwner() != side.ordinal()) continue;
			if (BattleNext_RttShipAI.isControlled(ship)) continue;
			taskManager.orderSearchAndDestroy(engine.getFleetManager(side).getDeployedFleetMember(ship), false);
		}
	}
}
