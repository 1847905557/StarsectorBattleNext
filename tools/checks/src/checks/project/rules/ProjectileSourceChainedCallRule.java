package checks.project.rules;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectileSourceChainedCallRule implements ProjectRule {
	public static final String NAME = "NoProjectileSourceChainedCall";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (MethodCallExpr call : sourceFile.unit().findAll(MethodCallExpr.class)) {
			if ("getSource".equals(call.getNameAsString())) {
				continue;
			}
			Optional<Expression> scope = call.getScope();
			if (scope.isEmpty() || !scope.get().isMethodCallExpr()) {
				continue;
			}

			MethodCallExpr scopedCall = scope.get().asMethodCallExpr();
			if (!"getSource".equals(scopedCall.getNameAsString()) || !isProjectileVariable(scopedCall.getScope())) {
				continue;
			}
			violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(call), NAME,
					"Do not call methods directly on projectile or missile getSource(); guard source before direct source method calls."));
		}
		return violations;
	}

	private boolean isProjectileVariable(Optional<Expression> scope) {
		if (scope.isEmpty() || !scope.get().isNameExpr()) {
			return false;
		}
		String name = scope.get().asNameExpr().getNameAsString();
		return "projectile".equals(name) || "missile".equals(name) || "proj".equals(name);
	}
}
