package checks.project.analysis;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class LifecycleControllerAnalysis {
	private static final List<String> LIFECYCLE_INTERFACES = List.of(
			"com.fs.starfarer.api.EveryFrameScript",
			"com.fs.starfarer.api.combat.EveryFrameCombatPlugin",
			"com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin",
			"EveryFrameScript",
			"EveryFrameCombatPlugin",
			"CombatLayeredRenderingPlugin"
	);

	private final Map<ClassOrInterfaceDeclaration, LifecycleAnalysis> cache = new WeakHashMap<>();

	public List<LifecycleEntryMethod> findLifecycleEntries(ClassOrInterfaceDeclaration classDeclaration) {
		return analyze(classDeclaration).entries();
	}

	public LifecycleTypeStatus lifecycleTypeStatus(ClassOrInterfaceDeclaration classDeclaration) {
		return analyze(classDeclaration).typeStatus();
	}

	public boolean hasStaticSelfRegistrationShape(ClassOrInterfaceDeclaration classDeclaration) {
		return !analyze(classDeclaration).candidateMethods().isEmpty();
	}

	public boolean hasLifecycleEntryAnnotation(ClassOrInterfaceDeclaration classDeclaration) {
		return classDeclaration.getMethods().stream().anyMatch(LifecycleControllerAnalysis::isAnnotatedLifecycleEntry);
	}

	public static boolean isAnnotatedLifecycleEntry(MethodDeclaration method) {
		return method.isStatic() && method.getAnnotations().stream()
				.map(annotation -> annotation.getName())
				.map(name -> name.getIdentifier())
				.anyMatch("LifecycleEntry"::equals);
	}

	private LifecycleAnalysis analyze(ClassOrInterfaceDeclaration classDeclaration) {
		return cache.computeIfAbsent(classDeclaration, this::analyzeUncached);
	}

	private LifecycleAnalysis analyzeUncached(ClassOrInterfaceDeclaration classDeclaration) {
		List<MethodDeclaration> candidateMethods = findCandidateMethods(classDeclaration);
		LifecycleTypeStatus typeStatus = lifecycleTypeStatusUncached(classDeclaration);
		if (typeStatus != LifecycleTypeStatus.MATCHED_LIFECYCLE_INTERFACE) {
			return new LifecycleAnalysis(typeStatus, candidateMethods, List.of());
		}

		List<LifecycleEntryMethod> entries = new ArrayList<>();
		for (MethodDeclaration method : candidateMethods) {
			entries.add(new LifecycleEntryMethod(method, isAnnotatedLifecycleEntry(method)));
		}
		return new LifecycleAnalysis(typeStatus, candidateMethods, entries);
	}

	private LifecycleTypeStatus lifecycleTypeStatusUncached(ClassOrInterfaceDeclaration classDeclaration) {
		try {
			ResolvedReferenceTypeDeclaration resolved = classDeclaration.resolve();
			if (!resolved.isClass()) {
				return LifecycleTypeStatus.NOT_LIFECYCLE_INTERFACE;
			}
			boolean matched = resolved.asClass().getAllInterfaces().stream()
					.map(ResolvedReferenceType::getQualifiedName)
					.anyMatch(LIFECYCLE_INTERFACES::contains);
			return matched ? LifecycleTypeStatus.MATCHED_LIFECYCLE_INTERFACE : LifecycleTypeStatus.NOT_LIFECYCLE_INTERFACE;
		} catch (RuntimeException ex) {
			return LifecycleTypeStatus.UNRESOLVED;
		}
	}

	private List<MethodDeclaration> findCandidateMethods(ClassOrInterfaceDeclaration classDeclaration) {
		List<MethodDeclaration> methods = new ArrayList<>();
		for (MethodDeclaration method : classDeclaration.getMethods()) {
			if (method.isStatic() && createsSelf(method, classDeclaration) && hasOwnershipAction(method)) {
				methods.add(method);
			}
		}
		return methods;
	}

	private static boolean createsSelf(MethodDeclaration method, ClassOrInterfaceDeclaration classDeclaration) {
		String className = classDeclaration.getNameAsString();
		return method.findAll(ObjectCreationExpr.class).stream()
				.anyMatch(creation -> className.equals(creation.getType().getNameAsString()));
	}

	private static boolean hasOwnershipAction(MethodDeclaration method) {
		return method.findAll(MethodCallExpr.class).stream()
				.map(MethodCallExpr::getNameAsString)
				.anyMatch(name -> name.equals("addPlugin")
						|| name.equals("addLayeredRenderingPlugin")
						|| name.equals("setCustomData")
						|| name.equals("getCustomData"));
	}

	public enum LifecycleTypeStatus {
		MATCHED_LIFECYCLE_INTERFACE,
		NOT_LIFECYCLE_INTERFACE,
		UNRESOLVED
	}

	public record LifecycleEntryMethod(MethodDeclaration method, boolean annotated) {
	}

	private record LifecycleAnalysis(
			LifecycleTypeStatus typeStatus,
			List<MethodDeclaration> candidateMethods,
			List<LifecycleEntryMethod> entries
	) {
	}
}
