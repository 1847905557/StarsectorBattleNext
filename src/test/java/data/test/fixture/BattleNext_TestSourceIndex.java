package data.test.fixture;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.validator.postprocessors.Java17PostProcessor;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;
import com.github.javaparser.utils.LineSeparator;

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
		JavaSymbolSolver resolver = new JavaSymbolSolver(solver);
		ParserConfiguration compiledConfiguration = new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
				.setAttributeComments(false)
				.setDetectOriginalLineSeparator(false)
				.setCharacterEncoding(StandardCharsets.UTF_8);
		compiledConfiguration.getProcessors().clear();
		Java17PostProcessor compiledPostProcessor = new Java17PostProcessor();
		JavaParser compiledParser = new JavaParser(compiledConfiguration);
		JavaParser rulesParser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
				.setAttributeComments(false)
				.setDetectOriginalLineSeparator(false)
				.setStoreTokens(false)
				.setCharacterEncoding(StandardCharsets.UTF_8));
		Map<Path, CompilationUnit> parsed = new LinkedHashMap<>();
		for (Path root : new Path[]{Path.of("src"), Path.of("tools/checks")}) {
			try (var paths = Files.walk(root)) {
				for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
					boolean runtime = path.startsWith(Path.of("src/data"));
					boolean compiled = path.startsWith(Path.of("src"))
							&& (!path.startsWith(Path.of("src/test")) || path.startsWith(Path.of("src/test/java")));
					compiledConfiguration.setStoreTokens(runtime);
					JavaParser parser = compiled ? compiledParser : rulesParser;
					String sourceText = Files.readString(path, StandardCharsets.UTF_8);
					var result = parser.parse(sourceText);
					if (compiled) {
						compiledPostProcessor.postProcess(result, compiledConfiguration);
					}
					if (!result.isSuccessful()) throw new IOException("Invalid Java source " + path + ": " + result.getProblems());
					CompilationUnit unit = result.getResult().orElseThrow();
					unit.setStorage(path, StandardCharsets.UTF_8);
					unit.setData(Node.LINE_SEPARATOR_KEY, LineSeparator.detect(sourceText));
					parsed.put(path, unit);
					if (!runtime) continue;
					unit.setData(Node.SYMBOL_RESOLVER_KEY, resolver);
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
