package checks.project.support;

import checks.project.model.Violation;

import java.util.List;

public final class RuleFailureReporter {
	private RuleFailureReporter() {
	}

	public static void reportViolations(List<Violation> violations, String message) {
		for (Violation violation : violations) {
			System.err.println(violation);
		}
		if (!violations.isEmpty()) {
			throw new IllegalStateException(violations.size() + " " + message);
		}
	}

	public static void reportFailures(List<String> failures, String message) {
		for (String failure : failures) {
			System.err.println(failure);
		}
		if (!failures.isEmpty()) {
			throw new IllegalStateException(failures.size() + " " + message);
		}
	}
}
