package checks.project.rules;

import checks.project.ProjectRule;
import checks.project.analysis.LifecycleControllerAnalysis.LifecycleTypeStatus;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.util.ArrayList;
import java.util.List;

public class LifecycleTypeResolutionFailureRule implements ProjectRule {
	public static final String NAME = "LifecycleTypeResolutionFailure";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (ClassOrInterfaceDeclaration classDeclaration : sourceFile.unit().findAll(ClassOrInterfaceDeclaration.class)) {
			if (!context.lifecycleControllerAnalysis().hasStaticSelfRegistrationShape(classDeclaration)) {
				continue;
			}
			if (context.lifecycleControllerAnalysis().lifecycleTypeStatus(classDeclaration) == LifecycleTypeStatus.UNRESOLVED) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(classDeclaration), NAME,
						"Classes with lifecycle-style self-registration must have resolvable lifecycle interface ancestry."));
			}
		}
		return violations;
	}
}
