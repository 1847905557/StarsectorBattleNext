package checks.integrity.rules;

import checks.integrity.IntegrityCheckInput;
import checks.integrity.IntegrityRule;
import checks.integrity.model.FixtureMetadata;
import checks.integrity.model.RuleRegistry;
import checks.integrity.support.FixtureMetadataReader;
import checks.integrity.support.ProjectRuleRegistryReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class FixtureIntegrityRule implements IntegrityRule {
	private static final String EXPECTED_RULES_FILE = "expected-rules.txt";
	private static final String SUPPORT_FIXTURE_DIR = "stubs";

	@Override
	public List<String> check(IntegrityCheckInput input) throws IOException {
		RuleRegistry ruleRegistry = ProjectRuleRegistryReader.read(input.projectRulesDir());
		List<String> failures = new ArrayList<>();
		failures.addAll(checkFixtureShape(input.fixturesRoot()));
		failures.addAll(checkFixtureCoverage(input.fixturesRoot(), ruleRegistry));
		return failures;
	}

	private static List<String> checkFixtureShape(Path fixturesRoot) throws IOException {
		List<String> failures = new ArrayList<>();
		try (Stream<Path> paths = Files.list(fixturesRoot)) {
			for (Path fixture : paths.filter(Files::isDirectory).sorted().toList()) {
				String name = fixture.getFileName().toString();
				boolean hasJavaFile = hasDirectJavaFile(fixture);
				if (name.equals(SUPPORT_FIXTURE_DIR)) {
					if (!hasJavaFile) {
						failures.add(fixture + ": support fixture directory must contain Java stubs.");
					}
					continue;
				}

				if (!Files.exists(fixture.resolve(EXPECTED_RULES_FILE))) {
					failures.add(fixture + ": fixture case must contain " + EXPECTED_RULES_FILE + ".");
				}
				if (!hasJavaFile) {
					failures.add(fixture + ": fixture case must contain at least one Java source file.");
				}
			}
		}
		return failures;
	}

	private static boolean hasDirectJavaFile(Path directory) throws IOException {
		try (Stream<Path> paths = Files.list(directory)) {
			return paths.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"));
		}
	}

	private static List<String> checkFixtureCoverage(Path fixturesRoot, RuleRegistry ruleRegistry) throws IOException {
		Set<String> ruleNames = new LinkedHashSet<>(ruleRegistry.classRuleNames().values());
		Set<String> violationCovered = new HashSet<>();
		Set<String> okCovered = new HashSet<>();
		List<String> failures = new ArrayList<>();

		try (Stream<Path> paths = Files.list(fixturesRoot)) {
			for (Path fixture : paths.filter(Files::isDirectory).sorted().toList()) {
				if (fixture.getFileName().toString().equals(SUPPORT_FIXTURE_DIR)) {
					continue;
				}
				Path expectedRulesFile = fixture.resolve(EXPECTED_RULES_FILE);
				if (!Files.exists(expectedRulesFile)) {
					continue;
				}
				FixtureMetadata metadata = FixtureMetadataReader.read(expectedRulesFile);
				if (metadata.expectedRules().isEmpty() && metadata.covers().isEmpty()) {
					failures.add(expectedRulesFile + ": ok fixture must declare # covers: RuleName.");
				}
				for (String expectedRule : metadata.expectedRules()) {
					if (!ruleNames.contains(expectedRule)) {
						failures.add(expectedRulesFile + ": expected unknown rule " + expectedRule + ".");
					} else {
						violationCovered.add(expectedRule);
					}
				}
				for (String coveredRule : metadata.covers()) {
					if (!ruleNames.contains(coveredRule)) {
						failures.add(expectedRulesFile + ": covers unknown rule " + coveredRule + ".");
					} else if (!metadata.expectedRules().contains(coveredRule)) {
						okCovered.add(coveredRule);
					}
				}
			}
		}

		for (java.util.Map.Entry<String, String> rule : ruleRegistry.classRuleNames().entrySet()) {
			if (!violationCovered.contains(rule.getValue())) {
				failures.add(rule.getKey() + " has no violation fixture covering " + rule.getValue() + ".");
			}
			if (!okCovered.contains(rule.getValue())) {
				failures.add(rule.getKey() + " has no ok fixture with # covers: " + rule.getValue() + ".");
			}
		}
		return failures;
	}
}
