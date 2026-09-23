package checks.integrity.rules;

import checks.integrity.IntegrityRule;

import java.util.List;

public final class IntegrityRules {
	private IntegrityRules() {
	}

	public static List<IntegrityRule> all() {
		return List.of(
				new RuleRegistrySyncRule(),
				new FixtureIntegrityRule(),
				new ReadmeRuleListSyncRule()
		);
	}

	public static List<IntegrityRule> registry() {
		return List.of(new RuleRegistrySyncRule());
	}

	public static List<IntegrityRule> fixtures() {
		return List.of(new FixtureIntegrityRule());
	}

	public static List<IntegrityRule> readme() {
		return List.of(new ReadmeRuleListSyncRule());
	}
}
