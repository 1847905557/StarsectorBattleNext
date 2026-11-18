public class LifecyclePublicCandidateViolation extends BaseEveryFrameCombatPlugin {
	public LifecyclePublicCandidateViolation() {
	}

	@LifecycleEntry
	public static LifecyclePublicCandidateViolation getInstance(CombatEngineAPI engine) {
		LifecyclePublicCandidateViolation controller = new LifecyclePublicCandidateViolation();
		engine.addPlugin(controller);
		return controller;
	}
}
