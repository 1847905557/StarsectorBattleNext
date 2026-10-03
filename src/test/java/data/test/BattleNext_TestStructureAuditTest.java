package data.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

class BattleNext_TestStructureAuditTest {

	@Test
	void fixtureSourcesKeepHelpersSeparateFromTests() throws Exception {

		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
		Set<String> tests = Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");
		try (Stream<Path> paths = Files.walk(Path.of("src/test/java/data/test/fixture"))) {
			for (Path file : paths.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
				var parsed = parser.parse(file);
				assertTrue(parsed.isSuccessful(), file + ": " + parsed.getProblems());
				var unit = parsed.getResult().orElseThrow();
				for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
					assertFalse(tests.contains(annotation.getName().getIdentifier()), file + ": " + annotation);
				}
				for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
					assertFalse(field.isPublic(), file + ": " + field);
				}
			}
		}
	}
}
