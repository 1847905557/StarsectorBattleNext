public class LifecycleInterfaceBaseOk extends BaseEveryFrameCombatPlugin {
	private static final String KEY = "key";

	private LifecycleInterfaceBaseOk() {
	}

	@LifecycleEntry
	public static LifecycleInterfaceBaseOk getInstance(CombatEngineAPI engine, CombatEntityAPI entity) {
		LifecycleInterfaceBaseOk controller = new LifecycleInterfaceBaseOk();
		engine.addPlugin(controller);
		entity.setCustomData(KEY, controller);
		return controller;
	}
}
