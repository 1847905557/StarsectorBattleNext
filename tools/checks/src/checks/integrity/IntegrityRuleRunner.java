package checks.integrity;

import checks.integrity.support.IntegrityFailureReporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class IntegrityRuleRunner {
	private IntegrityRuleRunner() {
	}

	public static void run(IntegrityCheckInput input, List<IntegrityRule> rules, String failureMessage) throws IOException {
		List<String> failures = new ArrayList<>();
		for (IntegrityRule rule : rules) {
			failures.addAll(rule.check(input));
		}
		IntegrityFailureReporter.report(failures, failureMessage);
	}
}
