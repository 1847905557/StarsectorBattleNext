package checks.project.rules;

import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;

public class FieldAccessBoundaryRule implements ProjectRule {
	public static final String NAME = "FieldAccessBoundary";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (FieldDeclaration field : sourceFile.unit().findAll(FieldDeclaration.class)) {
			if (field.isStatic()) {
				continue;
			}

			if (!field.isPublic() && !field.isPrivate() && !field.isProtected()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(field), NAME,
						"Fields must declare an explicit access modifier; use private unless the state has a formal exposure boundary."));
				continue;
			}

			if (field.isPublic()) {
				if (!isInsidePublicDataType(field)) {
					violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(field), NAME,
							"Public instance fields are only allowed inside @PublicData types."));
				}
				continue;
			}

			if (field.isProtected() && !isInsideExtendedType(field, context)) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(field), NAME,
						"Protected fields are only allowed when the declaring class is extended inside project sources."));
			}
		}
		return violations;
	}

	private boolean isInsidePublicDataType(FieldDeclaration field) {
		return field.findAncestor(TypeDeclaration.class)
				.map(type -> type.getAnnotationByName("PublicData").isPresent())
				.orElse(false);
	}

	private boolean isInsideExtendedType(FieldDeclaration field, RuleContext context) {
		return field.findAncestor(TypeDeclaration.class)
				.map(context.sourceInheritanceIndex()::isExtended)
				.orElse(false);
	}
}
