package checks.project.rules;

import checks.project.ProjectRule;

import java.util.List;

public final class ProjectRules {
	private ProjectRules() {
	}

	public static List<ProjectRule> all() {
		return List.of(
				new ColorAllocationRule(),
				new ProjectileSourceChainedCallRule(),
				new ProjectileSourceFieldCacheRule(),
				new MissingLifecycleEntryAnnotationRule(),
				new LifecycleTypeResolutionFailureRule(),
				new LifecycleConstructorRule(),
				new FieldAccessBoundaryRule(),
				new PublicDataShapeRule(),
				new StaticFieldImmutabilityRule()
		);
	}
}
