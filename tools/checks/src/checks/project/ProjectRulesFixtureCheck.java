package checks.project;

import checks.project.fixture.ProjectRuleFixtureResult;
import checks.project.fixture.ProjectRuleFixtureRunner;
import checks.project.rules.ProjectRules;
import checks.project.support.RuleFailureReporter;

import java.io.IOException;
import java.nio.file.Path;

public class ProjectRulesFixtureCheck {
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException("Usage: ProjectRulesFixtureCheck <fixtures-root>");
		}

		Path fixturesRoot = Path.of(args[0]);
		ProjectRuleFixtureResult result = ProjectRuleFixtureRunner.run(fixturesRoot, ProjectRules.all());
		RuleFailureReporter.reportFailures(result.failures(), "project rule fixture failure(s) found.");
	}
}
