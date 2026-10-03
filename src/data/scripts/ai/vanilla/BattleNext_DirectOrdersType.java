package data.scripts.ai.vanilla;

/**
 * Port of engine enum com.fs.starfarer.combat.tasks.DirectOrdersType.
 * The engine's direct-order channel is not exposed to the mod API, so the ported
 * order response module reconstructs these values from assignment types and the
 * retreat state of the ship.
 */
public enum BattleNext_DirectOrdersType {
	CARRY_OUT_COMBAT_TASK,
	RETREAT,
	REPAIR_AND_REFIT,
	SEARCH_AND_DESTROY,
	NO_ORDERS
}
