package checks.project;

import checks.project.model.Violation;
import checks.project.rules.ProjectRules;
import checks.project.support.RuleFailureReporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ProjectRulesCheck {
	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			throw new IllegalArgumentException("Usage: ProjectRulesCheck <source-root> [classpath...]");
		}

		Path sourceRoot = Path.of(args[0]);
		List<Path> classpath = Arrays.stream(args)
				.skip(1)
				.map(Path::of)
				.toList();
		List<Violation> violations = ProjectRuleRunner.run(ProjectRuleInput.source(sourceRoot, classpath), ProjectRules.all()).violations();
		RuleFailureReporter.reportViolations(violations, "project rule violation(s) found.");
	}
}
