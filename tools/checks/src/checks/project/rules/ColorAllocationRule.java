package checks.project.rules;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;

import java.util.ArrayList;
import java.util.List;

public class ColorAllocationRule implements ProjectRule {
	public static final String NAME = "NoNonStaticColorCreation";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (ObjectCreationExpr creation : sourceFile.unit().findAll(ObjectCreationExpr.class)) {
			if (!isColorType(creation.getType()) || !hasOnlyLiteralArguments(creation) || AstSupport.isInsideStaticFinalField(creation)) {
				continue;
			}
			violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(creation), NAME,
					"Do not create literal Color outside static final fields; move this Color to a static final constant."));
		}
		return violations;
	}

	private boolean isColorType(ClassOrInterfaceType type) {
		String name = type.getNameAsString();
		String fullName = type.toString();
		return "Color".equals(name) || "java.awt.Color".equals(fullName);
	}

	private boolean hasOnlyLiteralArguments(ObjectCreationExpr creation) {
		return creation.getArguments().stream().allMatch(Expression::isLiteralExpr);
	}
}
