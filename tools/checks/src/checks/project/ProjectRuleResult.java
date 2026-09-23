package checks.project;

import checks.project.model.Violation;

import java.util.List;

public record ProjectRuleResult(List<Violation> violations) {
}
