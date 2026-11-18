package checks.project.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class SourceInheritanceIndex {
	private boolean indexed = false;
	private final Set<String> extendedClasses = new HashSet<>();

	public boolean isExtended(TypeDeclaration<?> typeDeclaration) {
		return qualifiedNames(typeDeclaration).stream().anyMatch(extendedClasses::contains);
	}

	public void index(CompilationUnit unit) {
		for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
			for (ClassOrInterfaceType extendedType : declaration.getExtendedTypes()) {
				String name = extendedType.getNameAsString();
				extendedClasses.add(name);
				extendedClasses.add(extendedType.toString());
			}
		}
	}

	public void markIndexed() {
		indexed = true;
	}

	public boolean isIndexed() {
		return indexed;
	}

	private Set<String> qualifiedNames(TypeDeclaration<?> typeDeclaration) {
		Set<String> names = new HashSet<>();
		names.add(typeDeclaration.getNameAsString());
		Optional<CompilationUnit> unit = typeDeclaration.findCompilationUnit();
		if (unit.isPresent() && unit.get().getPackageDeclaration().isPresent()) {
			names.add(unit.get().getPackageDeclaration().get().getNameAsString() + "." + typeDeclaration.getNameAsString());
		}
		return names;
	}
}
