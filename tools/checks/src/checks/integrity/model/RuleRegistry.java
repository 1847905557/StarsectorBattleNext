package checks.integrity.model;

import java.util.Map;
import java.util.Set;

public record RuleRegistry(Set<String> ruleClasses, Map<String, String> classRuleNames) {
}
