package checks.project;

import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;

import java.util.List;

public interface ProjectRule {
	List<Violation> check(SourceFile sourceFile, RuleContext context);
}
