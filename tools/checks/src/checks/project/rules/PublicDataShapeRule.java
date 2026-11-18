package checks.project.rules;

import checks.project.ProjectRule;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.AstSupport;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithExtends;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;

import java.util.ArrayList;
import java.util.List;

public class PublicDataShapeRule implements ProjectRule {
	public static final String NAME = "PublicDataShape";

	@Override
	public List<Violation> check(SourceFile sourceFile, RuleContext context) {
		List<Violation> violations = new ArrayList<>();
		for (TypeDeclaration<?> type : sourceFile.unit().findAll(TypeDeclaration.class)) {
			if (type.getAnnotationByName("PublicData").isEmpty()) continue;

			if (!type.isPublic()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(type.getName()), NAME,
						"@PublicData types must be public."));
			}
			if (type instanceof NodeWithImplements<?> implementing && !implementing.getImplementedTypes().isEmpty()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(type), NAME,
						"@PublicData types must not implement behavior interfaces."));
			}
			if (type instanceof NodeWithExtends<?> extending && !extending.getExtendedTypes().isEmpty()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(type), NAME,
						"@PublicData types must not extend behavior or base classes."));
			}

			for (FieldDeclaration field : type.getFields()) {
				if (field.isPublic()) continue;
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(field), NAME,
						"@PublicData fields must be public."));
			}
			if (type instanceof RecordDeclaration record) {
				for (Parameter component : record.getParameters()) {
					violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(component), NAME,
							"Record components declare private fields; use the record without @PublicData."));
				}
			}

			for (MethodDeclaration method : type.getMethods()) {
				violations.add(new Violation(sourceFile.path(), AstSupport.lineOf(method), NAME,
						"@PublicData types must not declare methods; use constructors and fields only."));
			}
		}
		return violations;
	}
}
