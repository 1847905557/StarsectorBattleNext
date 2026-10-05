package data.test.fixture;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TestSourceIndex {

	private static BattleNext_TestSourceIndex instance;
	private final Map<Path, CompilationUnit> sources;

	private BattleNext_TestSourceIndex() throws IOException {

		CombinedTypeSolver solver = new CombinedTypeSolver();
		MemoryTypeSolver declarations = new MemoryTypeSolver();
		solver.add(declarations);
		solver.add(new ClassLoaderTypeSolver(getClass().getClassLoader()));
		JavaParser syntaxParser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
				.setCharacterEncoding(StandardCharsets.UTF_8));
		JavaParser runtimeParser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
				.setCharacterEncoding(StandardCharsets.UTF_8)
				.setSymbolResolver(new JavaSymbolSolver(solver)));
		Map<Path, CompilationUnit> parsed = new LinkedHashMap<>();
		for (Path root : new Path[]{Path.of("src"), Path.of("tools/checks")}) {
			try (var paths = Files.walk(root)) {
				for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
					JavaParser parser = path.startsWith(Path.of("src/data")) ? runtimeParser : syntaxParser;
					var result = parser.parse(path);
					if (!result.isSuccessful()) throw new IOException("Invalid Java source " + path + ": " + result.getProblems());
					CompilationUnit unit = result.getResult().orElseThrow();
					parsed.put(path, unit);
					if (!path.startsWith(Path.of("src/data"))) continue;
					for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
						if (type.getFullyQualifiedName().isEmpty()) continue;
						declarations.addDeclaration(type.getFullyQualifiedName().orElseThrow(),
								JavaParserFacade.get(solver).getTypeDeclaration(type));
					}
				}
			}
		}
		sources = Collections.unmodifiableMap(parsed);
	}

	public static synchronized BattleNext_TestSourceIndex getInstance() throws IOException {
		if (instance == null) {
			instance = new BattleNext_TestSourceIndex();
		}
		return instance;
	}

	public Map<Path, CompilationUnit> getSources() {
		return sources;
	}
}
