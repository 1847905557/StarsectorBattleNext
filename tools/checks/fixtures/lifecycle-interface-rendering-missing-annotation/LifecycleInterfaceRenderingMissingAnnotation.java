public class LifecycleInterfaceRenderingMissingAnnotation extends BaseCombatLayeredRenderingPlugin {
	private LifecycleInterfaceRenderingMissingAnnotation() {
	}

	public static LifecycleInterfaceRenderingMissingAnnotation attach(CombatEngineAPI engine) {
		LifecycleInterfaceRenderingMissingAnnotation controller = new LifecycleInterfaceRenderingMissingAnnotation();
		engine.addLayeredRenderingPlugin(controller);
		return controller;
	}
}
