package checks.integrity.support;

import java.util.List;

public final class IntegrityFailureReporter {
	private IntegrityFailureReporter() {
	}

	public static void report(List<String> failures, String message) {
		for (String failure : failures) {
			System.err.println(failure);
		}
		if (!failures.isEmpty()) {
			throw new IllegalStateException(failures.size() + " " + message);
		}
	}
}
