package data.test.fixture;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import data.test.fixture.BattleNext_TestTextCatalog.TextKey;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BattleNext_TestI18nSource {

	public record Section(String category, String prefix) { }

	public record Read(TextKey key, List<Expression> arguments, Node origin) { }

	private final JavaSymbolSolver resolver;
	private final List<CompilationUnit> units;
	private final List<MethodCallExpr> calls;
	private final Set<String> sectionNames;
	private final Map<Node, Set<Object>> values = new IdentityHashMap<>();
	private final Set<Node> active = Collections.newSetFromMap(new IdentityHashMap<>());

	public BattleNext_TestI18nSource(Path root) throws IOException {

		CombinedTypeSolver solver = new CombinedTypeSolver(
				new JavaParserTypeSolver(root.getParent()),
				new ClassLoaderTypeSolver(getClass().getClassLoader()));
		resolver = new JavaSymbolSolver(solver);
		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
				.setCharacterEncoding(StandardCharsets.UTF_8)
				.setSymbolResolver(resolver));
		units = new ArrayList<>();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
				var result = parser.parse(path);
				assertTrue(result.isSuccessful(), path + ": " + result.getProblems());
				units.add(result.getResult().orElseThrow());
			}
		}
		calls = units.stream().flatMap(unit -> unit.findAll(MethodCallExpr.class).stream()).toList();
		sectionNames = new HashSet<>();
		for (CompilationUnit unit : units) {
			for (VariableDeclarator variable : unit.findAll(VariableDeclarator.class)) {
				if (variable.getType().asString().endsWith("BattleNext_I18nSection")) sectionNames.add(variable.getNameAsString());
			}
			for (Parameter parameter : unit.findAll(Parameter.class)) {
				if (parameter.getType().asString().endsWith("BattleNext_I18nSection")) sectionNames.add(parameter.getNameAsString());
			}
		}
	}

	public List<Read> reads() {

		List<Read> reads = new ArrayList<>();
		for (MethodCallExpr call : calls) {
			if (insideSection(call)) continue;
			String name = call.getNameAsString();
			if (!Set.of("get", "format", "getString").contains(name)) continue;
			if (call.getScope().isEmpty()) continue;
			if (call.getArguments().isEmpty()) continue;

			Expression scope = call.getScope().orElseThrow();
			if (name.equals("getString") && call.getArguments().size() == 2
					&& scope.calculateResolvedType().describe().equals("com.fs.starfarer.api.SettingsAPI")) {
				for (Object category : evaluate(call.getArgument(0))) {
					for (Object key : evaluate(call.getArgument(1))) {
						reads.add(new Read(new TextKey(category.toString(), key.toString()), List.of(), call));
					}
				}
				continue;
			}
			if (!sectionNames.contains(scope.isFieldAccessExpr() ? scope.asFieldAccessExpr().getNameAsString() : scope.toString()) && !(scope instanceof MethodCallExpr factory
					&& factory.getScope().map(value -> value.toString().endsWith("BattleNext_I18nSection")).orElse(false))) continue;
			if (!scope.calculateResolvedType().describe().equals(
					"data.scripts.battlesnext.util.BattleNext_I18nSection")) continue;
			List<Expression> arguments = name.equals("format")
					? List.copyOf(call.getArguments().subList(1, call.getArguments().size())) : List.of();
			for (Object value : evaluate(scope)) {
				Section section = (Section) value;
				for (Object key : evaluate(call.getArgument(0))) {
					reads.add(new Read(new TextKey(section.category(), section.prefix() + key), arguments, call));
				}
			}
		}
		collectReferences(reads);
		return reads;
	}

	public Set<Section> sections() {
		Set<Section> sections = new LinkedHashSet<>();
		for (MethodCallExpr call : calls) {
			if (!call.getNameAsString().equals("getInstance")) continue;
			if (!call.getScope().map(value -> value.toString().endsWith("BattleNext_I18nSection")).orElse(false)) continue;
			if (!call.resolve().declaringType().getQualifiedName().equals(
					"data.scripts.battlesnext.util.BattleNext_I18nSection")) continue;
			for (Object section : evaluate(call)) {
				sections.add((Section) section);
			}
		}
		return sections;
	}

	private Set<Object> evaluate(Expression expression) {
		expression.findCompilationUnit().orElseThrow().setData(Node.SYMBOL_RESOLVER_KEY, resolver);
		if (values.containsKey(expression)) return values.get(expression);
		assertTrue(active.add(expression), "Cyclic text expression: " + expression);
		Set<Object> result = compute(expression);
		active.remove(expression);
		assertFalse(result.isEmpty(), "Unresolved text expression: " + expression);
		values.put(expression, result);
		return result;
	}

	private Set<Object> compute(Expression expression) {

		if (expression.isStringLiteralExpr()) return Set.of(expression.asStringLiteralExpr().asString());
		if (expression.isEnclosedExpr()) return evaluate(expression.asEnclosedExpr().getInner());
		if (expression.isArrayAccessExpr()) return evaluate(expression.asArrayAccessExpr().getName());
		if (expression.isArrayCreationExpr()) return evaluate(expression.asArrayCreationExpr().getInitializer().orElseThrow());
		if (expression.isArrayInitializerExpr()) {
			Set<Object> result = new LinkedHashSet<>();
			for (Expression value : expression.asArrayInitializerExpr().getValues()) {
				result.addAll(evaluate(value));
			}
			return result;
		}
		if (expression.isConditionalExpr()) {
			ConditionalExpr conditional = expression.asConditionalExpr();
			Set<Object> result = new LinkedHashSet<>(evaluate(conditional.getThenExpr()));
			result.addAll(evaluate(conditional.getElseExpr()));
			return result;
		}
		if (expression.isBinaryExpr() && expression.asBinaryExpr().getOperator() == BinaryExpr.Operator.PLUS) {
			Set<Object> result = new LinkedHashSet<>();
			BinaryExpr binary = expression.asBinaryExpr();
			for (Object left : evaluate(binary.getLeft())) {
				for (Object right : evaluate(binary.getRight())) {
					result.add(left.toString() + right);
				}
			}
			return result;
		}
		if (expression.isNameExpr()) {
			var declaration = expression.asNameExpr().resolve();
			if (declaration.getType().isReferenceType()
					&& declaration.getType().asReferenceType().getTypeDeclaration().orElseThrow().isEnum()) {
				return enumValues(declaration.getType());
			}
			return declarationValues(declaration.toAst().orElseThrow(), declaration.getName());
		}
		if (expression.isFieldAccessExpr()) {
			var declaration = expression.asFieldAccessExpr().resolve();
			if (declaration.getType().isReferenceType()
					&& declaration.getType().asReferenceType().getTypeDeclaration().orElseThrow().isEnum()) {
				return enumValues(declaration.getType());
			}
			return declarationValues(declaration.toAst().orElseThrow(), declaration.getName());
		}
		if (expression.isSwitchExpr()) {
			Set<Object> result = new LinkedHashSet<>();
			for (SwitchEntry entry : expression.asSwitchExpr().getEntries()) {
				for (Statement statement : entry.getStatements()) {
					if (statement.isExpressionStmt()) result.addAll(evaluate(statement.asExpressionStmt().getExpression()));
					else if (statement.isYieldStmt()) result.addAll(evaluate(statement.asYieldStmt().getExpression()));
					else {
						for (YieldStmt yield : statement.findAll(YieldStmt.class)) {
							result.addAll(evaluate(yield.getExpression()));
						}
					}
				}
			}
			return result;
		}
		if (expression.isMethodCallExpr()) return methodValues(expression.asMethodCallExpr());
		throw new AssertionError("Unsupported text expression: " + expression + " at " + expression.getRange());
	}

	private Set<Object> declarationValues(Node node, String name) {

		node.findCompilationUnit().orElseThrow().setData(Node.SYMBOL_RESOLVER_KEY, resolver);
		if (node instanceof Parameter parameter) {
			MethodDeclaration method = parameter.findAncestor(MethodDeclaration.class).orElseThrow();
			int index = method.getParameters().indexOf(parameter);
			Set<Object> result = new LinkedHashSet<>();
			String signature = method.resolve().getQualifiedSignature();
			for (MethodCallExpr call : calls) {
				if (!call.getNameAsString().equals(method.getNameAsString())) continue;
				if (call.getArguments().size() != method.getParameters().size()) continue;
				if (!call.resolve().getQualifiedSignature().equals(signature)) continue;
				result.addAll(evaluate(call.getArgument(index)));
			}
			return result;
		}
		VariableDeclarator variable = node instanceof VariableDeclarator declarator ? declarator
				: node.findAll(VariableDeclarator.class).stream().filter(value -> value.getNameAsString().equals(name))
						.findFirst().orElseThrow();
		if (variable.getInitializer().isPresent()) return evaluate(variable.getInitializer().orElseThrow());
		EnumDeclaration owner = variable.findAncestor(EnumDeclaration.class).orElseThrow();
		for (ConstructorDeclaration constructor : owner.getConstructors()) {
			for (AssignExpr assignment : constructor.findAll(AssignExpr.class)) {
				if (!assignment.getTarget().isFieldAccessExpr()) continue;
				if (!assignment.getTarget().asFieldAccessExpr().getNameAsString().equals(name)) continue;
				String parameterName = assignment.getValue().asNameExpr().getNameAsString();
				int index = -1;
				for (int position = 0; position < constructor.getParameters().size(); position++) {
					if (constructor.getParameter(position).getNameAsString().equals(parameterName)) index = position;
				}
				assertTrue(index >= 0, name);
				Set<Object> result = new LinkedHashSet<>();
				for (EnumConstantDeclaration entry : owner.getEntries()) {
					result.addAll(evaluate(entry.getArgument(index)));
				}
				return result;
			}
		}
		throw new AssertionError("Unresolved enum text field: " + name);
	}

	private Set<Object> methodValues(MethodCallExpr call) {

		String name = call.getNameAsString();
		if (name.equals("getInstance") && call.resolve().declaringType().getQualifiedName().equals(
				"data.scripts.battlesnext.util.BattleNext_I18nSection")) {
			return Set.of(new Section(call.getArgument(0).asStringLiteralExpr().asString(),
					call.getArgument(1).asStringLiteralExpr().asString()));
		}
		if (name.equals("toLowerCase")) {
			Set<Object> result = new LinkedHashSet<>();
			for (Object value : evaluate(call.getScope().orElseThrow())) {
				result.add(value.toString().toLowerCase(Locale.ROOT));
			}
			return result;
		}
		if (name.equals("name")) return evaluate(call.getScope().orElseThrow());
		ResolvedType type = call.calculateResolvedType();
		if (type.isReferenceType() && type.asReferenceType().getTypeDeclaration().orElseThrow().isEnum()) {
			return enumValues(type);
		}
		ResolvedMethodDeclaration declaration = call.resolve();
		MethodDeclaration method = (MethodDeclaration) declaration.toAst().orElseThrow();
		Set<Object> result = new LinkedHashSet<>();
		if (method.getBody().isPresent()) {
			for (ReturnStmt statement : method.findAll(ReturnStmt.class)) {
				result.addAll(evaluate(statement.getExpression().orElseThrow()));
			}
			return result;
		}
		for (CompilationUnit unit : units) {
			for (MethodDeclaration implementation : unit.findAll(MethodDeclaration.class)) {
				if (!implementation.getNameAsString().equals(name)) continue;
				if (implementation.getBody().isEmpty()) continue;
				if (!declaration.declaringType().isAssignableBy(implementation.resolve().declaringType())) continue;
				for (ReturnStmt statement : implementation.findAll(ReturnStmt.class)) {
					result.addAll(evaluate(statement.getExpression().orElseThrow()));
				}
			}
		}
		return result;
	}

	private Set<Object> enumValues(ResolvedType type) {
		EnumDeclaration declaration = (EnumDeclaration) type.asReferenceType().getTypeDeclaration().orElseThrow()
				.toAst().orElseThrow();
		Set<Object> result = new LinkedHashSet<>();
		for (EnumConstantDeclaration entry : declaration.getEntries()) {
			result.add(entry.getNameAsString());
		}
		return result;
	}

	private void collectReferences(List<Read> reads) {

		for (CompilationUnit unit : units) {
			for (MethodReferenceExpr reference : unit.findAll(MethodReferenceExpr.class)) {
				if (!reference.getIdentifier().equals("get")) continue;
				if (!sectionNames.contains(reference.getScope().isFieldAccessExpr()
						? reference.getScope().asFieldAccessExpr().getNameAsString() : reference.getScope().toString()) && !(reference.getScope() instanceof MethodCallExpr factory
						&& factory.getScope().map(value -> value.toString().endsWith("BattleNext_I18nSection")).orElse(false))) continue;
				if (!reference.getScope().calculateResolvedType().describe().equals(
						"data.scripts.battlesnext.util.BattleNext_I18nSection")) continue;
				Section section = (Section) evaluate(reference.getScope()).iterator().next();
				AssignExpr assignment = reference.findAncestor(AssignExpr.class).orElseThrow();
				String field = assignment.getTarget().asFieldAccessExpr().getNameAsString();
				TypeDeclaration<?> owner = reference.findAncestor(TypeDeclaration.class).orElseThrow();
				for (MethodCallExpr call : owner.findAll(MethodCallExpr.class)) {
					if (!call.getNameAsString().equals("apply")) continue;
					if (!call.getScope().orElseThrow().toString().equals(field)) continue;
					List<Expression> arguments = List.of();
					Node parent = call.getParentNode().orElseThrow();
					if (parent instanceof MethodCallExpr format && format.getNameAsString().equals("format")
							&& format.getArgument(0) == call) {
						arguments = List.copyOf(format.getArguments().subList(1, format.getArguments().size()));
					}
					for (Object key : evaluate(call.getArgument(0))) {
						reads.add(new Read(new TextKey(section.category(), section.prefix() + key), arguments, call));
					}
				}
			}
		}
	}

	private boolean insideSection(Node node) {
		return node.findAncestor(ClassOrInterfaceDeclaration.class).map(type ->
				type.getNameAsString().equals("BattleNext_I18nSection")).orElse(false);
	}
}
