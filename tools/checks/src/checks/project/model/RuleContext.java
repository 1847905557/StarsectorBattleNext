package checks.project.model;

import checks.project.analysis.LifecycleControllerAnalysis;
import checks.project.analysis.SourceInheritanceIndex;

public class RuleContext {
	private final LifecycleControllerAnalysis lifecycleControllerAnalysis = new LifecycleControllerAnalysis();
	private final SourceInheritanceIndex sourceInheritanceIndex = new SourceInheritanceIndex();

	public LifecycleControllerAnalysis lifecycleControllerAnalysis() {
		return lifecycleControllerAnalysis;
	}

	public SourceInheritanceIndex sourceInheritanceIndex() {
		return sourceInheritanceIndex;
	}
}
