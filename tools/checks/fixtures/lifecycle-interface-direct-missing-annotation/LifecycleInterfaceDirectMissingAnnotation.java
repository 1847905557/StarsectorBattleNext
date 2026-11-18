public class LifecycleInterfaceDirectMissingAnnotation implements EveryFrameScript {
	private static final String KEY = "key";

	private LifecycleInterfaceDirectMissingAnnotation() {
	}

	public static LifecycleInterfaceDirectMissingAnnotation getInstance(CombatEntityAPI entity) {
		LifecycleInterfaceDirectMissingAnnotation controller = new LifecycleInterfaceDirectMissingAnnotation();
		entity.setCustomData(KEY, controller);
		return controller;
	}
}
