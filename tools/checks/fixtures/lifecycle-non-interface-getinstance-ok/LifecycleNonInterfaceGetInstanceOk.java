public class LifecycleNonInterfaceGetInstanceOk {
	private LifecycleNonInterfaceGetInstanceOk() {
	}

	public static LifecycleNonInterfaceGetInstanceOk getInstance(CombatEntityAPI entity) {
		LifecycleNonInterfaceGetInstanceOk controller = new LifecycleNonInterfaceGetInstanceOk();
		entity.setCustomData("key", controller);
		return controller;
	}
}
