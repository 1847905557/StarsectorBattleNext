package checks.project.support;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.nio.charset.StandardCharsets;

public final class ProjectParser {
	private ProjectParser() {
	}

	public static void configure(CombinedTypeSolver typeSolver) {
		StaticJavaParser.setConfiguration(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
				.setCharacterEncoding(StandardCharsets.UTF_8)
				.setSymbolResolver(new JavaSymbolSolver(typeSolver)));
	}
}
