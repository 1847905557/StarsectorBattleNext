package data.scripts.combat;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import data.order.BattleNext_OrderField;
import data.order.BattleNext_OrderProcessor;
import data.order.BattleNext_OrderRegistry;
import data.scripts.fleet.BattleNext_FleetAssembly;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final Map<FleetMemberAPI, BattleNext_OrderProcessor> ordersByMember = new IdentityHashMap<>();
	private final Set<BattleNext_OrderField> activeFields = new HashSet<>();
	private final IntervalUtil ordersInterval = new IntervalUtil(ORDERS_INTERVAL, ORDERS_INTERVAL);
	private final IntervalUtil assaultInterval = new IntervalUtil(FULL_ASSAULT_INTERVAL, FULL_ASSAULT_INTERVAL);
	private CombatEngineAPI engine;

	public BattleNext_FleetBehavior(Mode playerMode, Mode enemyMode) {
		this.playerMode = playerMode;
		this.enemyMode = enemyMode;
	}

	public BattleNext_FleetBehavior(Mode playerMode, Mode enemyMode, BattleNext_FleetAssembly player, BattleNext_FleetAssembly enemy) {
		this(playerMode, enemyMode);
		if (playerMode == Mode.ORDERS) addOrders(player);
		if (enemyMode == Mode.ORDERS) addOrders(enemy);
	}

	private void addOrders(BattleNext_FleetAssembly assembly) {
		if (assembly == null) return;
		for (int index = 0; index < assembly.getMembers().size(); index++) {
			FleetMemberAPI member = assembly.getMembers().get(index);
			String script = assembly.getDefinition().getMembers().get(index).getOrders();
			String label = assembly.getDefinition().getName() + " member[" + index + "] "
					+ assembly.getDefinition().getMembers().get(index).getVariantId();
			BattleNext_OrderProcessor processor = new BattleNext_OrderProcessor(script, label,
					BattleNext_OrderRegistry.getInstance(), message -> Global.getLogger(BattleNext_FleetBehavior.class).error(message));
			if (!processor.isEmpty()) ordersByMember.put(member, processor);
		}
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
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive() || ship.isFighter() || ship.isStation() || ship.isStationModule()
					|| ship.getOriginalOwner() != side.ordinal()) continue;
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

	private void processFullAssault(FleetSide side, Mode mode) {
		if (mode != Mode.FULL_ASSAULT) return;
		CombatTaskManagerAPI taskManager = engine.getFleetManager(side).getTaskManager(false);
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive() || ship.isFighter() || ship.isStation() || ship.getOwner() != side.ordinal()) continue;
			taskManager.orderSearchAndDestroy(engine.getFleetManager(side).getDeployedFleetMember(ship), false);
		}
	}
}
