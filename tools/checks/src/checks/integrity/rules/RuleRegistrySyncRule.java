package checks.integrity.rules;

import checks.integrity.IntegrityCheckInput;
import checks.integrity.IntegrityRule;
import checks.integrity.support.ProjectRuleRegistryReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RuleRegistrySyncRule implements IntegrityRule {
	@Override
	public List<String> check(IntegrityCheckInput input) throws IOException {
		Path rulesDir = input.projectRulesDir();
		Set<String> ruleClasses = ProjectRuleRegistryReader.listRuleClasses(rulesDir);
		Set<String> registeredClasses = ProjectRuleRegistryReader.readRegisteredRuleClasses(rulesDir.resolve("ProjectRules.java"));
		return checkRuleRegistration(ruleClasses, registeredClasses);
	}

	private static List<String> checkRuleRegistration(Set<String> ruleClasses, Set<String> registeredClasses) {
		List<String> failures = new ArrayList<>();
		for (String ruleClass : ruleClasses) {
			if (!registeredClasses.contains(ruleClass)) {
				failures.add("ProjectRules.all() does not register " + ruleClass + ".");
			}
		}
		for (String registeredClass : registeredClasses) {
			if (!registeredClass.endsWith("Rule")) {
				failures.add("ProjectRules.all() registers non-rule class " + registeredClass + ".");
				continue;
			}
			if (!ruleClasses.contains(registeredClass)) {
				failures.add("ProjectRules.all() registers missing rule class " + registeredClass + ".");
			}
		}
		return failures;
	}
}
