package checks.project.rules;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;

import java.util.ArrayList;
import java.util.List;

public class LifecycleConstructorRule implements ProjectRule {
	public static final String NAME = "NoPublicLifecycleControllerConstructor";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (ClassOrInterfaceDeclaration classDeclaration : sourceFile.unit().findAll(ClassOrInterfaceDeclaration.class)) {
			boolean isLifecycleController = !context.lifecycleControllerAnalysis().findLifecycleEntries(classDeclaration).isEmpty()
					|| context.lifecycleControllerAnalysis().hasLifecycleEntryAnnotation(classDeclaration);
			if (!isLifecycleController) {
				continue;
			}
			for (ConstructorDeclaration constructor : classDeclaration.getConstructors()) {
				if (constructor.isPublic()) {
					violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(constructor), NAME,
							"Lifecycle controller classes with @LifecycleEntry methods must not expose public constructors."));
				}
			}
		}
		return violations;
	}
}
