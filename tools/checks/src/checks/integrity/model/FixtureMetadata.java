package checks.integrity.model;

import java.util.Set;

public record FixtureMetadata(Set<String> expectedRules, Set<String> covers) {
}
