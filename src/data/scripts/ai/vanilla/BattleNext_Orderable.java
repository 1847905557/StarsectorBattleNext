package data.scripts.ai.vanilla;

/**
 * Port of engine interface com.fs.starfarer.combat.ai.Orderable.
 * Implemented by the main ship AI so maneuvers and the order response module can
 * query and follow fleet-level orders.
 * Deviation from vanilla: the engine interface extends the engine AI marker
 * (advance + render) and takes an internal CombatOrder parameter on
 * notifyNewOrderGivenToBattleGroup; neither exists in the mod API, so advance is
 * declared here directly and the notification lost its parameter.
 */
public interface BattleNext_Orderable {
	void advance(float amount);

	boolean isEngaged();

	boolean canObeyOrders();

	BattleNext_DirectOrders getOrders();

	void notifyNewOrderGivenToBattleGroup();
}
