package checks.project.rules;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import checks.project.ProjectRule;
import checks.project.analysis.LifecycleControllerAnalysis.LifecycleEntryMethod;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;

import java.util.ArrayList;
import java.util.List;

public class MissingLifecycleEntryAnnotationRule implements ProjectRule {
	public static final String NAME = "MissingLifecycleEntryAnnotation";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (ClassOrInterfaceDeclaration classDeclaration : sourceFile.unit().findAll(ClassOrInterfaceDeclaration.class)) {
			for (LifecycleEntryMethod entry : context.lifecycleControllerAnalysis().findLifecycleEntries(classDeclaration)) {
				if (!entry.annotated()) {
					violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(entry.method()), NAME,
							"Lifecycle controller static entry methods must be marked with @LifecycleEntry."));
				}
			}
		}
		return violations;
	}
}
