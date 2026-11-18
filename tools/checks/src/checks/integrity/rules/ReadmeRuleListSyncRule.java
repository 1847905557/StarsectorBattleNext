package checks.integrity.rules;

import checks.integrity.IntegrityCheckInput;
import checks.integrity.IntegrityRule;
import checks.integrity.model.RuleRegistry;
import checks.integrity.support.ProjectRuleRegistryReader;
import checks.integrity.support.ReadmeSectionReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadmeRuleListSyncRule implements IntegrityRule {
	private static final Pattern README_CODE_SPAN = Pattern.compile("`([^`]+)`");
	private static final String PROJECT_RULES_SECTION = "### 项目源码规则";

	@Override
	public List<String> check(IntegrityCheckInput input) throws IOException {
		RuleRegistry ruleRegistry = ProjectRuleRegistryReader.read(input.projectRulesDir());
		Set<String> expectedRuleFiles = new LinkedHashSet<>();
		for (String ruleClass : ruleRegistry.ruleClasses()) {
			expectedRuleFiles.add(ruleClass + ".java");
		}

		List<String> failures = new ArrayList<>();
		String section = ReadmeSectionReader.readSection(input.readme(), PROJECT_RULES_SECTION);
		Set<String> actualRuleFiles = new LinkedHashSet<>();
		Matcher matcher = README_CODE_SPAN.matcher(section);
		while (matcher.find()) {
			String code = matcher.group(1);
			if (!code.endsWith(".java")) {
				continue;
			}
			if (code.contains("/") || code.contains("\\")) {
				failures.add("tools README checks section must list rule file names without paths: " + code + ".");
				continue;
			}
			if (!code.endsWith("Rule.java")) {
				failures.add("tools README checks section must not list non-rule Java file " + code + ".");
				continue;
			}
			actualRuleFiles.add(code);
		}

		for (String expectedRuleFile : expectedRuleFiles) {
			if (!actualRuleFiles.contains(expectedRuleFile)) {
				failures.add("tools README checks section does not list " + expectedRuleFile + ".");
			}
		}
		for (String actualRuleFile : actualRuleFiles) {
			if (!expectedRuleFiles.contains(actualRuleFile)) {
				failures.add("tools README checks section lists unknown rule file " + actualRuleFile + ".");
			}
		}
		return failures;
	}
}
