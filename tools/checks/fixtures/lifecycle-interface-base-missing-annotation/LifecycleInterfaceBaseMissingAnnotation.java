public class LifecycleInterfaceBaseMissingAnnotation extends BaseEveryFrameCombatPlugin {
	private static final String KEY = "key";

	private LifecycleInterfaceBaseMissingAnnotation() {
	}

	public static LifecycleInterfaceBaseMissingAnnotation getInstance(CombatEngineAPI engine, CombatEntityAPI entity) {
		LifecycleInterfaceBaseMissingAnnotation controller = new LifecycleInterfaceBaseMissingAnnotation();
		engine.addPlugin(controller);
		entity.setCustomData(KEY, controller);
		return controller;
	}
}
