package data.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

class BattleNext_ImportStyleTest {

	private final JavaParser parser = new JavaParser(new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
	private final Pattern qualifiedType = Pattern.compile("[a-z_][\\w]*(?:\\.[a-z_][\\w]*)*\\.[A-Z][\\w]*(?:\\.[\\w]+)*");

	@Test
	void allRepositoryJavaTypeReferencesUseImports() throws Exception {

		List<String> failures = new ArrayList<>();
		for (Path root : List.of(Path.of("src"), Path.of("tools/checks"))) {
			try (var files = Files.walk(root)) {
				for (Path path : files.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
					var result = parser.parse(path);
					assertTrue(result.isSuccessful(), path + ": " + result.getProblems());
					for (String reference : references(result.getResult().orElseThrow())) {
						failures.add(path + ": " + reference);
					}
				}
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

	private List<String> references(CompilationUnit unit) {
		List<String> references = new ArrayList<>();
		for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
			if (qualifiedType.matcher(type.getNameWithScope()).matches()) references.add(type.getNameWithScope());
		}
		for (FieldAccessExpr expression : unit.findAll(FieldAccessExpr.class)) {
			if (qualifiedType.matcher(expression.toString()).matches()) references.add(expression.toString());
		}
		for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
			if (qualifiedType.matcher(annotation.getNameAsString()).matches()) references.add(annotation.getNameAsString());
		}
		return references;
	}
}
