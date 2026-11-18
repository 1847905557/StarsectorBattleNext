package checks.project.fixture;

import java.nio.file.Path;
import java.util.Set;

public record ProjectRuleFixtureCase(Path path, Set<String> expectedRules) {
}
