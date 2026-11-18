package checks.integrity;

import checks.integrity.rules.IntegrityRules;

import java.io.IOException;
import java.nio.file.Path;

public class ProjectRulesIntegrityCheck {
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			throw new IllegalArgumentException("Usage: ProjectRulesIntegrityCheck <checks-source-root> <fixtures-root> <tools-readme>");
		}

		Path checksSourceRoot = Path.of(args[0]);
		Path fixturesRoot = Path.of(args[1]);
		Path readme = Path.of(args[2]);
		IntegrityRuleRunner.run(
				IntegrityCheckInput.full(checksSourceRoot, fixturesRoot, readme),
				IntegrityRules.all(),
				"project rule integrity failure(s) found."
		);
	}
}
