package checks.project.support;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.Type;

import java.util.Optional;

public final class AstSupport {
	private AstSupport() {
	}

	public static int lineOf(Node node) {
		return node.getBegin()
				.map(position -> position.line)
				.orElse(1);
	}

	public static boolean isInsideStaticFinalField(Node node) {
		@SuppressWarnings("unchecked")
		Optional<FieldDeclaration> field = node.findAncestor(FieldDeclaration.class);
		return field.isPresent() && field.get().isStatic() && field.get().isFinal();
	}

	public static boolean isTypeNamed(Type type, String simpleName) {
		if (!type.isClassOrInterfaceType()) {
			return false;
		}
		return simpleName.equals(type.asClassOrInterfaceType().getNameAsString());
	}
}
