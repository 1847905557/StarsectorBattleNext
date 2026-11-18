package checks.project.fixture;

import checks.project.ProjectRule;
import checks.project.ProjectRuleInput;
import checks.project.ProjectRuleRunner;
import checks.project.model.Violation;
import checks.project.support.FixtureExpectedRulesReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProjectRuleFixtureRunner {
	private static final String EXPECTED_RULES_FILE = "expected-rules.txt";

	private ProjectRuleFixtureRunner() {
	}

	public static ProjectRuleFixtureResult run(Path fixturesRoot, List<ProjectRule> rules) throws IOException {
		try (Stream<Path> paths = Files.walk(fixturesRoot, 2)) {
			List<String> failures = paths
					.filter(Files::isDirectory)
					.filter(path -> Files.exists(path.resolve(EXPECTED_RULES_FILE)))
					.sorted()
					.flatMap(path -> runFixture(fixturesRoot, path, rules).stream())
					.toList();
			return new ProjectRuleFixtureResult(failures);
		}
	}

	private static List<String> runFixture(Path fixturesRoot, Path fixture, List<ProjectRule> rules) {
		try {
			ProjectRuleFixtureCase fixtureCase = new ProjectRuleFixtureCase(
					fixture,
					FixtureExpectedRulesReader.read(fixture.resolve(EXPECTED_RULES_FILE))
			);
			List<Violation> violations = ProjectRuleRunner.run(
					new ProjectRuleInput(fixtureCase.path(), listFixtureSourceRoots(fixturesRoot), List.of()),
					rules
			).violations();
			Set<String> actualRules = violations.stream()
					.map(Violation::rule)
					.collect(Collectors.toCollection(HashSet::new));

			if (actualRules.equals(fixtureCase.expectedRules())) {
				return List.of();
			}

			String message = fixtureCase.path() + ": expected rules " + fixtureCase.expectedRules() + " but got " + actualRules;
			return Stream.concat(Stream.of(message), violations.stream().map(Violation::toString)).toList();
		} catch (IOException ex) {
			return List.of(fixture + ": " + ex.getMessage());
		}
	}

	private static List<Path> listFixtureSourceRoots(Path fixturesRoot) throws IOException {
		try (Stream<Path> paths = Files.walk(fixturesRoot, 1)) {
			return paths
					.filter(Files::isDirectory)
					.sorted()
					.toList();
		}
	}
}
