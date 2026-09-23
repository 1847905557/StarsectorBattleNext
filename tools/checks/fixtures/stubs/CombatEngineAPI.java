public interface CombatEngineAPI {
	void addPlugin(EveryFrameCombatPlugin plugin);

	void addLayeredRenderingPlugin(CombatLayeredRenderingPlugin plugin);
}
