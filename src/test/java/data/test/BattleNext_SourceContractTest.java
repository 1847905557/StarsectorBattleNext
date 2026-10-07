package data.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import data.scripts.ui.editor.BattleNext_EditorView;
import data.test.fixture.BattleNext_TestI18nSource;
import data.test.fixture.BattleNext_TestSourceIndex;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestTextCatalog.TextKey;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

class BattleNext_SourceContractTest {

	private static BattleNext_TestI18nSource source;
	private static List<BattleNext_TestI18nSource.Read> reads;
	private static final Pattern FORMAT = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])");
	private final JavaParser parser = new JavaParser(new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
	private final Pattern qualifiedType = Pattern.compile("[a-z_][\\w]*(?:\\.[a-z_][\\w]*)*\\.[A-Z][\\w]*(?:\\.[\\w]+)*");

	@BeforeAll
	static void inspectRuntime() throws Exception {
		source = new BattleNext_TestI18nSource(Path.of("src/data"));
		reads = source.reads();
	}

	@Test
	void editorOwnershipAndExceptionDiagnosticsFollowTheProjectContracts() throws Exception {

		Set<String> controls = Set.of("BattleNext_TextField", "BattleNext_OptionField", "BattleNext_OptionPicker");
		Set<String> directCreation = Set.of("addCheckbox", "editorTextField", "editorButton", "fixedButton", "bindField", "bindCheck");
		for (var entry : BattleNext_TestSourceIndex.getInstance().getSources().entrySet()) {
			Path path = entry.getKey();
			if (!path.startsWith(Path.of("src/data"))) continue;
			var unit = entry.getValue();
			for (ThrowStmt statement : unit.findAll(ThrowStmt.class)) {
				assertFalse(statement.toString().matches("(?s).*(?:Strings|strings|texts|I18nSection)\\.(?:get|format).*"), () -> path + ": " + statement);
			}
			if (!path.startsWith(Path.of("src/data/scripts/ui/editor"))) continue;
			if (path.getFileName().toString().equals("BattleNext_EditorView.java")) continue;
			for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
				if (declaration.isNestedType() || declaration.isAbstract()) continue;
				Class<?> type = Class.forName(declaration.getFullyQualifiedName().orElseThrow());
				assertTrue(BattleNext_EditorView.class.isAssignableFrom(type), path.toString());
				for (String name : List.of("attach", "detach", "refreshContent", "close", "save", "discard", "hasUnsavedChanges")) {
					var method = Arrays.stream(type.getMethods()).filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
					assertEquals(BattleNext_EditorView.class, method.getDeclaringClass(), path + ": " + name);
					assertTrue(Modifier.isFinal(method.getModifiers()), path + ": " + name);
				}
			}
			for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
				assertFalse(controls.contains(creation.getType().asString()), () -> path + ": " + creation);
			}
			for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
				assertFalse(directCreation.contains(call.getNameAsString()), () -> path + ": " + call);
			}
		}
	}

	@Test
	void combatInputSynchronizationAndMissionKeyboardReadsUseTheirSharedFramework() throws Exception {

		var sources = BattleNext_TestSourceIndex.getInstance().getSources();
		for (String name : List.of("BattleNext_FreeCameraPlugin", "BattleNext_RttPlugin")) {
			var type = sources.get(Path.of("src/data/scripts/plugin", name + ".java")).getClassByName(name).orElseThrow();
			MethodDeclaration input = type.getMethodsByName("processInputPreCoreControls").get(0);
			var synchronization = input.findAll(MethodCallExpr.class).stream()
					.filter(call -> call.getNameAsString().equals("updateControlState")).findFirst().orElseThrow();
			ForEachStmt loop = input.findFirst(ForEachStmt.class).orElseThrow();
			assertTrue(synchronization.getBegin().orElseThrow().line < loop.getBegin().orElseThrow().line, name);
			for (String gate : List.of("isPlayerPlaying", "isUiCovered")) {
				assertTrue(type.findAll(MethodCallExpr.class).stream().anyMatch(call -> call.getNameAsString().equals(gate)
						&& call.getScope().map(Object::toString).orElse("").equals("BattleNext_CombatInput")), name + ": " + gate);
			}
		}
		for (var entry : sources.entrySet()) {
			if (!entry.getKey().startsWith(Path.of("src/data/missions"))) continue;
			if (!entry.getKey().getFileName().toString().equals("MissionDefinition.java")) continue;
			for (MethodCallExpr call : entry.getValue().findAll(MethodCallExpr.class)) {
				assertFalse(call.getNameAsString().equals("isKeyDown")
						&& call.getScope().map(Object::toString).orElse("").equals("Keyboard"), () -> entry.getKey() + ": " + call);
			}
		}
	}

	@Test
	void previewMetadataAndStatusRegistrationFollowTheirNativeBoundaries() throws Exception {

		var sources = BattleNext_TestSourceIndex.getInstance().getSources();
		var preview = sources.get(Path.of("src/data/scripts/ui/widget/BattleNext_VariantPreview.java"));
		for (MethodCallExpr call : preview.findAll(MethodCallExpr.class)) {
			assertFalse(Set.of("forName", "getDeclaredMethod", "findVirtual", "loadJSON", "openStream", "createFXDrone")
					.contains(call.getNameAsString()), call::toString);
		}
		assertTrue(preview.findAll(MethodCallExpr.class).stream().anyMatch(call -> call.getNameAsString().equals("read")
				&& call.getScope().map(Object::toString).orElse("").equals("BattleNext_SpriteSpec")));
		for (var entry : sources.entrySet()) {
			if (!entry.getKey().startsWith(Path.of("src/data"))) continue;
			if (entry.getKey().getFileName().toString().equals("BattleNext_AllShipsStatusPlugin.java")) continue;
			for (SimpleName name : entry.getValue().findAll(SimpleName.class)) {
				if (!name.asString().equals("BattleNext_AllShipsStatusPlugin")) continue;
				var type = assertInstanceOf(ClassOrInterfaceType.class, name.getParentNode().orElseThrow());
				var creation = assertInstanceOf(ObjectCreationExpr.class, type.getParentNode().orElseThrow());
				var registration = assertInstanceOf(MethodCallExpr.class, creation.getParentNode().orElseThrow());
				assertEquals("addPlugin", registration.getNameAsString(), entry.getKey().toString());
			}
		}
	}

	@Test
	void simulatorControlFlowUsesMultilineBlocks() throws Exception {
		for (var entry : BattleNext_TestSourceIndex.getInstance().getSources().entrySet()) {
			if (!entry.getKey().startsWith(Path.of("src/data/simulator"))) continue;
			var unit = entry.getValue();
			for (ForStmt loop : unit.findAll(ForStmt.class)) {
				assertBlock(loop.getBody());
			}
			for (ForEachStmt loop : unit.findAll(ForEachStmt.class)) {
				assertBlock(loop.getBody());
			}
			for (WhileStmt loop : unit.findAll(WhileStmt.class)) {
				assertBlock(loop.getBody());
			}
			for (DoStmt loop : unit.findAll(DoStmt.class)) {
				assertBlock(loop.getBody());
			}
			for (IfStmt branch : unit.findAll(IfStmt.class)) {
				if (branch.getThenStmt().isExpressionStmt()) {
					assertBlock(branch.getThenStmt());
				}
			}
		}
	}

	private void assertBlock(Statement body) {
		assertTrue(body.isBlockStmt(), body::toString);
		assertTrue(body.getBegin().orElseThrow().line < body.getEnd().orElseThrow().line, body::toString);
	}

	@Test
	void allRepositoryJavaTypeReferencesUseImports() throws Exception {

		List<String> failures = new ArrayList<>();
		for (var entry : BattleNext_TestSourceIndex.getInstance().getSources().entrySet()) {
			for (String reference : references(entry.getValue())) {
				failures.add(entry.getKey() + ": " + reference);
			}
		}
		assertEquals(List.of(), failures);
	}

	@ParameterizedTest
	@ValueSource(strings = {"class Example extends java.util.ArrayList<String> {}",
			"class Example { Object value = java.util.List.of(); }", "@org.junit.jupiter.api.Test class Example {}"})
	void checkerFindsQualifiedTypesInDeclarationsExpressionsAndAnnotations(String source) {
		var unit = parser.parse(source).getResult().orElseThrow();
		assertFalse(references(unit).isEmpty());
	}

	@Test
	void packageImportsStringsAndSimpleNestedTypesUseTheirOwnSyntax() {
		String source = "package example; import java.util.List; class Example { List<String> values = List.of(); "
				+ "String text = \"java.util.List\"; Thread.State state = Thread.State.NEW; }";
		assertEquals(List.of(), references(parser.parse(source).getResult().orElseThrow()));
	}

	@Test
	void fixtureSourcesKeepHelpersSeparateFromTests() throws Exception {

		Set<String> tests = Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");
		for (var entry : BattleNext_TestSourceIndex.getInstance().getSources().entrySet()) {
			Path file = entry.getKey();
			if (!file.startsWith(Path.of("src/test/java/data/test/fixture"))) continue;
			var unit = entry.getValue();
			for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
				assertFalse(tests.contains(annotation.getName().getIdentifier()), () -> file + ": " + annotation);
			}
			for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
				assertFalse(field.isPublic(), () -> file + ": " + field);
			}
		}
	}

	@Test
	void testClipboardOperationsUseOwnedClipboardInputs() throws Exception {
		for (var entry : BattleNext_TestSourceIndex.getInstance().getSources().entrySet()) {
			if (!entry.getKey().startsWith(Path.of("src/test/java"))) continue;
			for (MethodCallExpr call : entry.getValue().findAll(MethodCallExpr.class)) {
				assertFalse(Set.of("getSystemClipboard", "getSystemSelection").contains(call.getNameAsString()),
						() -> "Test clipboard operations require an owned in-memory clipboard: " + entry.getKey() + ": " + call);
			}
		}
	}

	@Test
	void testTextLookupsKeepLanguageSelectionInsideTheCatalog() throws Exception {
		List<String> failures = new ArrayList<>();
		for (var entry : BattleNext_TestSourceIndex.getInstance().getSources().entrySet()) {
			if (!entry.getKey().startsWith(Path.of("src/test/java"))) continue;
			if (entry.getKey().getFileName().toString().equals("BattleNext_TestTextCatalog.java")) continue;
			for (MethodCallExpr call : entry.getValue().findAll(MethodCallExpr.class)) {
				if (!call.getScope().map(Object::toString).orElse("").equals("BattleNext_TestTextCatalog")) continue;
				if (!call.getNameAsString().equals("text")) continue;
				if (call.getArguments().size() != 2 || !call.getArgument(0).findAll(ConditionalExpr.class).isEmpty()) {
					failures.add(entry.getKey() + ": " + call);
				}
			}
			for (ConditionalExpr conditional : entry.getValue().findAll(ConditionalExpr.class)) {
				if (!conditional.getThenExpr().isStringLiteralExpr() || !conditional.getElseExpr().isStringLiteralExpr()) continue;
				String first = conditional.getThenExpr().asStringLiteralExpr().asString();
				String second = conditional.getElseExpr().asStringLiteralExpr().asString();
				if (first.equals(second + "_CN") || second.equals(first + "_CN")) {
					failures.add(entry.getKey() + ": " + conditional);
				}
			}
		}
		assertEquals(List.of(), failures);
	}

	@Test
	void everyRuntimeReadResolvesAndEveryCatalogEntryHasARuntimeConsumer() {
		Set<TextKey> consumed = new LinkedHashSet<>();
		for (BattleNext_TestI18nSource.Read read : reads) {
			assertTrue(BattleNext_TestTextCatalog.entries().containsKey(read.key()),
					read.key() + " at " + read.origin().getRange());
			consumed.add(read.key());
			consumed.add(new TextKey(read.key().category() + "_CN", read.key().id()));
		}
		assertEquals(BattleNext_TestTextCatalog.entries().keySet(), consumed);
	}

	@Test
	void sectionsUseTheirOwnExplicitCategoryAndPrefix() {
		Set<String> categories = new TreeSet<>();
		for (BattleNext_TestI18nSource.Section section : source.sections()) {
			assertEquals("BattleNext_" + section.category().toLowerCase(Locale.ROOT) + "_", section.prefix());
			categories.add(section.category());
		}
		assertEquals(new TreeSet<>(BattleNext_TestTextCatalog.entries().keySet().stream()
				.map(TextKey::category).filter(category -> !category.endsWith("_CN")).toList()), categories);
	}

	@Test
	void everyFormattedReadMatchesArgumentCountAndTypes() {

		for (BattleNext_TestI18nSource.Read read : reads) {
			if (read.arguments().isEmpty()) continue;
			Object[] values = new Object[read.arguments().size()];
			for (int index = 0; index < values.length; index++) {
				String type = read.arguments().get(index).calculateResolvedType().describe();
				values[index] = switch (type) {
					case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short",
							"java.lang.Integer", "java.lang.Long" -> 1;
					case "float", "double", "java.lang.Float", "java.lang.Double" -> 1.25f;
					case "boolean", "java.lang.Boolean" -> true;
					case "char", "java.lang.Character" -> 'A';
					case "java.lang.String" -> "value";
					default -> throw new AssertionError("Unsupported format argument type: " + type);
				};
			}
			for (String category : List.of(read.key().category(), read.key().category() + "_CN")) {
				String pattern = BattleNext_TestTextCatalog.entries().get(new TextKey(category, read.key().id()));
				List<String> conversions = FORMAT
						.matcher(pattern).results().map(match -> match.group(1))
						.filter(conversion -> !conversion.equals("%") && !conversion.equals("n")).toList();
				assertEquals(conversions.size(), read.arguments().size(), read.key().toString());
				assertDoesNotThrow(() -> String.format(Locale.ROOT, pattern, values), read.key().toString());
			}
		}
	}

	private List<String> references(CompilationUnit unit) {
		List<String> references = new ArrayList<>();
		for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
			if (qualifiedType.matcher(type.getNameWithScope()).matches()) references.add(type.getNameWithScope());
		}
		for (FieldAccessExpr expression : unit.findAll(FieldAccessExpr.class)) {
			Deque<String> names = new ArrayDeque<>();
			Expression scope = expression;
			while (scope instanceof FieldAccessExpr access) {
				names.addFirst(access.getNameAsString());
				scope = access.getScope();
			}
			if (!scope.isNameExpr()) continue;
			names.addFirst(scope.asNameExpr().getNameAsString());
			String name = String.join(".", names);
			if (qualifiedType.matcher(name).matches()) references.add(name);
		}
		for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
			if (qualifiedType.matcher(annotation.getNameAsString()).matches()) references.add(annotation.getNameAsString());
		}
		return references;
	}
}
