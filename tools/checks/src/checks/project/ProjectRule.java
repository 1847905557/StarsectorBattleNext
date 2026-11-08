package checks.project;

import checks.project.ProjectRuleRunner.RuleContext;
import checks.project.ProjectRuleRunner.SourceFile;
import checks.project.model.Violation;

import java.util.List;

public interface ProjectRule {
	List<Violation> check(SourceFile sourceFile, RuleContext context);
}
