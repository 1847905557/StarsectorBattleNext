package checks.project.rules;

import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.List;

public class PublicDataShapeRule implements ProjectRule {
	public static final String NAME = "PublicDataShape";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (ClassOrInterfaceDeclaration type : sourceFile.unit().findAll(ClassOrInterfaceDeclaration.class)) {
			if (type.getAnnotationByName("PublicData").isEmpty()) continue;

			if (!type.getImplementedTypes().isEmpty()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(type), NAME,
						"@PublicData types must not implement behavior interfaces."));
			}
			if (!type.getExtendedTypes().isEmpty()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(type), NAME,
						"@PublicData types must not extend behavior or base classes."));
			}

			for (MethodDeclaration method : type.getMethods()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(method), NAME,
						"@PublicData types must not declare methods; use constructors and fields only."));
			}
		}
		return violations;
	}
}
