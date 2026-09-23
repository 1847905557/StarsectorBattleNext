package checks.project.rules;

import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.util.ArrayList;
import java.util.List;

public class StaticFieldImmutabilityRule implements ProjectRule {
	public static final String NAME = "StaticFieldImmutability";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (FieldDeclaration field : sourceFile.unit().findAll(FieldDeclaration.class)) {
			if (field.isStatic() && !field.isFinal()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(field), NAME,
						"Static fields must be final; mutable state must be owned by an instance or an explicit runtime store."));
			}
		}
		return violations;
	}
}
