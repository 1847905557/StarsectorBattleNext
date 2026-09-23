public class LifecyclePrivateOk {
	private LifecyclePrivateOk() {
	}

	@LifecycleEntry
	public static LifecyclePrivateOk getInstance() {
		return new LifecyclePrivateOk();
	}
}
