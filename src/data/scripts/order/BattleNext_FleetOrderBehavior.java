package data.scripts.order;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import data.order.BattleNext_OrderRegistry;
import data.order.BattleNext_OrderUtils;
import data.scripts.ai.rtt.BattleNext_RttShipAI;
import data.scripts.fleet.BattleNext_AssembledFleet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BattleNext_FleetOrderBehavior extends BaseEveryFrameCombatPlugin {

	public enum FleetOrderMode {
		ORDERS, AUTO_COMMAND, FULL_ASSAULT
	}

	public static final float ORDERS_INTERVAL = 0.15f;
	public static final float FULL_ASSAULT_INTERVAL = 1f;
	private final FleetOrderMode playerMode;
	private final FleetOrderMode enemyMode;
	private final List<MemberOrders> orders = new ArrayList<>();
	private final Set<BattleNext_OrderField> activeFields = new LinkedHashSet<>();
	private final IntervalUtil ordersInterval = new IntervalUtil(ORDERS_INTERVAL, ORDERS_INTERVAL);
	private final IntervalUtil assaultInterval = new IntervalUtil(FULL_ASSAULT_INTERVAL, FULL_ASSAULT_INTERVAL);
	private CombatEngineAPI engine;

	public BattleNext_FleetOrderBehavior(FleetOrderMode playerMode, FleetOrderMode enemyMode) {
		this.playerMode = playerMode;
		this.enemyMode = enemyMode;
	}

	public BattleNext_FleetOrderBehavior(FleetOrderMode playerMode, FleetOrderMode enemyMode,
			BattleNext_AssembledFleet assembledPlayerFleet, BattleNext_AssembledFleet assembledEnemyFleet) {
		this(playerMode, enemyMode);
		if (playerMode == FleetOrderMode.ORDERS) addOrders(assembledPlayerFleet, FleetSide.PLAYER);
		if (enemyMode == FleetOrderMode.ORDERS) addOrders(assembledEnemyFleet, FleetSide.ENEMY);
	}

	private void addOrders(BattleNext_AssembledFleet assembledFleet, FleetSide side) {
		if (assembledFleet == null) return;
		for (int index = 0; index < assembledFleet.getMembers().size(); index++) {
			FleetMemberAPI member = assembledFleet.getMembers().get(index);
			String script = assembledFleet.getDefinition().getMembers().get(index).getOrders();
			String label = assembledFleet.getDefinition().getName() + " member[" + index + "] "
					+ assembledFleet.getDefinition().getMembers().get(index).getVariantId();
			BattleNext_OrderProcessor processor = new BattleNext_OrderProcessor(script, label,
					BattleNext_OrderRegistry.getInstance(), message -> Global.getLogger(BattleNext_FleetOrderBehavior.class).error(message));
			if (!processor.isEmpty()) orders.add(new MemberOrders(member, side.ordinal(), processor));
		}
	}

	public static FleetOrderMode fromTags(List<String> tags) {
		if (tags == null) return FleetOrderMode.ORDERS;
		if (tags.contains("default_ai")) return FleetOrderMode.AUTO_COMMAND;
		if (tags.contains("full_assault")) return FleetOrderMode.FULL_ASSAULT;
		return FleetOrderMode.ORDERS;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		configureAutoCommand(FleetSide.PLAYER, playerMode);
		configureAutoCommand(FleetSide.ENEMY, enemyMode);
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine.isCombatOver()) {
			cleanup();
			return;
		}
		if (engine.isPaused()) return;
		ordersInterval.advance(amount);
		assaultInterval.advance(amount);
		if (ordersInterval.intervalElapsed()) processOrders();
		if (assaultInterval.intervalElapsed()) {
			processFullAssault(FleetSide.PLAYER, playerMode);
			processFullAssault(FleetSide.ENEMY, enemyMode);
		}
	}

	private void configureAutoCommand(FleetSide side, FleetOrderMode mode) {
		if (mode != FleetOrderMode.AUTO_COMMAND) return;
		engine.getFleetManager(side).getTaskManager(false).setFullAssault(false);
	}

	private void processOrders() {

		Map<FleetMemberAPI, MemberOrders> registered = new IdentityHashMap<>();
		for (MemberOrders entry : orders) {
			registered.put(entry.member, entry);
		}
		Map<FleetMemberAPI, ShipAPI> deployed = new IdentityHashMap<>();
		for (ShipAPI ship : engine.getShips()) {
			MemberOrders entry = registered.get(ship.getFleetMember());
			if (entry == null || ship.getOwner() != entry.owner) continue;
			if (!BattleNext_OrderUtils.isValid(ship, engine) || ship.isStation()) continue;
			if (BattleNext_RttShipAI.isControlled(ship)) continue;
			CombatTaskManagerAPI tasks = engine.getFleetManager(ship.getOwner()).getTaskManager(ship.isAlly());
			if (tasks.isFullAssault() || tasks.isInFullRetreat()) continue;
			deployed.put(ship.getFleetMember(), ship);
		}
		List<ShipAPI> eligible = new ArrayList<>();
		for (MemberOrders entry : orders) {
			ShipAPI ship = deployed.get(entry.member);
			if (ship == null || ship.getOwner() != entry.owner) continue;
			eligible.add(ship);
			activeFields.add(BattleNext_OrderField.getInstance(engine, ship.getOwner(), ship.isAlly()));
		}
		for (BattleNext_OrderField field : activeFields) {
			field.reconcile(eligible);
		}
		for (MemberOrders entry : orders) {
			ShipAPI ship = deployed.get(entry.member);
			if (ship == null || ship.getOwner() != entry.owner) continue;
			BattleNext_OrderField field = BattleNext_OrderField.getInstance(engine, ship.getOwner(), ship.isAlly());
			entry.processor.advance(ship, engine, field);
		}
		for (BattleNext_OrderField field : activeFields) {
			field.pruneAssignments();
		}
	}

	private void processFullAssault(FleetSide side, FleetOrderMode mode) {
		if (mode != FleetOrderMode.FULL_ASSAULT) return;
		for (ShipAPI ship : engine.getShips()) {
			if (!BattleNext_OrderUtils.isValid(ship, engine) || ship.isStation() || ship.getOwner() != side.ordinal()) continue;
			if (BattleNext_RttShipAI.isControlled(ship)) continue;
			CombatTaskManagerAPI tasks = engine.getFleetManager(ship.getOwner()).getTaskManager(ship.isAlly());
			if (tasks.getAssignmentFor(ship) != null || ship.isRetreating() || tasks.isInFullRetreat()) continue;
			tasks.orderSearchAndDestroy(engine.getFleetManager(ship.getOwner()).getDeployedFleetMember(ship), false);
		}
	}

	public void cleanup() {
		for (BattleNext_OrderField field : activeFields) {
			field.cleanup();
		}
		activeFields.clear();
		orders.clear();
		engine.removePlugin(this);
	}

	private record MemberOrders(FleetMemberAPI member, int owner, BattleNext_OrderProcessor processor) { }
}
