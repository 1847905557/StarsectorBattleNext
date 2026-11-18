package checks.project.rules;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.Type;
import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;

import java.util.ArrayList;
import java.util.List;

public class ProjectileSourceFieldCacheRule implements ProjectRule {
	public static final String NAME = "NoProjectileSourceFieldCache";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (ClassOrInterfaceDeclaration classDeclaration : sourceFile.unit().findAll(ClassOrInterfaceDeclaration.class)) {
			if (!hasProjectileOrMissileField(classDeclaration)) {
				continue;
			}
			for (FieldDeclaration field : classDeclaration.getFields()) {
				if (!AstSupport.isTypeNamed(field.getElementType(), "ShipAPI")) {
					continue;
				}
				boolean hasSourceVariable = field.getVariables().stream()
						.map(variable -> variable.getName())
						.map(SimpleName::asString)
						.anyMatch("source"::equals);
				if (hasSourceVariable) {
					violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(field), NAME,
							"Do not cache projectile or missile source in a ShipAPI field; use method-local source variables only."));
				}
			}
		}
		return violations;
	}

	private boolean hasProjectileOrMissileField(ClassOrInterfaceDeclaration classDeclaration) {
		return classDeclaration.getFields().stream()
				.map(FieldDeclaration::getElementType)
				.anyMatch(this::isProjectileOrMissileType);
	}

	private boolean isProjectileOrMissileType(Type type) {
		return AstSupport.isTypeNamed(type, "DamagingProjectileAPI") || AstSupport.isTypeNamed(type, "MissileAPI");
	}
}
