package checks.project.support;

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ProjectTypeSolver {
	private ProjectTypeSolver() {
	}

	public static CombinedTypeSolver build(List<Path> sourceRoots, List<Path> classpath) throws IOException {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
		for (Path sourcePath : sourceRoots) {
			typeSolver.add(new JavaParserTypeSolver(sourcePath));
		}
		for (Path classpathEntry : classpath) {
			if (Files.isRegularFile(classpathEntry) && classpathEntry.toString().endsWith(".jar")) {
				typeSolver.add(new JarTypeSolver(classpathEntry));
			}
		}
		return typeSolver;
	}
}
