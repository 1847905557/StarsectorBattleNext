public class LifecyclePublicViolation {
	public LifecyclePublicViolation() {
	}

	@LifecycleEntry
	public static LifecyclePublicViolation getInstance() {
		return new LifecyclePublicViolation();
	}
}
