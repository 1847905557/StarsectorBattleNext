public class LifecycleResolutionFailureViolation implements MissingLifecycleInterface {
	private static final String KEY = "key";

	private LifecycleResolutionFailureViolation() {
	}

	public static LifecycleResolutionFailureViolation getInstance(CombatEntityAPI entity) {
		LifecycleResolutionFailureViolation controller = new LifecycleResolutionFailureViolation();
		entity.setCustomData(KEY, controller);
		return controller;
	}
}
